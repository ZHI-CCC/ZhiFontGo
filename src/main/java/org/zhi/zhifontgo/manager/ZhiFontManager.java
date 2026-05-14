package org.zhi.zhifontgo.manager;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.data.ZhiFontData;
import org.zhi.zhifontgo.data.ZhiFontEntryData;
import org.zhi.zhifontgo.data.ZhiFontLoadRequestData;
import org.zhi.zhifontgo.data.ZhiFontPreparedFontData;
import org.zhi.zhifontgo.data.ZhiFontSettingsData;
import org.zhi.zhifontgo.mixin.ZhiMinecraftAccessor;
import org.zhi.zhifontgo.mixin.ZhiVanillaFontManagerAccessor;
import org.zhi.zhifontgo.util.ZhiFontUtil;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontManager {
    private static final ResourceLocation RUNTIME_DEFAULT_TEXTURE = ZhiFontUtil.mod("runtime/default");
    private static final ResourceLocation PREVIEW_DEFAULT_TEXTURE = ZhiFontUtil.mod("preview/default");
    private static final float DEFAULT_SIZE = 8.0F;
    private static final float DEFAULT_OVERSAMPLE = 8.0F;
    private static final float DEFAULT_SHIFT_X = 0.0F;
    private static final float DEFAULT_SHIFT_Y = 0.0F;
    private static final boolean DEFAULT_SHADOW_ENABLED = true;
    private static final float MIN_SIZE = 6.0F;
    private static final float MAX_SIZE = 48.0F;
    private static final float MIN_OVERSAMPLE = 1.0F;
    private static final float MAX_OVERSAMPLE = 8.0F;
    private static final float MIN_SHIFT = -8.0F;
    private static final float MAX_SHIFT = 8.0F;
    private static final String DEFAULT_SKIP = "";
    private static final String DEFAULT_SEED_CHARACTERS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,!?-+*/:_()[]{}<>|#@%&=\\\"'`~";
    private static final PreparableReloadListener RELOAD_MANAGER = new ZhiFontReloadManager();
    private static float currentSize = DEFAULT_SIZE;
    private static float currentOversample = DEFAULT_OVERSAMPLE;
    private static float currentShiftX = DEFAULT_SHIFT_X;
    private static float currentShiftY = DEFAULT_SHIFT_Y;
    private static boolean currentShadowEnabled = DEFAULT_SHADOW_ENABLED;
    @Nullable
    private static Path selectedFontPath;
    @Nullable
    private static String selectedFontLabel;
    @Nullable
    private static ZhiFontGlyphProviderManager defaultProvider;
    @Nullable
    private static ZhiFontAtlasManager customAtlas;
    @Nullable
    private static ZhiFontAtlasManager worldCompatibleAtlas;
    @Nullable
    private static ZhiFontRenderer runtimeFont;
    @Nullable
    private static ZhiFontRenderer runtimeFontFilterFishy;
    @Nullable
    private static ZhiFontRenderer worldRuntimeFont;
    private static boolean settingsLoaded;
    private static boolean autoApplyPending;

    private ZhiFontManager() {
    }

    public static void primeClientBootstrap() {
        ensureSettingsLoaded();
    }

    public static List<ZhiFontEntryData> discoverFonts() {
        ensureSettingsLoaded();
        return ZhiFontDiscoveryManager.discoverFonts();
    }

    public static Path fontDirectory() {
        return ZhiFontDiscoveryManager.getFontDirectory();
    }

    public static PreparableReloadListener reloadManager() {
        return RELOAD_MANAGER;
    }

    public static boolean hasSelectedFont() {
        ensureSettingsLoaded();
        return selectedFontPath != null && selectedFontLabel != null;
    }

    public static boolean isCustomFontActive() {
        ensureSettingsLoaded();
        return runtimeFont != null && runtimeFontFilterFishy != null;
    }

    @Nullable
    public static ZhiFontRenderer activeRuntimeFont() {
        ensureSettingsLoaded();
        return runtimeFont;
    }

    @Nullable
    public static ZhiFontRenderer activeRuntimeFontFilterFishy() {
        ensureSettingsLoaded();
        return runtimeFontFilterFishy;
    }

    @Nullable
    public static ZhiFontRenderer activeWorldRuntimeFont() {
        ensureSettingsLoaded();
        if (ZhiFontIrisCompatManager.shouldUseCompatibleWorldText()) {
            return worldRuntimeFont != null ? worldRuntimeFont : runtimeFont;
        }
        return runtimeFont;
    }

    public static boolean shouldAutoApplySelectedFont() {
        ensureSettingsLoaded();
        return autoApplyPending && hasSelectedFont();
    }

    public static void beginAutoApplyAttempt() {
        ensureSettingsLoaded();
        autoApplyPending = false;
    }

    public static void scheduleSelectedFontAutoApply() {
        ensureSettingsLoaded();
        if (hasSelectedFont()) {
            autoApplyPending = true;
        }
    }

    public static float currentSize() {
        ensureSettingsLoaded();
        return currentSize;
    }

    public static float currentOversample() {
        ensureSettingsLoaded();
        return currentOversample;
    }

    public static float currentShiftX() {
        ensureSettingsLoaded();
        return currentShiftX;
    }

    public static float currentShiftY() {
        ensureSettingsLoaded();
        return currentShiftY;
    }

    public static boolean currentShadowEnabled() {
        ensureSettingsLoaded();
        return currentShadowEnabled;
    }

    @Nullable
    public static Path selectedFontPath() {
        ensureSettingsLoaded();
        return selectedFontPath;
    }

    @Nullable
    public static String selectedFontLabel() {
        ensureSettingsLoaded();
        return selectedFontLabel;
    }

    public static ZhiFontLoadRequestData createLoadRequest(ZhiFontEntryData fontEntry, ZhiFontLoadRequestData.Mode mode) {
        return createLoadRequest(fontEntry, mode, currentSize, currentOversample, currentShiftX, currentShiftY);
    }

    public static ZhiFontLoadRequestData createLoadRequest(
        ZhiFontEntryData fontEntry,
        ZhiFontLoadRequestData.Mode mode,
        float size,
        float oversample,
        float shiftX,
        float shiftY
    ) {
        return createLoadRequest(fontEntry, mode, size, oversample, shiftX, shiftY, currentShadowEnabled);
    }

    public static ZhiFontLoadRequestData createLoadRequest(
        ZhiFontEntryData fontEntry,
        ZhiFontLoadRequestData.Mode mode,
        float size,
        float oversample,
        float shiftX,
        float shiftY,
        boolean shadowEnabled
    ) {
        ensureSettingsLoaded();
        return new ZhiFontLoadRequestData(
            mode,
            fontEntry.displayName(),
            fontEntry.filePath().toAbsolutePath().normalize(),
            readFontBytes(fontEntry.filePath()),
            clampAndRound(size, MIN_SIZE, MAX_SIZE),
            clampAndRound(oversample, MIN_OVERSAMPLE, MAX_OVERSAMPLE),
            clampAndRound(shiftX, MIN_SHIFT, MAX_SHIFT),
            clampAndRound(shiftY, MIN_SHIFT, MAX_SHIFT),
            shadowEnabled
        );
    }

    public static ZhiFontLoadRequestData createSelectedFontLoadRequest(ZhiFontLoadRequestData.Mode mode) {
        return createSelectedFontLoadRequest(mode, currentSize, currentOversample, currentShiftX, currentShiftY);
    }

    public static ZhiFontLoadRequestData createSelectedFontLoadRequest(
        ZhiFontLoadRequestData.Mode mode,
        float size,
        float oversample,
        float shiftX,
        float shiftY
    ) {
        return createSelectedFontLoadRequest(mode, size, oversample, shiftX, shiftY, currentShadowEnabled);
    }

    public static ZhiFontLoadRequestData createSelectedFontLoadRequest(
        ZhiFontLoadRequestData.Mode mode,
        float size,
        float oversample,
        float shiftX,
        float shiftY,
        boolean shadowEnabled
    ) {
        ensureSettingsLoaded();
        if (selectedFontPath == null || selectedFontLabel == null) {
            throw new IllegalStateException("No selected font is available for loading.");
        }
        return new ZhiFontLoadRequestData(
            mode,
            selectedFontLabel,
            selectedFontPath,
            readFontBytes(selectedFontPath),
            clampAndRound(size, MIN_SIZE, MAX_SIZE),
            clampAndRound(oversample, MIN_OVERSAMPLE, MAX_OVERSAMPLE),
            clampAndRound(shiftX, MIN_SHIFT, MAX_SHIFT),
            clampAndRound(shiftY, MIN_SHIFT, MAX_SHIFT),
            shadowEnabled
        );
    }

    public static StartupCacheDescriptor describeFontCache(
        String familyName,
        byte[] fontBytes,
        float size,
        float oversample,
        float shiftX,
        float shiftY
    ) {
        ZhiFontData runtimeFontData = createRuntimeFontData(familyName, size, oversample, shiftX, shiftY);
        String cacheKey = ZhiFontCacheManager.buildCacheKey(fontBytes, runtimeFontData);
        String cacheFileName = ZhiFontCacheManager.buildCacheFileName(runtimeFontData, cacheKey);
        return new StartupCacheDescriptor(cacheKey, cacheFileName);
    }

    public static ZhiFontPreparedFontData prepareFont(String familyName, byte[] fontBytes) {
        return prepareFont(familyName, fontBytes, currentSize, currentOversample, currentShiftX, currentShiftY, currentShadowEnabled, RUNTIME_DEFAULT_TEXTURE);
    }

    public static ZhiFontPreparedFontData preparePreviewFont(
        String familyName,
        byte[] fontBytes,
        float size,
        float oversample,
        float shiftX,
        float shiftY
    ) {
        return preparePreviewFont(familyName, fontBytes, size, oversample, shiftX, shiftY, currentShadowEnabled);
    }

    public static ZhiFontPreparedFontData preparePreviewFont(
        String familyName,
        byte[] fontBytes,
        float size,
        float oversample,
        float shiftX,
        float shiftY,
        boolean shadowEnabled
    ) {
        return prepareFont(familyName, fontBytes, size, oversample, shiftX, shiftY, shadowEnabled, PREVIEW_DEFAULT_TEXTURE);
    }

    public static ZhiFontPreparedFontData prepareFont(
        String familyName,
        byte[] fontBytes,
        float size,
        float oversample,
        float shiftX,
        float shiftY
    ) {
        return prepareFont(familyName, fontBytes, size, oversample, shiftX, shiftY, currentShadowEnabled, RUNTIME_DEFAULT_TEXTURE);
    }

    public static ZhiFontPreparedFontData prepareFont(
        String familyName,
        byte[] fontBytes,
        float size,
        float oversample,
        float shiftX,
        float shiftY,
        boolean shadowEnabled
    ) {
        return prepareFont(familyName, fontBytes, size, oversample, shiftX, shiftY, shadowEnabled, RUNTIME_DEFAULT_TEXTURE);
    }

    private static ZhiFontPreparedFontData prepareFont(
        String familyName,
        byte[] fontBytes,
        float size,
        float oversample,
        float shiftX,
        float shiftY,
        boolean shadowEnabled,
        ResourceLocation atlasBaseTexture
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        FontManager vanillaFontManager = ((ZhiMinecraftAccessor)(Object)minecraft).zhifontgo$getFontManager();
        ZhiVanillaFontManagerAccessor vanillaFontManagerAccessor = (ZhiVanillaFontManagerAccessor)(Object)vanillaFontManager;
        ZhiFontData runtimeFontData = createRuntimeFontData(familyName, size, oversample, shiftX, shiftY);
        String cacheKey = ZhiFontCacheManager.buildCacheKey(fontBytes, runtimeFontData);
        String cacheFileName = ZhiFontCacheManager.buildCacheFileName(runtimeFontData, cacheKey);
        ZhiFontGlyphProviderManager nextProvider = ZhiFontGlyphProviderManager.create(runtimeFontData, fontBytes);
        ZhiFontAtlasManager nextAtlas = new ZhiFontAtlasManager(
            minecraft.getTextureManager(),
            atlasBaseTexture,
            nextProvider,
            ZhiFontAtlasManager.PipelineMode.SDF
        );
        ZhiFontRenderer nextRuntimeFont = new ZhiFontRenderer(vanillaFontManagerAccessor, false, shadowEnabled, nextAtlas, false);
        ZhiFontRenderer nextRuntimeFontFilterFishy = new ZhiFontRenderer(vanillaFontManagerAccessor, true, shadowEnabled, nextAtlas, false);
        ZhiFontAtlasManager nextWorldAtlas = shouldBuildWorldCompatibleFont()
            ? new ZhiFontAtlasManager(
                minecraft.getTextureManager(),
                atlasBaseTexture.withSuffix("_world"),
                nextProvider,
                ZhiFontAtlasManager.PipelineMode.IRIS_COMPAT
            )
            : nextAtlas;
        ZhiFontRenderer nextWorldRuntimeFont = shouldBuildWorldCompatibleFont()
            ? new ZhiFontRenderer(vanillaFontManagerAccessor, false, shadowEnabled, nextWorldAtlas, true)
            : nextRuntimeFont;
        return new ZhiFontPreparedFontData(
            cacheKey,
            cacheFileName,
            nextProvider,
            nextAtlas,
            nextWorldAtlas,
            nextRuntimeFont,
            nextRuntimeFontFilterFishy,
            nextWorldRuntimeFont,
            nextProvider.preloadCodepoints()
        );
    }

    public static void installPreparedFont(
        ZhiFontPreparedFontData preparedFont,
        @Nullable Path fontPath,
        @Nullable String fontLabel,
        float size,
        float oversample,
        float shiftX,
        float shiftY,
        boolean shadowEnabled
    ) {
        ensureSettingsLoaded();
        Minecraft minecraft = Minecraft.getInstance();
        ZhiMinecraftAccessor minecraftAccessor = (ZhiMinecraftAccessor)(Object)minecraft;

        release();

        defaultProvider = preparedFont.glyphProvider();
        defaultProvider.enableRuntimeCachePersistence(preparedFont.cacheKey(), preparedFont.cacheFileName());
        customAtlas = preparedFont.atlasManager();
        worldCompatibleAtlas = preparedFont.worldAtlasManager() == customAtlas ? customAtlas : preparedFont.worldAtlasManager();
        runtimeFont = preparedFont.runtimeFont();
        runtimeFontFilterFishy = preparedFont.runtimeFontFilterFishy();
        worldRuntimeFont = preparedFont.worldRuntimeFont();
        currentSize = clampAndRound(size, MIN_SIZE, MAX_SIZE);
        currentOversample = clampAndRound(oversample, MIN_OVERSAMPLE, MAX_OVERSAMPLE);
        currentShiftX = clampAndRound(shiftX, MIN_SHIFT, MAX_SHIFT);
        currentShiftY = clampAndRound(shiftY, MIN_SHIFT, MAX_SHIFT);
        currentShadowEnabled = shadowEnabled;
        selectedFontPath = fontPath != null ? fontPath.toAbsolutePath().normalize() : null;
        selectedFontLabel = fontLabel;
        autoApplyPending = false;

        minecraftAccessor.zhifontgo$setFont(runtimeFont);
        minecraftAccessor.zhifontgo$setFontFilterFishy(runtimeFontFilterFishy);
        saveSettings();
    }

    public static void installCachedFont(
        ZhiFontLoadRequestData request,
        Iterable<ZhiFontGlyphProviderManager.CachedGlyphData> cachedGlyphs
    ) {
        ensureSettingsLoaded();
        long startedAtNanos = System.nanoTime();
        ZhiFontPreparedFontData preparedFont = prepareFont(
            request.fontLabel(),
            request.fontBytes(),
            request.size(),
            request.oversample(),
            request.shiftX(),
            request.shiftY(),
            request.shadowEnabled()
        );
        boolean installed = false;
        try {
            preparedFont.glyphProvider().loadCachedGlyphs(cachedGlyphs);
            int cachedGlyphCount = preparedFont.glyphProvider().cachedGlyphCount();
            installPreparedFont(
                preparedFont,
                request.fontPath(),
                request.fontLabel(),
                request.size(),
                request.oversample(),
                request.shiftX(),
                request.shiftY(),
                request.shadowEnabled()
            );
            installed = true;
            long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
            Main.LOGGER.info(
                "ZhiFontGo restored '{}' from startup cache with {} glyphs in {} ms.",
                request.fontLabel(),
                cachedGlyphCount,
                elapsedMillis
            );
        } finally {
            if (!installed) {
                preparedFont.close();
            }
        }
    }

    public static boolean adjustSize(float delta) {
        ensureSettingsLoaded();
        float nextSize = clampAndRound(currentSize + delta, MIN_SIZE, MAX_SIZE);
        if (Float.compare(nextSize, currentSize) == 0) {
            return hasSelectedFont();
        }
        currentSize = nextSize;
        saveSettings();
        return hasSelectedFont();
    }

    public static boolean adjustOversample(float delta) {
        ensureSettingsLoaded();
        float nextOversample = clampAndRound(currentOversample + delta, MIN_OVERSAMPLE, MAX_OVERSAMPLE);
        if (Float.compare(nextOversample, currentOversample) == 0) {
            return hasSelectedFont();
        }
        currentOversample = nextOversample;
        saveSettings();
        return hasSelectedFont();
    }

    public static boolean adjustShiftX(float delta) {
        ensureSettingsLoaded();
        float nextShiftX = clampAndRound(currentShiftX + delta, MIN_SHIFT, MAX_SHIFT);
        if (Float.compare(nextShiftX, currentShiftX) == 0) {
            return hasSelectedFont();
        }
        currentShiftX = nextShiftX;
        saveSettings();
        return hasSelectedFont();
    }

    public static boolean adjustShiftY(float delta) {
        ensureSettingsLoaded();
        float nextShiftY = clampAndRound(currentShiftY + delta, MIN_SHIFT, MAX_SHIFT);
        if (Float.compare(nextShiftY, currentShiftY) == 0) {
            return hasSelectedFont();
        }
        currentShiftY = nextShiftY;
        saveSettings();
        return hasSelectedFont();
    }

    public static boolean resetTuning() {
        ensureSettingsLoaded();
        boolean changed = Float.compare(currentSize, DEFAULT_SIZE) != 0
            || Float.compare(currentOversample, DEFAULT_OVERSAMPLE) != 0
            || Float.compare(currentShiftX, DEFAULT_SHIFT_X) != 0
            || Float.compare(currentShiftY, DEFAULT_SHIFT_Y) != 0
            || currentShadowEnabled != DEFAULT_SHADOW_ENABLED;
        currentSize = DEFAULT_SIZE;
        currentOversample = DEFAULT_OVERSAMPLE;
        currentShiftX = DEFAULT_SHIFT_X;
        currentShiftY = DEFAULT_SHIFT_Y;
        currentShadowEnabled = DEFAULT_SHADOW_ENABLED;
        if (changed) {
            saveSettings();
        }
        return hasSelectedFont();
    }

    public static void setStoredTuning(float size, float oversample, float shiftX, float shiftY, boolean shadowEnabled) {
        ensureSettingsLoaded();
        currentSize = clampAndRound(size, MIN_SIZE, MAX_SIZE);
        currentOversample = clampAndRound(oversample, MIN_OVERSAMPLE, MAX_OVERSAMPLE);
        currentShiftX = clampAndRound(shiftX, MIN_SHIFT, MAX_SHIFT);
        currentShiftY = clampAndRound(shiftY, MIN_SHIFT, MAX_SHIFT);
        currentShadowEnabled = shadowEnabled;
        saveSettings();
    }

    public static void clearSelectionAndRestoreVanilla() {
        ensureSettingsLoaded();
        selectedFontPath = null;
        selectedFontLabel = null;
        autoApplyPending = false;
        restoreVanilla();
        saveSettings();
    }

    public static void restoreVanilla() {
        Minecraft minecraft = Minecraft.getInstance();
        ZhiMinecraftAccessor minecraftAccessor = (ZhiMinecraftAccessor)(Object)minecraft;
        FontManager vanillaFontManager = minecraftAccessor.zhifontgo$getFontManager();

        release();
        minecraftAccessor.zhifontgo$setFont(vanillaFontManager.createFont());
        minecraftAccessor.zhifontgo$setFontFilterFishy(vanillaFontManager.createFontFilterFishy());
    }

    public static void logBootstrap(Logger logger) {
        ensureSettingsLoaded();
        logger.info(
            "ZhiFontGo runtime engine ready. Default font stays vanilla until a font is selected. External font directory: {}. Settings file: {}. Cache directory: {}. Default size={}, oversample={}, shiftX={}, shiftY={}, shadowEnabled={}",
            fontDirectory(),
            ZhiFontSettingsManager.settingsFilePath(),
            ZhiFontCacheManager.cacheDirectory(),
            DEFAULT_SIZE,
            DEFAULT_OVERSAMPLE,
            DEFAULT_SHIFT_X,
            DEFAULT_SHIFT_Y,
            DEFAULT_SHADOW_ENABLED
        );
    }

    public static void initializeClientState() {
        ensureSettingsLoaded();
        if (selectedFontPath != null && selectedFontLabel != null) {
            if (!Files.isRegularFile(selectedFontPath)) {
                Main.LOGGER.warn("ZhiFontGo saved font no longer exists, clearing selection: {}", selectedFontPath);
                clearSelectionAndRestoreVanilla();
                return;
            }
            autoApplyPending = true;
            Main.LOGGER.info("ZhiFontGo prepared startup font restore for '{}'.", selectedFontLabel);
        }
    }

    private static ZhiFontData createRuntimeFontData(String familyName) {
        return createRuntimeFontData(familyName, currentSize, currentOversample, currentShiftX, currentShiftY);
    }

    public static ZhiFontData createRuntimeFontData(String familyName, float size, float oversample, float shiftX, float shiftY) {
        return new ZhiFontData(
            familyName,
            clampAndRound(size, MIN_SIZE, MAX_SIZE),
            clampAndRound(oversample, MIN_OVERSAMPLE, MAX_OVERSAMPLE),
            clampAndRound(shiftX, MIN_SHIFT, MAX_SHIFT),
            clampAndRound(shiftY, MIN_SHIFT, MAX_SHIFT),
            DEFAULT_SKIP,
            DEFAULT_SEED_CHARACTERS
        );
    }

    private static byte[] readFontBytes(Path fontPath) {
        try {
            return Files.readAllBytes(fontPath);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read font file " + fontPath, exception);
        }
    }

    private static void release() {
        runtimeFont = null;
        runtimeFontFilterFishy = null;
        worldRuntimeFont = null;
        if (worldCompatibleAtlas != null && worldCompatibleAtlas != customAtlas) {
            worldCompatibleAtlas.close();
            worldCompatibleAtlas = null;
        }
        if (customAtlas != null) {
            customAtlas.close();
            customAtlas = null;
        }
        worldCompatibleAtlas = null;
        if (defaultProvider != null) {
            defaultProvider.close();
            defaultProvider = null;
        }
    }

    private static boolean shouldBuildWorldCompatibleFont() {
        return ModList.get().isLoaded("iris");
    }

    private static float clampAndRound(float value, float min, float max) {
        float clamped = Math.max(min, Math.min(max, value));
        return Math.round(clamped * 100.0F) / 100.0F;
    }

    private static void ensureSettingsLoaded() {
        if (settingsLoaded) {
            return;
        }

        settingsLoaded = true;
        Path settingsFile = ZhiFontSettingsManager.settingsFilePath();
        boolean shouldRewriteSettings = !Files.isRegularFile(settingsFile);
        ZhiFontSettingsData settingsData;
        try {
            settingsData = ZhiFontSettingsManager.load(
                DEFAULT_SIZE,
                DEFAULT_OVERSAMPLE,
                DEFAULT_SHIFT_X,
                DEFAULT_SHIFT_Y,
                DEFAULT_SHADOW_ENABLED
            );
        } catch (RuntimeException exception) {
            Main.LOGGER.warn("ZhiFontGo failed to load settings file, falling back to defaults: {}", settingsFile, exception);
            settingsData = new ZhiFontSettingsData(null, DEFAULT_SIZE, DEFAULT_OVERSAMPLE, DEFAULT_SHIFT_X, DEFAULT_SHIFT_Y, DEFAULT_SHADOW_ENABLED);
            shouldRewriteSettings = true;
        }

        currentSize = clampAndRound(settingsData.size(), MIN_SIZE, MAX_SIZE);
        currentOversample = clampAndRound(settingsData.oversample(), MIN_OVERSAMPLE, MAX_OVERSAMPLE);
        currentShiftX = clampAndRound(settingsData.shiftX(), MIN_SHIFT, MAX_SHIFT);
        currentShiftY = clampAndRound(settingsData.shiftY(), MIN_SHIFT, MAX_SHIFT);
        currentShadowEnabled = settingsData.shadowEnabled();

        String selectedRelativePath = settingsData.selectedFontRelativePath();
        if (selectedRelativePath == null || selectedRelativePath.isBlank()) {
            autoApplyPending = false;
            if (shouldRewriteSettings) {
                saveSettings();
            }
            return;
        }

        Path resolvedPath = fontDirectory().resolve(selectedRelativePath.replace('/', File.separatorChar)).toAbsolutePath().normalize();
        if (Files.isRegularFile(resolvedPath)) {
            selectedFontPath = resolvedPath;
            selectedFontLabel = displayLabelFromRelativePath(selectedRelativePath);
            autoApplyPending = true;
            if (shouldRewriteSettings) {
                saveSettings();
            }
            return;
        }

        selectedFontPath = null;
        selectedFontLabel = null;
        autoApplyPending = false;
        saveSettings();
    }

    private static void saveSettings() {
        ZhiFontSettingsManager.save(
            new ZhiFontSettingsData(
                selectedFontRelativePath(),
                currentSize,
                currentOversample,
                currentShiftX,
                currentShiftY,
                currentShadowEnabled
            )
        );
    }

    @Nullable
    private static String selectedFontRelativePath() {
        if (selectedFontPath == null) {
            return null;
        }

        Path fontDirectory = fontDirectory();
        if (!selectedFontPath.startsWith(fontDirectory)) {
            return selectedFontPath.getFileName().toString();
        }

        return fontDirectory.relativize(selectedFontPath).toString().replace('\\', '/');
    }

    private static String displayLabelFromRelativePath(String relativePath) {
        int extensionIndex = relativePath.lastIndexOf('.');
        return extensionIndex >= 0 ? relativePath.substring(0, extensionIndex) : relativePath;
    }

    public record StartupCacheDescriptor(String cacheKey, String cacheFileName) {
    }
}
