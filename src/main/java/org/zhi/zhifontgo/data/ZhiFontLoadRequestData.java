package org.zhi.zhifontgo.data;

import java.nio.file.Path;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ZhiFontLoadRequestData(
    Mode mode,
    String fontLabel,
    @Nullable Path fontPath,
    byte[] fontBytes,
    float size,
    float oversample,
    float shiftX,
    float shiftY,
    boolean shadowEnabled
) {
    public enum Mode {
        STARTUP,
        APPLY,
        REAPPLY
    }
}
