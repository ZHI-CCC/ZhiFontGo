package org.zhi.zhifontgo.listener;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.manager.ZhiFontItemLabelManager;

@EventBusSubscriber(modid = Main.MODID, value = Dist.CLIENT)
public final class ZhiFontLoadingListener {
    private ZhiFontLoadingListener() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        ZhiFontItemLabelManager.render(event);
    }
}
