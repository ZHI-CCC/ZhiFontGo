package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@Mixin(SignRenderer.class)
public abstract class ZhiSignRendererMixin {
    @Shadow
    @Final
    private Font font;

    @Redirect(
        method = "renderSignText",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/blockentity/SignRenderer;font:Lnet/minecraft/client/gui/Font;"
        )
    )
    private Font zhifontgo$useRuntimeFont(SignRenderer renderer) {
        ZhiFontRenderer runtimeFont = ZhiFontManager.activeWorldRuntimeFont();
        return runtimeFont != null ? runtimeFont : this.font;
    }
}
