package org.zhi.zhifontgo.manager;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
@OnlyIn(Dist.CLIENT)
public final class ZhiFontRenderTypeManager {
    private static final RenderStateShard.ShaderStateShard SDF_SHADER = new RenderStateShard.ShaderStateShard(ZhiFontShaderManager::getSdfShader);
    private static final Function<ResourceLocation, RenderType> TEXT_SDF = Util.memoize(ZhiFontRenderTypeManager::createTextSdf);
    private static final Function<ResourceLocation, RenderType> TEXT_SDF_SEE_THROUGH = Util.memoize(ZhiFontRenderTypeManager::createTextSdfSeeThrough);
    private static final Function<ResourceLocation, RenderType> TEXT_SDF_POLYGON_OFFSET = Util.memoize(ZhiFontRenderTypeManager::createTextSdfPolygonOffset);

    private ZhiFontRenderTypeManager() {
    }

    public static GlyphRenderTypes createForSdfTexture(ResourceLocation textureLocation) {
        return new GlyphRenderTypes(
            TEXT_SDF.apply(textureLocation),
            TEXT_SDF_SEE_THROUGH.apply(textureLocation),
            TEXT_SDF_POLYGON_OFFSET.apply(textureLocation)
        );
    }

    public static GlyphRenderTypes createForIrisCompatibleTexture(ResourceLocation textureLocation) {
        return GlyphRenderTypes.createForIntensityTexture(textureLocation);
    }

    private static RenderType createTextSdf(ResourceLocation textureLocation) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(SDF_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(textureLocation, true, false))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderType.LIGHTMAP)
            .createCompositeState(false);
        return RenderType.create("zhifont_sdf_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    private static RenderType createTextSdfSeeThrough(ResourceLocation textureLocation) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(SDF_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(textureLocation, true, false))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderType.LIGHTMAP)
            .setDepthTestState(RenderType.NO_DEPTH_TEST)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .createCompositeState(false);
        return RenderType.create("zhifont_sdf_text_see_through", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    private static RenderType createTextSdfPolygonOffset(ResourceLocation textureLocation) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(SDF_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(textureLocation, true, false))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderType.LIGHTMAP)
            .setLayeringState(RenderType.POLYGON_OFFSET_LAYERING)
            .createCompositeState(false);
        return RenderType.create("zhifont_sdf_text_polygon_offset", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

}
