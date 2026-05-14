package org.zhi.zhifontgo.data;

import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ZhiFontLoadResultData(
    boolean successful,
    @Nullable String errorMessage
) {
    public static ZhiFontLoadResultData success() {
        return new ZhiFontLoadResultData(true, null);
    }

    public static ZhiFontLoadResultData failure(String errorMessage) {
        return new ZhiFontLoadResultData(false, errorMessage);
    }
}
