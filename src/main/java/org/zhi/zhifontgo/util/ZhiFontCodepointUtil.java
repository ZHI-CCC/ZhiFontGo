package org.zhi.zhifontgo.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontCodepointUtil {
    private ZhiFontCodepointUtil() {
    }

    public static IntSet buildSeedGlyphs(String seedCharacters) {
        IntSet codepoints = new IntOpenHashSet();
        addRange(codepoints, 0x0020, 0x007E);
        addRange(codepoints, 0x00A0, 0x00FF);
        addRange(codepoints, 0x2000, 0x206F);
        addRange(codepoints, 0x3000, 0x303F);
        addRange(codepoints, 0x3040, 0x309F);
        addRange(codepoints, 0x30A0, 0x30FF);
        addRange(codepoints, 0x4E00, 0x9FFF);
        addRange(codepoints, 0xFF00, 0xFFEF);
        seedCharacters.codePoints().forEach(codepoints::add);
        codepoints.add(' ');
        return codepoints;
    }

    private static void addRange(IntSet codepoints, int startInclusive, int endInclusive) {
        for (int codepoint = startInclusive; codepoint <= endInclusive; codepoint++) {
            codepoints.add(codepoint);
        }
    }
}
