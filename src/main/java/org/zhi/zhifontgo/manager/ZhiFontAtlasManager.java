package org.zhi.zhifontgo.manager;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontAtlasManager implements AutoCloseable {
    public enum PipelineMode {
        SDF,
        IRIS_COMPAT
    }

    private final TextureManager textureManager;
    private final ResourceLocation atlasBaseId;
    private final ZhiFontGlyphProviderManager glyphProvider;
    private final PipelineMode pipelineMode;
    private final Int2ObjectMap<BakedGlyph> bakedGlyphs = new Int2ObjectOpenHashMap<>();
    private final List<ZhiFontTextureManager> textures = new ArrayList<>();

    public ZhiFontAtlasManager(
        TextureManager textureManager,
        ResourceLocation atlasBaseId,
        ZhiFontGlyphProviderManager glyphProvider,
        PipelineMode pipelineMode
    ) {
        this.textureManager = textureManager;
        this.atlasBaseId = atlasBaseId;
        this.glyphProvider = glyphProvider;
        this.pipelineMode = pipelineMode;
    }

    @Nullable
    public GlyphInfo findGlyphInfo(int codepoint) {
        return this.glyphProvider.getGlyph(codepoint);
    }

    @Nullable
    public BakedGlyph findGlyph(int codepoint) {
        if (this.bakedGlyphs.containsKey(codepoint)) {
            return this.bakedGlyphs.get(codepoint);
        }

        GlyphInfo glyphInfo = this.findGlyphInfo(codepoint);
        if (glyphInfo == null) {
            return null;
        }

        BakedGlyph bakedGlyph = glyphInfo.bake(this::stitch);
        this.bakedGlyphs.put(codepoint, bakedGlyph);
        return bakedGlyph;
    }

    public void warmUpGlyphs(IntSet codepoints) {
        for (Integer codepoint : codepoints) {
            this.findGlyph(codepoint);
        }
    }

    @Override
    public void close() {
        for (ZhiFontTextureManager texture : this.textures) {
            texture.close();
        }
        this.textures.clear();
        this.bakedGlyphs.clear();
    }

    private BakedGlyph stitch(SheetGlyphInfo glyphInfo) {
        for (ZhiFontTextureManager texture : this.textures) {
            BakedGlyph bakedGlyph = texture.add(glyphInfo);
            if (bakedGlyph != null) {
                return bakedGlyph;
            }
        }

        ResourceLocation textureId = this.atlasBaseId.withSuffix("/" + this.textures.size());
        GlyphRenderTypes renderTypes = this.pipelineMode == PipelineMode.IRIS_COMPAT
            ? ZhiFontRenderTypeManager.createForIrisCompatibleTexture(textureId)
            : ZhiFontRenderTypeManager.createForSdfTexture(textureId);
        ZhiFontTextureManager nextTexture = new ZhiFontTextureManager(
            renderTypes,
            glyphInfo.isColored(),
            this.pipelineMode == PipelineMode.IRIS_COMPAT
        );
        this.textures.add(nextTexture);
        this.textureManager.register(textureId, nextTexture);
        return nextTexture.add(glyphInfo);
    }
}
