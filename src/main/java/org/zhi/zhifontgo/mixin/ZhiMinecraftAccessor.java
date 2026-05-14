package org.zhi.zhifontgo.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface ZhiMinecraftAccessor {
    @Accessor("font")
    @Mutable
    void zhifontgo$setFont(Font font);

    @Accessor("fontFilterFishy")
    @Mutable
    void zhifontgo$setFontFilterFishy(Font fontFilterFishy);

    @Accessor("fontManager")
    FontManager zhifontgo$getFontManager();
}
