package org.zhi.zhifontgo.screen;

import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.data.ZhiFontEntryData;
import org.zhi.zhifontgo.data.ZhiFontLoadRequestData;
import org.zhi.zhifontgo.data.ZhiFontLoadResultData;
import org.zhi.zhifontgo.data.ZhiFontPreparedFontData;
import org.zhi.zhifontgo.manager.ZhiFontManager;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontSelectScreen extends Screen {
    private static final int SETTINGS_TOP = 66;
    private static final int SETTINGS_ROW_HEIGHT = 24;
    private static final int PREVIEW_TOP = 160;
    private static final int PREVIEW_HEIGHT = 72;
    private static final int LIST_TOP = 244;
    private static final int ROW_HEIGHT = 24;
    private static final Component TITLE = Component.translatable("screen.zhifontgo.title");
    private static final Component DIRECTORY_HINT = Component.translatable("screen.zhifontgo.directory_hint");
    private static final Component NO_FONTS = Component.translatable("screen.zhifontgo.no_fonts");
    private static final Component APPLY_HINT = Component.translatable("screen.zhifontgo.apply_hint");
    private static final Component PREVIEW_TITLE = Component.translatable("screen.zhifontgo.preview_title");
    private final Screen parentScreen;
    private List<ZhiFontEntryData> fontEntries = List.of();
    @Nullable
    private Component statusMessage;
    @Nullable
    private ZhiFontEntryData pendingFontEntry;
    @Nullable
    private ZhiFontPreparedFontData previewPreparedFont;
    private int pageIndex;
    private float previewSize;
    private float previewOversample;
    private float previewShiftX;
    private float previewShiftY;
    private boolean previewShadowEnabled;

    public ZhiFontSelectScreen(@Nullable Screen parentScreen) {
        this(parentScreen, 0, null);
    }

    public ZhiFontSelectScreen(@Nullable Screen parentScreen, int pageIndex, @Nullable Component statusMessage) {
        super(TITLE);
        this.parentScreen = parentScreen;
        this.pageIndex = Math.max(pageIndex, 0);
        this.statusMessage = statusMessage;
        this.previewSize = ZhiFontManager.currentSize();
        this.previewOversample = ZhiFontManager.currentOversample();
        this.previewShiftX = ZhiFontManager.currentShiftX();
        this.previewShiftY = ZhiFontManager.currentShiftY();
        this.previewShadowEnabled = ZhiFontManager.currentShadowEnabled();
    }

    @Override
    protected void init() {
        if (this.fontEntries.isEmpty()) {
            this.fontEntries = ZhiFontManager.discoverFonts();
        }
        if (this.pendingFontEntry == null) {
            this.pendingFontEntry = this.resolvePendingFontEntry();
        }
        if (this.previewPreparedFont == null && this.pendingFontEntry != null) {
            this.rebuildPreviewFont();
        }

        this.pageIndex = Math.min(this.pageIndex, this.getLastPageIndex());
        this.addSettingButtons(20, SETTINGS_TOP, false);
        this.addSettingButtons(this.width / 2 + 10, SETTINGS_TOP, true);
        this.addRenderableWidget(
            Button.builder(this.shadowToggleText(), button -> this.togglePreviewShadow())
                .bounds(this.width / 2 - 90, SETTINGS_TOP + SETTINGS_ROW_HEIGHT * 2, 180, 20)
                .build()
        );

        int listWidth = Math.min(360, this.width - 40);
        int left = (this.width - listWidth) / 2;
        int visibleCount = this.getVisibleEntryCount();
        int startIndex = this.pageIndex * visibleCount;
        int endIndex = Math.min(startIndex + visibleCount, this.fontEntries.size());

        for (int index = startIndex; index < endIndex; index++) {
            ZhiFontEntryData fontEntry = this.fontEntries.get(index);
            int rowY = LIST_TOP + (index - startIndex) * ROW_HEIGHT;
            this.addRenderableWidget(
                Button.builder(Component.literal(this.buttonLabel(fontEntry)), button -> this.previewFont(fontEntry))
                    .bounds(left, rowY, listWidth, 20)
                    .build()
            );
        }

        int bottomY = this.height - 28;
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.zhifontgo.prev"), button -> this.changePage(-1))
                .bounds(left, bottomY, 70, 20)
                .build()
        ).active = this.pageIndex > 0;
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.zhifontgo.next"), button -> this.changePage(1))
                .bounds(left + 76, bottomY, 70, 20)
                .build()
        ).active = this.pageIndex < this.getLastPageIndex();
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.zhifontgo.refresh"), button -> this.refreshFonts(Component.translatable("screen.zhifontgo.status.refreshed")))
                .bounds(left + 152, bottomY, 80, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.zhifontgo.restore_vanilla"), button -> this.previewVanilla())
                .bounds(left + 238, bottomY, 122, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.zhifontgo.reset_tuning"), button -> this.resetPreviewTuning())
                .bounds(left, this.height - 54, 110, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.zhifontgo.confirm_apply"), button -> this.confirmChanges())
                .bounds(left + 116, this.height - 54, 110, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(left + Math.max(0, listWidth - 80), this.height - 54, 80, 20)
                .build()
        );
    }

    @Override
    public void onClose() {
        this.closePreviewFont();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public void removed() {
        this.closePreviewFont();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, DIRECTORY_HINT, this.width / 2, 30, 0xA0A0A0);

        guiGraphics.drawString(this.font, this.currentFontText(), 20, 44, 0xFFFFFF);
        guiGraphics.drawString(this.font, this.pendingFontText(), 20, 54, 0xFFD37F);
        this.renderSettingTexts(guiGraphics);
        this.renderPreviewPanel(guiGraphics);
        guiGraphics.drawString(
            this.font,
            Component.translatable("screen.zhifontgo.page_value", this.pageIndex + 1, Math.max(1, this.getLastPageIndex() + 1)),
            20,
            this.height - 22,
            0xA0A0A0
        );

        if (this.statusMessage != null) {
            guiGraphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height - 78, 0xFFD37F);
        }

        if (this.fontEntries.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, NO_FONTS, this.width / 2, LIST_TOP + 20, 0xFFFFFF);
        } else {
            int previewTop = LIST_TOP + this.getVisibleEntryCount() * ROW_HEIGHT + 10;
            if (previewTop < this.height - 90) {
                guiGraphics.drawCenteredString(this.font, APPLY_HINT, this.width / 2, previewTop, 0xA0A0A0);
            }
        }
    }

    private void previewFont(ZhiFontEntryData fontEntry) {
        this.pendingFontEntry = fontEntry;
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_font", fontEntry.displayName());
        this.rebuildWidgets();
    }

    private void previewVanilla() {
        this.pendingFontEntry = null;
        this.closePreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_vanilla");
        this.rebuildWidgets();
    }

    private void confirmChanges() {
        try {
            if (this.pendingFontEntry == null) {
                ZhiFontManager.setStoredTuning(
                    this.previewSize,
                    this.previewOversample,
                    this.previewShiftX,
                    this.previewShiftY,
                    this.previewShadowEnabled
                );
                ZhiFontManager.clearSelectionAndRestoreVanilla();
                this.statusMessage = Component.translatable("screen.zhifontgo.status.restored");
                this.refreshFonts(null);
                return;
            }

            ZhiFontLoadRequestData request = ZhiFontManager.createLoadRequest(
                this.pendingFontEntry,
                ZhiFontLoadRequestData.Mode.APPLY,
                this.previewSize,
                this.previewOversample,
                this.previewShiftX,
                this.previewShiftY,
                this.previewShadowEnabled
            );
            this.openLoadingScreen(
                request,
                result -> result.successful()
                    ? Component.translatable(
                        "screen.zhifontgo.status.applied",
                        this.pendingFontEntry.displayName(),
                        this.formatValue(this.previewSize),
                        this.formatValue(this.previewOversample)
                    )
                    : Component.translatable("screen.zhifontgo.status.failed", this.pendingFontEntry.displayName())
            );
        } catch (RuntimeException exception) {
            Main.LOGGER.error("ZhiFontGo failed to confirm font changes.", exception);
            this.statusMessage = Component.translatable("screen.zhifontgo.status.failed", this.pendingFontEntry != null ? this.pendingFontEntry.displayName() : "vanilla");
            this.refreshFonts(null);
        }
    }

    private void resetPreviewTuning() {
        this.previewSize = 8.0F;
        this.previewOversample = 8.0F;
        this.previewShiftX = 0.0F;
        this.previewShiftY = 0.0F;
        this.previewShadowEnabled = true;
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_tuning");
        this.rebuildWidgets();
    }

    private void refreshFonts(@Nullable Component refreshedMessage) {
        this.fontEntries = ZhiFontManager.discoverFonts();
        this.pendingFontEntry = this.resolvePendingFontEntry();
        this.pageIndex = Math.min(this.pageIndex, this.getLastPageIndex());
        if (refreshedMessage != null) {
            this.statusMessage = refreshedMessage;
        }
        this.font = this.minecraft.font;
        this.rebuildPreviewFont();
        this.rebuildWidgets();
    }

    private void changePage(int pageDelta) {
        this.pageIndex = Math.max(0, Math.min(this.pageIndex + pageDelta, this.getLastPageIndex()));
        this.rebuildWidgets();
    }

    private int getVisibleEntryCount() {
        return Math.max(1, Math.min(6, (this.height - LIST_TOP - 64) / ROW_HEIGHT));
    }

    private int getLastPageIndex() {
        int visibleCount = this.getVisibleEntryCount();
        if (this.fontEntries.isEmpty()) {
            return 0;
        }
        return Math.max(0, (this.fontEntries.size() - 1) / visibleCount);
    }

    private String buttonLabel(ZhiFontEntryData fontEntry) {
        boolean pending = this.pendingFontEntry != null && fontEntry.filePath().equals(this.pendingFontEntry.filePath());
        boolean active = fontEntry.filePath().equals(ZhiFontManager.selectedFontPath());
        String prefix = active
            ? Component.translatable("screen.zhifontgo.active_prefix").getString()
            : pending ? Component.translatable("screen.zhifontgo.pending_prefix").getString() : "";
        return prefix + fontEntry.displayName();
    }

    private Component currentFontText() {
        String currentLabel = ZhiFontManager.selectedFontLabel();
        if (currentLabel == null) {
            return Component.translatable("screen.zhifontgo.current.vanilla");
        }
        return Component.translatable("screen.zhifontgo.current.selected", currentLabel);
    }

    private Component pendingFontText() {
        if (this.pendingFontEntry == null) {
            return Component.translatable("screen.zhifontgo.pending.vanilla");
        }
        return Component.translatable("screen.zhifontgo.pending.selected", this.pendingFontEntry.displayName());
    }

    private void addSettingButtons(int left, int top, boolean rightColumn) {
        if (!rightColumn) {
            this.addMinusPlusButtons(left, top, () -> this.adjustSize(-0.5F), () -> this.adjustSize(0.5F));
            this.addMinusPlusButtons(left, top + SETTINGS_ROW_HEIGHT, () -> this.adjustOversample(-0.25F), () -> this.adjustOversample(0.25F));
        } else {
            this.addMinusPlusButtons(left, top, () -> this.adjustShiftX(-0.25F), () -> this.adjustShiftX(0.25F));
            this.addMinusPlusButtons(left, top + SETTINGS_ROW_HEIGHT, () -> this.adjustShiftY(-0.25F), () -> this.adjustShiftY(0.25F));
        }
    }

    private void addMinusPlusButtons(int left, int top, Runnable minusAction, Runnable plusAction) {
        this.addRenderableWidget(
            Button.builder(Component.literal("-"), button -> minusAction.run())
                .bounds(left + 150, top, 20, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("+"), button -> plusAction.run())
                .bounds(left + 174, top, 20, 20)
                .build()
        );
    }

    private void renderSettingTexts(GuiGraphics guiGraphics) {
        int leftColumn = 20;
        int rightColumn = this.width / 2 + 10;
        this.renderSettingLine(guiGraphics, leftColumn, SETTINGS_TOP, "screen.zhifontgo.size", this.formatValue(this.previewSize));
        this.renderSettingLine(guiGraphics, leftColumn, SETTINGS_TOP + SETTINGS_ROW_HEIGHT, "screen.zhifontgo.oversample", this.formatValue(this.previewOversample));
        this.renderSettingLine(guiGraphics, rightColumn, SETTINGS_TOP, "screen.zhifontgo.shift_x", this.formatValue(this.previewShiftX));
        this.renderSettingLine(guiGraphics, rightColumn, SETTINGS_TOP + SETTINGS_ROW_HEIGHT, "screen.zhifontgo.shift_y", this.formatValue(this.previewShiftY));
        guiGraphics.drawString(
            this.font,
            Component.translatable("screen.zhifontgo.quality_hint"),
            20,
            SETTINGS_TOP + SETTINGS_ROW_HEIGHT * 3 + 6,
            0xA0A0A0
        );
    }

    private void renderPreviewPanel(GuiGraphics guiGraphics) {
        Font previewFont = this.previewFont();
        int left = 20;
        int right = this.width - 20;
        int top = PREVIEW_TOP;
        int bottom = PREVIEW_TOP + PREVIEW_HEIGHT;
        int textLeft = left + 8;

        guiGraphics.fill(left, top, right, bottom, 0xCC111111);
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0x66383838);
        guiGraphics.drawString(this.font, PREVIEW_TITLE, textLeft, top + 5, 0xFFE39A);
        guiGraphics.drawString(previewFont, Component.translatable("screen.zhifontgo.preview_plain"), textLeft, top + 17, 0xFFFFFF);
        guiGraphics.drawString(previewFont, this.previewColorText(), textLeft, top + 29, 0xFFFFFF);
        guiGraphics.drawString(previewFont, this.previewStyleText(), textLeft, top + 41, 0xFFFFFF);
        guiGraphics.drawString(previewFont, this.previewInlineEffectText(), textLeft, top + 53, 0xFFFFFF);
        guiGraphics.drawString(previewFont, Component.translatable("screen.zhifontgo.preview_shadow"), textLeft, top + 65, 0xE8E8E8, this.previewShadowEnabled);
    }

    private void renderSettingLine(GuiGraphics guiGraphics, int left, int top, String labelKey, String value) {
        guiGraphics.drawString(this.font, Component.translatable(labelKey, value), left, top + 6, 0xFFFFFF);
    }

    private void adjustSize(float delta) {
        this.previewSize = this.clampAndRound(this.previewSize + delta, 6.0F, 48.0F);
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_tuning");
        this.rebuildWidgets();
    }

    private void adjustOversample(float delta) {
        this.previewOversample = this.clampAndRound(this.previewOversample + delta, 1.0F, 8.0F);
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_tuning");
        this.rebuildWidgets();
    }

    private void adjustShiftX(float delta) {
        this.previewShiftX = this.clampAndRound(this.previewShiftX + delta, -8.0F, 8.0F);
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_tuning");
        this.rebuildWidgets();
    }

    private void adjustShiftY(float delta) {
        this.previewShiftY = this.clampAndRound(this.previewShiftY + delta, -8.0F, 8.0F);
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_tuning");
        this.rebuildWidgets();
    }

    private void togglePreviewShadow() {
        this.previewShadowEnabled = !this.previewShadowEnabled;
        this.rebuildPreviewFont();
        this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_tuning");
        this.rebuildWidgets();
    }

    private void rebuildPreviewFont() {
        this.closePreviewFont();
        if (this.pendingFontEntry == null) {
            return;
        }

        try {
            ZhiFontLoadRequestData request = ZhiFontManager.createLoadRequest(
                this.pendingFontEntry,
                ZhiFontLoadRequestData.Mode.APPLY,
                this.previewSize,
                this.previewOversample,
                this.previewShiftX,
                this.previewShiftY,
                this.previewShadowEnabled
            );
            this.previewPreparedFont = ZhiFontManager.preparePreviewFont(
                request.fontLabel(),
                request.fontBytes(),
                request.size(),
                request.oversample(),
                request.shiftX(),
                request.shiftY(),
                request.shadowEnabled()
            );
            this.warmUpPreviewText();
        } catch (RuntimeException exception) {
            Main.LOGGER.error("ZhiFontGo failed to build preview font for {}.", this.pendingFontEntry.filePath(), exception);
            this.closePreviewFont();
            this.statusMessage = Component.translatable("screen.zhifontgo.status.preview_failed", this.pendingFontEntry.displayName());
        }
    }

    private void warmUpPreviewText() {
        if (this.previewPreparedFont == null) {
            return;
        }

        this.previewTextPayload().codePoints().forEach(codepoint -> {
            if (codepoint == '<' || codepoint == '>' || codepoint == '/') {
                return;
            }
            this.previewPreparedFont.glyphProvider().warmUpGlyph(codepoint);
            this.previewPreparedFont.atlasManager().findGlyph(codepoint);
        });
    }

    private String previewTextPayload() {
        return Component.translatable("screen.zhifontgo.preview_plain").getString()
            + this.previewColorText().getString()
            + this.previewStyleText().getString()
            + this.previewInlineEffectText().getString()
            + Component.translatable("screen.zhifontgo.preview_shadow").getString();
    }

    private void closePreviewFont() {
        if (this.previewPreparedFont != null) {
            this.previewPreparedFont.close();
            this.previewPreparedFont = null;
        }
    }

    private Font previewFont() {
        return this.previewPreparedFont != null ? this.previewPreparedFont.runtimeFont() : this.font;
    }

    @Nullable
    private ZhiFontEntryData resolvePendingFontEntry() {
        if (this.pendingFontEntry != null) {
            for (ZhiFontEntryData fontEntry : this.fontEntries) {
                if (fontEntry.filePath().equals(this.pendingFontEntry.filePath())) {
                    return fontEntry;
                }
            }
            return this.pendingFontEntry;
        }

        if (ZhiFontManager.selectedFontPath() == null || ZhiFontManager.selectedFontLabel() == null) {
            return null;
        }

        for (ZhiFontEntryData fontEntry : this.fontEntries) {
            if (fontEntry.filePath().equals(ZhiFontManager.selectedFontPath())) {
                return fontEntry;
            }
        }

        return new ZhiFontEntryData(
            ZhiFontManager.selectedFontLabel(),
            ZhiFontManager.selectedFontPath().getFileName().toString(),
            ZhiFontManager.selectedFontPath()
        );
    }

    private void openLoadingScreen(ZhiFontLoadRequestData request, Function<ZhiFontLoadResultData, Component> statusFactory) {
        if (this.minecraft == null) {
            return;
        }

        int currentPage = this.pageIndex;
        Screen parent = this.parentScreen;
        this.closePreviewFont();
        this.minecraft.setScreen(
            new ZhiFontLoadingScreen(
                request,
                result -> new ZhiFontSelectScreen(parent, currentPage, statusFactory.apply(result))
            )
        );
    }

    private float clampAndRound(float value, float min, float max) {
        float clamped = Math.max(min, Math.min(max, value));
        return Math.round(clamped * 100.0F) / 100.0F;
    }

    private String formatValue(float value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private Component previewColorText() {
        return Component.empty()
            .append(Component.translatable("screen.zhifontgo.preview_color_prefix"))
            .append(Component.literal("Red ").withStyle(ChatFormatting.RED))
            .append(Component.literal("Gold ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("Green ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("Aqua ").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("Yellow").withStyle(ChatFormatting.YELLOW));
    }

    private Component previewStyleText() {
        return Component.empty()
            .append(Component.translatable("screen.zhifontgo.preview_style_prefix"))
            .append(Component.literal("Bold ").withStyle(ChatFormatting.BOLD))
            .append(Component.literal("Italic ").withStyle(ChatFormatting.ITALIC))
            .append(Component.literal("Underline").withStyle(ChatFormatting.UNDERLINE));
    }

    private Component previewInlineEffectText() {
        return Component.empty()
            .append(Component.translatable("screen.zhifontgo.preview_effect_prefix"))
            .append(Component.literal("<zf:shine>\u6D41\u5149<zf> "))
            .append(Component.literal("<zf:wave>\u6CE2\u6D6A<zf> "))
            .append(Component.literal("<zf:rainbow>\u5F69\u8679<zf> "))
            .append(Component.literal("<zf:shake>\u6296\u52A8<zf> "))
            .append(Component.literal("<zf:pulse>\u547C\u5438<zf>"));
    }

    private Component shadowToggleText() {
        return Component.translatable(
            "screen.zhifontgo.shadow_toggle",
            Component.translatable(this.previewShadowEnabled ? "screen.zhifontgo.shadow_enabled" : "screen.zhifontgo.shadow_disabled")
        );
    }
}
