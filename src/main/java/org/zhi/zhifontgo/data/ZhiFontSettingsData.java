package org.zhi.zhifontgo.data;

import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ZhiFontSettingsData(
    @Nullable String selectedFontRelativePath,
    float size,
    float oversample,
    float shiftX,
    float shiftY,
    boolean shadowEnabled
) {
}
