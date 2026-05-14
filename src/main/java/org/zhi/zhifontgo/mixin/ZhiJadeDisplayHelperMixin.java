package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@Pseudo
@Mixin(targets = "snownee.jade.overlay.DisplayHelper", remap = false)
public abstract class ZhiJadeDisplayHelperMixin {
    @Inject(method = "font", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zhifontgo$useRuntimeFont(CallbackInfoReturnable<Font> cir) {
        ZhiFontRenderer runtimeFont = ZhiFontManager.activeRuntimeFont();
        if (runtimeFont != null) {
            cir.setReturnValue(runtimeFont);
        }
    }
}
