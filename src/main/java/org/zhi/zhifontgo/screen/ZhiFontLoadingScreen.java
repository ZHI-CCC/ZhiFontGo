package org.zhi.zhifontgo.screen;

import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.data.ZhiFontLoadRequestData;
import org.zhi.zhifontgo.data.ZhiFontLoadResultData;
import org.zhi.zhifontgo.manager.ZhiFontLoadingManager;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontLoadingScreen extends Screen {
    private final ZhiFontLoadRequestData request;
    private final Function<ZhiFontLoadResultData, Screen> completionScreenFactory;
    private boolean loadingStarted;

    public ZhiFontLoadingScreen(ZhiFontLoadRequestData request, Function<ZhiFontLoadResultData, Screen> completionScreenFactory) {
        super(Component.translatable("screen.zhifontgo.loading.title"));
        this.request = request;
        this.completionScreenFactory = completionScreenFactory;
    }

    @Override
    protected void init() {
        if (!this.loadingStarted) {
            ZhiFontLoadingManager.start(this.request);
            this.loadingStarted = true;
        }
    }

    @Override
    public void tick() {
        ZhiFontLoadingManager.tick();
        if (this.minecraft == null || !ZhiFontLoadingManager.isFinished()) {
            return;
        }

        ZhiFontLoadResultData result = ZhiFontLoadingManager.consumeResult();
        if (result == null) {
            return;
        }
        this.minecraft.setScreen(this.completionScreenFactory.apply(result));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - 140;
        int right = this.width / 2 + 140;
        int top = this.height / 2 - 18;
        int bottom = top + 16;
        int fillRight = left + Math.round((right - left) * ZhiFontLoadingManager.progress());

        if (this.request.mode() == ZhiFontLoadRequestData.Mode.STARTUP) {
            guiGraphics.fill(left, top, right, bottom, 0xFF202020);
            if (fillRight > left + 1) {
                guiGraphics.fill(left + 1, top + 1, fillRight - 1, bottom - 1, 0xFF6BCB77);
            }
            guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("screen.zhifontgo.loading.startup_hint"),
                this.width / 2,
                this.height / 2 + 24,
                0xFFFFFF
            );
            return;
        }

        guiGraphics.drawCenteredString(this.font, Component.translatable("screen.zhifontgo.loading.title"), this.width / 2, this.height / 2 - 54, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, this.modeText(), this.width / 2, this.height / 2 - 34, 0xFFD37F);
        guiGraphics.drawCenteredString(this.font, Component.literal(this.request.fontLabel()), this.width / 2, this.height / 2 - 20, 0xE0E0E0);
        guiGraphics.fill(left, top, right, bottom, 0xFF202020);
        if (fillRight > left + 1) {
            guiGraphics.fill(left + 1, top + 1, fillRight - 1, bottom - 1, 0xFF6BCB77);
        }
        guiGraphics.drawCenteredString(this.font, this.progressText(), this.width / 2, this.height / 2 + 8, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("screen.zhifontgo.loading.hint"), this.width / 2, this.height / 2 + 24, 0xA0A0A0);
    }

    private Component modeText() {
        return switch (this.request.mode()) {
            case STARTUP -> Component.translatable("screen.zhifontgo.loading.mode.startup");
            case APPLY -> Component.translatable("screen.zhifontgo.loading.mode.apply");
            case REAPPLY -> Component.translatable("screen.zhifontgo.loading.mode.reapply");
        };
    }

    private Component progressText() {
        return Component.translatable(
            "screen.zhifontgo.loading.progress",
            ZhiFontLoadingManager.processedGlyphs(),
            Math.max(1, ZhiFontLoadingManager.totalGlyphs())
        );
    }
}
