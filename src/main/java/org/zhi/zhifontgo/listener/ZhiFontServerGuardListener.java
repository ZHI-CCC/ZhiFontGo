package org.zhi.zhifontgo.listener;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.manager.ZhiFontServerGuardManager;

@EventBusSubscriber(modid = Main.MODID, value = Dist.CLIENT)
public final class ZhiFontServerGuardListener {
    private ZhiFontServerGuardListener() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ZhiFontServerGuardManager.validateCurrentServer(event.getConnection());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ZhiFontServerGuardManager.clear();
    }
}
