package org.zhi.zhifontgo.mixin;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FontManager.class)
public interface ZhiVanillaFontManagerAccessor {
    @Invoker("getFontSetRaw")
    FontSet zhifontgo$invokeGetFontSetRaw(ResourceLocation fontId);
}
