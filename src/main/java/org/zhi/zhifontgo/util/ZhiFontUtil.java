package org.zhi.zhifontgo.util;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.Main;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontUtil {
    private ZhiFontUtil() {
    }

    public static ResourceLocation mod(String path) {
        return ResourceLocation.fromNamespaceAndPath(Main.MODID, path);
    }

    public static ResourceLocation minecraft(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
