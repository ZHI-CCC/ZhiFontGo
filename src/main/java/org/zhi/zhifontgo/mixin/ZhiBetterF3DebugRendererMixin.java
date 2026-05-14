package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontRenderer;

@Pseudo
@Mixin(targets = "me.cominixo.betterf3.utils.DebugRenderer", remap = false)
public abstract class ZhiBetterF3DebugRendererMixin {
    @ModifyVariable(method = "immediate", at = @At("HEAD"), argsOnly = true)
    private static Font zhifontgo$useRuntimeFontForImmediate(Font originalFont) {
        return runtimeFontOrOriginal(originalFont);
    }

    @ModifyVariable(method = "drawLeftText", at = @At("HEAD"), argsOnly = true)
    private static Font zhifontgo$useRuntimeFontForLeft(Font originalFont) {
        return runtimeFontOrOriginal(originalFont);
    }

    @ModifyVariable(method = "drawRightText", at = @At("HEAD"), argsOnly = true)
    private static Font zhifontgo$useRuntimeFontForRight(Font originalFont) {
        return runtimeFontOrOriginal(originalFont);
    }

    private static Font runtimeFontOrOriginal(Font originalFont) {
        ZhiFontRenderer runtimeFont = ZhiFontManager.activeRuntimeFont();
        return runtimeFont != null ? runtimeFont : originalFont;
    }
}
