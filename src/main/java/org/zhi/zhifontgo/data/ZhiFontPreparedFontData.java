package org.zhi.zhifontgo.data;

import it.unimi.dsi.fastutil.ints.IntList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.manager.ZhiFontAtlasManager;
import org.zhi.zhifontgo.manager.ZhiFontGlyphProviderManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontPreparedFontData implements AutoCloseable {
    private final String cacheKey;
    private final String cacheFileName;
    private final ZhiFontGlyphProviderManager glyphProvider;
    private final ZhiFontAtlasManager atlasManager;
    private final ZhiFontAtlasManager worldAtlasManager;
    private final ZhiFontRenderer runtimeFont;
    private final ZhiFontRenderer runtimeFontFilterFishy;
    private final ZhiFontRenderer worldRuntimeFont;
    private final IntList preloadCodepoints;

    public ZhiFontPreparedFontData(
        String cacheKey,
        String cacheFileName,
        ZhiFontGlyphProviderManager glyphProvider,
        ZhiFontAtlasManager atlasManager,
        ZhiFontAtlasManager worldAtlasManager,
        ZhiFontRenderer runtimeFont,
        ZhiFontRenderer runtimeFontFilterFishy,
        ZhiFontRenderer worldRuntimeFont,
        IntList preloadCodepoints
    ) {
        this.cacheKey = cacheKey;
        this.cacheFileName = cacheFileName;
        this.glyphProvider = glyphProvider;
        this.atlasManager = atlasManager;
        this.worldAtlasManager = worldAtlasManager;
        this.runtimeFont = runtimeFont;
        this.runtimeFontFilterFishy = runtimeFontFilterFishy;
        this.worldRuntimeFont = worldRuntimeFont;
        this.preloadCodepoints = preloadCodepoints;
    }

    public String cacheKey() {
        return this.cacheKey;
    }

    public String cacheFileName() {
        return this.cacheFileName;
    }

    public ZhiFontGlyphProviderManager glyphProvider() {
        return this.glyphProvider;
    }

    public ZhiFontAtlasManager atlasManager() {
        return this.atlasManager;
    }

    public ZhiFontAtlasManager worldAtlasManager() {
        return this.worldAtlasManager;
    }

    public ZhiFontRenderer runtimeFont() {
        return this.runtimeFont;
    }

    public ZhiFontRenderer runtimeFontFilterFishy() {
        return this.runtimeFontFilterFishy;
    }

    public ZhiFontRenderer worldRuntimeFont() {
        return this.worldRuntimeFont;
    }

    public IntList preloadCodepoints() {
        return this.preloadCodepoints;
    }

    @Override
    public void close() {
        if (this.worldAtlasManager != this.atlasManager) {
            this.worldAtlasManager.close();
        }
        this.atlasManager.close();
        this.glyphProvider.close();
    }
}
