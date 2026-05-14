package org.zhi.zhifontgo.data;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ZhiFontData(
    String familyName,
    float size,
    float oversample,
    float shiftX,
    float shiftY,
    String skip,
    String seedCharacters
) {
}
