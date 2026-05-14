package org.zhi.zhifontgo;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.zhi.zhifontgo.manager.ZhiFontManager;
import org.zhi.zhifontgo.screen.ZhiFontSelectScreen;

@Mod(Main.MODID)
public class Main {
    public static final String MODID = "zhifontgo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Main(ModContainer modContainer, Dist dist) {
        if (dist == Dist.CLIENT) {
            ZhiFontManager.primeClientBootstrap();
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, modListScreen) -> new ZhiFontSelectScreen(modListScreen));
        }
        LOGGER.info("ZhiFontGo mod bootstrap complete.");
    }
}
