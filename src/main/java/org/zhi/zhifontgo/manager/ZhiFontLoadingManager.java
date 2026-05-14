package org.zhi.zhifontgo.manager;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.lang.reflect.Field;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.data.ZhiFontLoadRequestData;
import org.zhi.zhifontgo.data.ZhiFontLoadResultData;
import org.zhi.zhifontgo.data.ZhiFontPreparedFontData;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontLoadingManager {
    private static final int MAX_GLYPHS_PER_TICK = 96;
    private static final long WORK_BUDGET_NANOS = 20_000_000L;
    @Nullable
    private static LoadSession activeSession;

    private ZhiFontLoadingManager() {
    }

    public static void start(ZhiFontLoadRequestData request) {
        clearSession();
        try {
            activeSession = new LoadSession(request);
        } catch (RuntimeException exception) {
            Main.LOGGER.error("ZhiFontGo failed to prepare font '{}' for loading.", request.fontLabel(), exception);
            activeSession = LoadSession.failed(request, exception);
        }
    }

    public static void tick() {
        if (activeSession == null || activeSession.isFinished()) {
            return;
        }
        activeSession.tick();
    }

    public static boolean isLoading() {
        return activeSession != null && !activeSession.isFinished();
    }

    public static boolean isFinished() {
        return activeSession != null && activeSession.isFinished();
    }

    @Nullable
    public static ZhiFontLoadRequestData activeRequest() {
        return activeSession != null ? activeSession.request() : null;
    }

    public static int processedGlyphs() {
        return activeSession != null ? activeSession.processedGlyphs() : 0;
    }

    public static int totalGlyphs() {
        return activeSession != null ? activeSession.totalGlyphs() : 0;
    }

    public static IntList buildStartupPreloadCodepoints(String seedCharacters) {
        return LoadSession.buildPreloadCodepoints(ZhiFontGlyphProviderManager.createBasePreloadCodepoints(seedCharacters));
    }

    public static float progress() {
        if (activeSession == null || activeSession.totalGlyphs() <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float)activeSession.processedGlyphs() / (float)activeSession.totalGlyphs());
    }

    @Nullable
    public static ZhiFontLoadResultData consumeResult() {
        if (activeSession == null || !activeSession.isFinished()) {
            return null;
        }

        ZhiFontLoadResultData result = activeSession.result();
        activeSession.close();
        activeSession = null;
        return result;
    }

    private static void clearSession() {
        if (activeSession != null) {
            activeSession.close();
            activeSession = null;
        }
    }

    private static final class LoadSession implements AutoCloseable {
        private final ZhiFontLoadRequestData request;
        @Nullable
        private final ZhiFontPreparedFontData preparedFont;
        @Nullable
        private final IntList preloadCodepoints;
        private final int initialCachedGlyphCount;
        private final long startedAtNanos = System.nanoTime();
        private int nextIndex;
        private boolean adopted;
        private boolean cacheDirty;
        @Nullable
        private ZhiFontLoadResultData result;

        private LoadSession(ZhiFontLoadRequestData request) {
            this.request = request;
            this.preparedFont = ZhiFontManager.prepareFont(
                request.fontLabel(),
                request.fontBytes(),
                request.size(),
                request.oversample(),
                request.shiftX(),
                request.shiftY(),
                request.shadowEnabled()
            );
            this.preloadCodepoints = buildPreloadCodepoints(this.preparedFont.preloadCodepoints());
            this.preparedFont.glyphProvider().loadCachedGlyphs(
                ZhiFontCacheManager.loadGlyphs(this.preparedFont.cacheKey(), this.preparedFont.cacheFileName(), this.preloadCodepoints)
            );
            this.initialCachedGlyphCount = this.preparedFont.glyphProvider().cachedGlyphCount();
        }

        private LoadSession(ZhiFontLoadRequestData request, ZhiFontLoadResultData result) {
            this.request = request;
            this.preparedFont = null;
            this.preloadCodepoints = null;
            this.initialCachedGlyphCount = 0;
            this.result = result;
        }

        private static LoadSession failed(ZhiFontLoadRequestData request, RuntimeException exception) {
            String errorMessage = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            return new LoadSession(request, ZhiFontLoadResultData.failure(errorMessage));
        }

        private static IntList buildPreloadCodepoints(IntList baseCodepoints) {
            IntArrayList merged = new IntArrayList(baseCodepoints);
            IntSet seen = new IntOpenHashSet(baseCodepoints);
            collectCurrentLanguageCharacters().codePoints().forEach(codepoint -> {
                if (seen.add(codepoint)) {
                    merged.add(codepoint);
                }
            });
            return merged;
        }

        private static String collectCurrentLanguageCharacters() {
            Language language = Language.getInstance();
            StringBuilder builder = new StringBuilder(8192);
            for (Field field : language.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(language);
                    if (!(value instanceof Map<?, ?> map)) {
                        continue;
                    }
                    for (Object translation : map.values()) {
                        if (translation instanceof String text) {
                            builder.append(text).append('\n');
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    Main.LOGGER.debug("ZhiFontGo failed to inspect language field '{}' for preload text.", field.getName(), exception);
                }
            }
            return builder.toString();
        }

        private void tick() {
            if (this.preparedFont == null || this.preloadCodepoints == null) {
                return;
            }
            try {
                long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
                int processedThisTick = 0;
                while (this.nextIndex < this.preloadCodepoints.size() && processedThisTick < MAX_GLYPHS_PER_TICK && System.nanoTime() < deadline) {
                    int codepoint = this.preloadCodepoints.getInt(this.nextIndex++);
                    if (this.preparedFont.glyphProvider().warmUpGlyph(codepoint)) {
                        this.cacheDirty = true;
                    }
                    processedThisTick++;
                }

                if (this.nextIndex >= this.preloadCodepoints.size()) {
                    if (this.cacheDirty) {
                        try {
                            ZhiFontCacheManager.saveGlyphs(
                                this.preparedFont.cacheKey(),
                                this.preparedFont.cacheFileName(),
                                this.preparedFont.glyphProvider().exportCachedGlyphs()
                            );
                        } catch (RuntimeException exception) {
                            Main.LOGGER.warn("ZhiFontGo failed to persist glyph cache for '{}'. The font will still be applied.", this.request.fontLabel(), exception);
                        }
                    }
                    ZhiFontManager.installPreparedFont(
                        this.preparedFont,
                        this.request.fontPath(),
                        this.request.fontLabel(),
                        this.request.size(),
                        this.request.oversample(),
                        this.request.shiftX(),
                        this.request.shiftY(),
                        this.request.shadowEnabled()
                    );
                    this.adopted = true;
                    long elapsedMillis = (System.nanoTime() - this.startedAtNanos) / 1_000_000L;
                    Main.LOGGER.info(
                        "ZhiFontGo preloaded {} glyphs for '{}' in {} ms. Cache primed with {} glyphs, cache updated={}.",
                        this.preloadCodepoints.size(),
                        this.request.fontLabel(),
                        elapsedMillis,
                        this.initialCachedGlyphCount,
                        this.cacheDirty
                    );
                    this.result = ZhiFontLoadResultData.success();
                }
            } catch (RuntimeException exception) {
                Main.LOGGER.error("ZhiFontGo failed while preloading glyphs for '{}'.", this.request.fontLabel(), exception);
                this.result = ZhiFontLoadResultData.failure(exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
            }
        }

        private ZhiFontLoadRequestData request() {
            return this.request;
        }

        private int processedGlyphs() {
            return this.preloadCodepoints == null ? 0 : Math.min(this.nextIndex, this.preloadCodepoints.size());
        }

        private int totalGlyphs() {
            return this.preloadCodepoints == null ? 0 : this.preloadCodepoints.size();
        }

        private boolean isFinished() {
            return this.result != null;
        }

        private ZhiFontLoadResultData result() {
            return this.result != null ? this.result : ZhiFontLoadResultData.failure("Loading did not finish.");
        }

        @Override
        public void close() {
            if (!this.adopted && this.preparedFont != null) {
                this.preparedFont.close();
            }
        }
    }
}
