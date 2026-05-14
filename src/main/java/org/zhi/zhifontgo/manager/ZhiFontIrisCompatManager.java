package org.zhi.zhifontgo.manager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import org.zhi.zhifontgo.Main;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontIrisCompatManager {
    private static final String IRIS_MOD_ID = "iris";
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private ZhiFontIrisCompatManager() {
    }

    public static boolean shouldUseCompatibleWorldText() {
        if (!ModList.get().isLoaded(IRIS_MOD_ID)) {
            return false;
        }

        try {
            Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
            Method getInstanceMethod = irisApiClass.getMethod("getInstance");
            Object irisApi = getInstanceMethod.invoke(null);
            Method isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            Object result = isShaderPackInUseMethod.invoke(irisApi);
            return result instanceof Boolean enabled && enabled;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            Main.LOGGER.debug("ZhiFontGo failed to query Iris shader state, keeping custom world font.", exception);
            return false;
        }
    }
}
