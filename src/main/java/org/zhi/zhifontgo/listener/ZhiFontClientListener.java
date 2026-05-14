package org.zhi.zhifontgo.listener;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.manager.ZhiFontShaderManager;
import org.zhi.zhifontgo.util.ZhiFontUtil;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Main.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZhiFontClientListener {
    private ZhiFontClientListener() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ZhiFontManager::initializeClientState);
        ZhiFontManager.logBootstrap(Main.LOGGER);
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ZhiFontManager.reloadManager());
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ZhiFontUtil.mod("zhifont_sdf"), DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
            ZhiFontShaderManager::setSdfShader
        );
    }
}
