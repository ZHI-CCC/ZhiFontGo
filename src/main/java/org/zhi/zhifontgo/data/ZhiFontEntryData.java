package org.zhi.zhifontgo.data;

import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ZhiFontEntryData(
    String displayName,
    String relativePath,
    Path filePath
) {
}
