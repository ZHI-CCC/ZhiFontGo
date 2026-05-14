package org.zhi.zhifontgo.manager;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontShaderManager {
    @Nullable
    private static ShaderInstance sdfShader;

    private ZhiFontShaderManager() {
    }

    public static void setSdfShader(ShaderInstance shaderInstance) {
        sdfShader = shaderInstance;
    }

    public static ShaderInstance getSdfShader() {
        return sdfShader != null ? sdfShader : GameRenderer.getRendertypeTextIntensityShader();
    }
}
