package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@Mixin(FontManager.class)
public final class ZhiVanillaFontManagerMixin {
    @Inject(method = "createFont", at = @At("HEAD"), cancellable = true)
    private void zhifontgo$useRuntimeFont(CallbackInfoReturnable<Font> cir) {
        ZhiFontRenderer runtimeFont = ZhiFontManager.activeRuntimeFont();
        if (runtimeFont != null) {
            cir.setReturnValue(runtimeFont);
        }
    }

    @Inject(method = "createFontFilterFishy", at = @At("HEAD"), cancellable = true)
    private void zhifontgo$useRuntimeFontFilterFishy(CallbackInfoReturnable<Font> cir) {
        ZhiFontRenderer runtimeFontFilterFishy = ZhiFontManager.activeRuntimeFontFilterFishy();
        if (runtimeFontFilterFishy != null) {
            cir.setReturnValue(runtimeFontFilterFishy);
        }
    }
}
