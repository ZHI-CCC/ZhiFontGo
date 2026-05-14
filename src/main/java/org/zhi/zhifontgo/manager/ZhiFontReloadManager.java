package org.zhi.zhifontgo.manager;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.data.ZhiFontData;
import org.zhi.zhifontgo.data.ZhiFontLoadRequestData;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontReloadManager extends SimplePreparableReloadListener<ZhiFontReloadManager.ZhiFontReloadState> {
    @Override
    protected ZhiFontReloadState prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Path selectedFontPath = ZhiFontManager.selectedFontPath();
        if (selectedFontPath == null) {
            return ZhiFontReloadState.restoreVanilla();
        }

        if (!java.nio.file.Files.isRegularFile(selectedFontPath)) {
            Main.LOGGER.warn("ZhiFontGo selected font no longer exists, restoring vanilla font: {}", selectedFontPath);
            return ZhiFontReloadState.withClearedSelection();
        }

        try {
            ZhiFontLoadRequestData request = ZhiFontManager.createSelectedFontLoadRequest(ZhiFontLoadRequestData.Mode.STARTUP);
            ZhiFontData runtimeFontData = ZhiFontManager.createRuntimeFontData(
                request.fontLabel(),
                request.size(),
                request.oversample(),
                request.shiftX(),
                request.shiftY()
            );
            ZhiFontManager.StartupCacheDescriptor cacheDescriptor = ZhiFontManager.describeFontCache(
                request.fontLabel(),
                request.fontBytes(),
                request.size(),
                request.oversample(),
                request.shiftX(),
                request.shiftY()
            );
            List<ZhiFontGlyphProviderManager.CachedGlyphData> preparedGlyphs = prepareStartupGlyphs(request, runtimeFontData, cacheDescriptor);
            return ZhiFontReloadState.withPreparedFont(request, preparedGlyphs);
        } catch (RuntimeException exception) {
            Main.LOGGER.warn("ZhiFontGo failed to prepare startup font cache for '{}'. Falling back to vanilla font.", selectedFontPath, exception);
            return ZhiFontReloadState.restoreVanilla();
        }
    }

    @Override
    protected void apply(ZhiFontReloadState reloadData, ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            if (reloadData.shouldClearSelection()) {
                ZhiFontManager.clearSelectionAndRestoreVanilla();
                return;
            }

            if (reloadData.preparedRequest() != null) {
                ZhiFontManager.installCachedFont(reloadData.preparedRequest(), reloadData.preparedGlyphs());
                return;
            }

            ZhiFontManager.restoreVanilla();
        } catch (RuntimeException exception) {
            ZhiFontManager.clearSelectionAndRestoreVanilla();
            Main.LOGGER.error("ZhiFontGo failed during font reload. Restored vanilla font.", exception);
        }
    }

    private static List<ZhiFontGlyphProviderManager.CachedGlyphData> prepareStartupGlyphs(
        ZhiFontLoadRequestData request,
        ZhiFontData runtimeFontData,
        ZhiFontManager.StartupCacheDescriptor cacheDescriptor
    ) {
        List<Integer> preloadCodepoints = ZhiFontLoadingManager.buildStartupPreloadCodepoints(runtimeFontData.seedCharacters());
        ZhiFontGlyphProviderManager glyphProvider = ZhiFontGlyphProviderManager.create(runtimeFontData, request.fontBytes());
        boolean cacheDirty = false;
        try {
            if (ZhiFontCacheManager.hasCacheFile(cacheDescriptor.cacheKey(), cacheDescriptor.cacheFileName())) {
                glyphProvider.loadCachedGlyphs(
                    ZhiFontCacheManager.loadGlyphs(cacheDescriptor.cacheKey(), cacheDescriptor.cacheFileName(), preloadCodepoints)
                );
            }

            for (Integer codepoint : preloadCodepoints) {
                if (codepoint != null && glyphProvider.warmUpGlyph(codepoint)) {
                    cacheDirty = true;
                }
            }

            List<ZhiFontGlyphProviderManager.CachedGlyphData> preparedGlyphs = List.copyOf(glyphProvider.exportCachedGlyphs());
            if (cacheDirty || !ZhiFontCacheManager.hasCacheFile(cacheDescriptor.cacheKey(), cacheDescriptor.cacheFileName())) {
                ZhiFontCacheManager.saveGlyphs(cacheDescriptor.cacheKey(), cacheDescriptor.cacheFileName(), preparedGlyphs);
            }
            return preparedGlyphs;
        } finally {
            glyphProvider.close();
        }
    }

    static record ZhiFontReloadState(
        @Nullable ZhiFontLoadRequestData preparedRequest,
        List<ZhiFontGlyphProviderManager.CachedGlyphData> preparedGlyphs,
        boolean shouldClearSelection
    ) {
        static ZhiFontReloadState restoreVanilla() {
            return new ZhiFontReloadState(null, List.of(), false);
        }

        static ZhiFontReloadState withClearedSelection() {
            return new ZhiFontReloadState(null, List.of(), true);
        }

        static ZhiFontReloadState withPreparedFont(
            ZhiFontLoadRequestData request,
            List<ZhiFontGlyphProviderManager.CachedGlyphData> preparedGlyphs
        ) {
            return new ZhiFontReloadState(request, preparedGlyphs, false);
        }
    }
}
