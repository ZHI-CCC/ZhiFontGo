package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$TextDisplayRenderer")
public abstract class ZhiTextDisplayRendererMixin {
    @Shadow
    @Final
    private Font font;

    @Redirect(
        method = {"splitLines", "renderInner"},
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/entity/DisplayRenderer$TextDisplayRenderer;font:Lnet/minecraft/client/gui/Font;"
        )
    )
    private Font zhifontgo$useRuntimeFont(DisplayRenderer.TextDisplayRenderer renderer) {
        ZhiFontRenderer runtimeFont = ZhiFontManager.activeWorldRuntimeFont();
        return runtimeFont != null ? runtimeFont : this.font;
    }
}
