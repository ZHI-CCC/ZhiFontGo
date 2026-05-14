package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@Mixin(EntityRenderer.class)
public abstract class ZhiEntityRendererMixin {
    @Redirect(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getFont()Lnet/minecraft/client/gui/Font;"
        )
    )
    private Font zhifontgo$useRuntimeFont(EntityRenderer<?> renderer) {
        ZhiFontRenderer runtimeFont = ZhiFontManager.activeWorldRuntimeFont();
        return runtimeFont != null ? runtimeFont : renderer.getFont();
    }
}
