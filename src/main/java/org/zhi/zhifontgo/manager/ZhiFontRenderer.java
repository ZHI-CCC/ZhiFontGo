package org.zhi.zhifontgo.manager;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.util.StringDecomposer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.zhi.zhifontgo.mixin.ZhiVanillaFontManagerAccessor;
import org.zhi.zhifontgo.util.ZhiFontUtil;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontRenderer extends Font {
    private static final ResourceLocation DEFAULT_FONT_ID = ZhiFontUtil.minecraft("default");
    private static final ResourceLocation UNIFORM_FONT_ID = ZhiFontUtil.minecraft("uniform");
    private static final Vector3f SHADOW_OFFSET = new Vector3f(0.0F, 0.0F, 0.03F);
    private static final String EFFECT_TAG_NAMESPACE = "zf";
    private static final String EFFECT_TAG_OPEN = "<" + EFFECT_TAG_NAMESPACE;
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private final ZhiVanillaFontManagerAccessor vanillaFontManagerAccessor;
    private final boolean filterFishyGlyphs;
    private final boolean shadowEnabled;
    private final ZhiFontAtlasManager customAtlas;
    private final boolean worldIrisCompatible;
    private final StringSplitter customSplitter;
    private final StringSplitter effectAwareSplitter;

    public ZhiFontRenderer(
        ZhiVanillaFontManagerAccessor vanillaFontManagerAccessor,
        boolean filterFishyGlyphs,
        boolean shadowEnabled,
        ZhiFontAtlasManager customAtlas,
        boolean worldIrisCompatible
    ) {
        super(vanillaFontManagerAccessor::zhifontgo$invokeGetFontSetRaw, filterFishyGlyphs);
        this.vanillaFontManagerAccessor = vanillaFontManagerAccessor;
        this.filterFishyGlyphs = filterFishyGlyphs;
        this.shadowEnabled = shadowEnabled;
        this.customAtlas = customAtlas;
        this.worldIrisCompatible = worldIrisCompatible;
        this.customSplitter = new StringSplitter((codepoint, style) -> this.resolveAdvance(style, codepoint));
        this.effectAwareSplitter = new EffectAwareStringSplitter();
    }

    @Override
    public int drawInBatch(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        return this.drawInBatch(text, x, y, color, dropShadow, pose, bufferSource, displayMode, backgroundColor, packedLight, this.isBidirectional());
    }

    @Override
    public int drawInBatch(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight,
        boolean bidirectional
    ) {
        if (bidirectional) {
            text = this.bidirectionalShaping(text);
        }
        return this.drawInternal(text, x, y, color, dropShadow, pose, bufferSource, displayMode, backgroundColor, packedLight);
    }

    @Override
    public int drawInBatch(
        Component text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        return this.drawInBatch(text.getVisualOrderText(), x, y, color, dropShadow, pose, bufferSource, displayMode, backgroundColor, packedLight);
    }

    @Override
    public int drawInBatch(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        return this.drawInternal(text, x, y, color, dropShadow, pose, bufferSource, displayMode, backgroundColor, packedLight);
    }

    @Override
    public void drawInBatch8xOutline(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        int backgroundColor,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        ParsedText parsedText = this.parseFormattedSequence(text, !this.shouldBypassEffectParsing());
        int outlineColor = adjustColor(backgroundColor);
        RenderOutput outlineOutput = new RenderOutput(bufferSource, 0.0F, 0.0F, outlineColor, false, pose, Font.DisplayMode.NORMAL, packedLight, parsedText.segmentWidths());

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                if (offsetX != 0 || offsetY != 0) {
                    float currentX = x;
                    for (ParsedGlyph glyph : parsedText.glyphs()) {
                        GlyphInfo glyphInfo = this.resolveGlyphInfo(glyph.style(), glyph.codepoint(), glyph.effectStyle());
                        outlineOutput.setPosition(
                            currentX + (float)offsetX * glyphInfo.getShadowOffset(),
                            y + (float)offsetY * glyphInfo.getShadowOffset()
                        );
                        outlineOutput.accept(glyph.index(), glyph.style().withColor(outlineColor), glyph.codepoint(), glyph.effectStyle().outlineStyle());
                        currentX += this.resolveAdvance(glyph.style(), glyph.codepoint(), glyph.effectStyle());
                    }
                }
            }
        }

        RenderOutput fillOutput = new RenderOutput(bufferSource, x, y, adjustColor(color), false, pose, Font.DisplayMode.POLYGON_OFFSET, packedLight, parsedText.segmentWidths());
        this.renderParsedText(parsedText, fillOutput);
        fillOutput.finish(0, x);
    }

    @Override
    public int width(String text) {
        if (this.shouldParseEffectTags(text)) {
            return Mth.ceil(this.parseLiteralString(text, true).totalWidth());
        }
        return Mth.ceil(this.customSplitter.stringWidth(text));
    }

    @Override
    public int width(FormattedText text) {
        return this.width(Language.getInstance().getVisualOrder(text));
    }

    @Override
    public int width(FormattedCharSequence text) {
        if (this.shouldParseEffectTags(text)) {
            return Mth.ceil(this.parseFormattedSequence(text, true).totalWidth());
        }
        return Mth.ceil(this.measureFormattedSequence(text));
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth, boolean tail) {
        if (this.shouldParseEffectTags(text)) {
            return tail ? this.effectTailByWidth(text, maxWidth) : this.effectHeadByWidth(text, maxWidth);
        }
        return tail ? this.customSplitter.plainTailByWidth(text, maxWidth, Style.EMPTY) : this.customSplitter.plainHeadByWidth(text, maxWidth, Style.EMPTY);
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth) {
        if (this.shouldParseEffectTags(text)) {
            return this.effectHeadByWidth(text, maxWidth);
        }
        return this.customSplitter.plainHeadByWidth(text, maxWidth, Style.EMPTY);
    }

    @Override
    public FormattedText substrByWidth(FormattedText text, int maxWidth) {
        if (!this.shouldParseEffectTags(text.getString())) {
            return this.customSplitter.headByWidth(text, maxWidth, Style.EMPTY);
        }

        List<String> wrappedLines = this.splitEffectTaggedText(text.getString(), maxWidth);
        return FormattedText.of(wrappedLines.isEmpty() ? "" : wrappedLines.get(0));
    }

    @Override
    public int wordWrapHeight(String text, int maxWidth) {
        if (this.shouldParseEffectTags(text)) {
            return this.lineHeight * this.splitEffectTaggedText(text, maxWidth).size();
        }
        return this.lineHeight * this.customSplitter.splitLines(text, maxWidth, Style.EMPTY).size();
    }

    @Override
    public int wordWrapHeight(FormattedText text, int maxWidth) {
        if (!this.shouldParseEffectTags(text.getString())) {
            return this.lineHeight * this.customSplitter.splitLines(text, maxWidth, Style.EMPTY).size();
        }
        return this.lineHeight * this.splitEffectTaggedText(text.getString(), maxWidth).size();
    }

    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        if (!this.shouldParseEffectTags(text.getString())) {
            return Language.getInstance().getVisualOrder(this.customSplitter.splitLines(text, maxWidth, Style.EMPTY));
        }
        List<String> wrappedLines = this.splitEffectTaggedText(text.getString(), maxWidth);
        List<FormattedCharSequence> visualOrder = new ArrayList<>(wrappedLines.size());
        for (String wrappedLine : wrappedLines) {
            visualOrder.add(Language.getInstance().getVisualOrder(FormattedText.of(wrappedLine)));
        }
        return visualOrder;
    }

    @Override
    public StringSplitter getSplitter() {
        return this.effectAwareSplitter;
    }

    private boolean shouldBypassEffectParsing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ChatScreen)) {
            return false;
        }

        return STACK_WALKER.walk(frames ->
            frames.anyMatch(frame -> frame.getDeclaringClass().getName().equals("net.minecraft.client.gui.components.EditBox"))
        );
    }

    private boolean shouldParseEffectTags(String text) {
        return !this.shouldBypassEffectParsing() && text.indexOf('<') >= 0;
    }

    private boolean shouldParseEffectTags(FormattedCharSequence text) {
        if (this.shouldBypassEffectParsing()) {
            return false;
        }

        final boolean[] foundTagPrefix = {false};
        text.accept((index, style, codepoint) -> {
            if (codepoint == '<') {
                foundTagPrefix[0] = true;
                return false;
            }
            return true;
        });
        return foundTagPrefix[0];
    }

    private List<String> splitEffectTaggedText(String text, int maxWidth) {
        if (text.isEmpty()) {
            return List.of("");
        }

        int effectiveMaxWidth = Math.max(1, maxWidth);
        List<String> wrappedLines = new ArrayList<>();
        int lineStartIndex = 0;
        EffectStyle carryEffect = EffectStyle.defaultStyle();

        while (lineStartIndex < text.length()) {
            WrappedTagLine wrappedLine = this.wrapNextTaggedLine(text, lineStartIndex, effectiveMaxWidth, carryEffect);
            wrappedLines.add(wrappedLine.lineText());
            if (wrappedLine.nextIndex() <= lineStartIndex) {
                break;
            }
            lineStartIndex = wrappedLine.nextIndex();
            carryEffect = wrappedLine.carryEffect();
        }

        if (wrappedLines.isEmpty()) {
            wrappedLines.add("");
        }

        return wrappedLines;
    }

    private String effectHeadByWidth(String text, int maxWidth) {
        List<String> wrappedLines = this.splitEffectTaggedText(text, maxWidth);
        return wrappedLines.isEmpty() ? "" : wrappedLines.get(0);
    }

    private String effectTailByWidth(String text, int maxWidth) {
        if (text.isEmpty()) {
            return "";
        }

        String currentSlice = text;
        int effectiveMaxWidth = Math.max(1, maxWidth);
        while (!currentSlice.isEmpty()) {
            WrappedTagLine wrappedLine = this.wrapNextTaggedLine(currentSlice, 0, effectiveMaxWidth, EffectStyle.defaultStyle());
            if (wrappedLine.nextIndex() >= currentSlice.length()) {
                return currentSlice;
            }
            if (wrappedLine.nextIndex() <= 0) {
                return "";
            }
            currentSlice = currentSlice.substring(wrappedLine.nextIndex());
        }
        return "";
    }

    private WrappedTagLine wrapNextTaggedLine(String text, int startIndex, int maxWidth, EffectStyle carryEffect) {
        int index = startIndex;
        float width = 0.0F;
        int visibleGlyphCount = 0;
        int lastBreakIndex = -1;
        int lastResumeIndex = -1;
        EffectStyle activeEffect = carryEffect;
        EffectStyle carryEffectAtBreak = carryEffect;

        while (index < text.length()) {
            TagMatch tagMatch = tryParseEffectTagAt(text, index);
            if (tagMatch != null) {
                activeEffect = activeEffect.apply(tagMatch.parsedTag(), 0);
                index = tagMatch.nextIndex();
                continue;
            }

            int codepoint = text.codePointAt(index);
            int charCount = Character.charCount(codepoint);
            if (codepoint == '\r' || codepoint == '\n') {
                int nextIndex = index + charCount;
                if (codepoint == '\r' && nextIndex < text.length() && text.charAt(nextIndex) == '\n') {
                    nextIndex++;
                }

                String rawLine = text.substring(startIndex, index);
                return new WrappedTagLine(applyCarryTag(rawLine, carryEffect), nextIndex, activeEffect);
            }
            float advance = this.resolveAdvance(Style.EMPTY, codepoint, activeEffect);

            if (visibleGlyphCount > 0 && width + advance > (float)maxWidth) {
                if (lastBreakIndex >= startIndex) {
                    String rawLine = text.substring(startIndex, lastBreakIndex);
                    return new WrappedTagLine(applyCarryTag(rawLine, carryEffect), lastResumeIndex, carryEffectAtBreak);
                }

                String rawLine = text.substring(startIndex, index);
                return new WrappedTagLine(applyCarryTag(rawLine, carryEffect), index, activeEffect);
            }

            if (Character.isWhitespace(codepoint) && visibleGlyphCount > 0) {
                lastBreakIndex = index;
                lastResumeIndex = index + charCount;
                carryEffectAtBreak = activeEffect;
            }

            width += advance;
            visibleGlyphCount++;
            index += charCount;
        }

        String rawLine = text.substring(startIndex);
        return new WrappedTagLine(applyCarryTag(rawLine, carryEffect), text.length(), activeEffect);
    }

    private int drawInternal(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        dropShadow = dropShadow && this.shadowEnabled;
        color = adjustColor(color);
        Matrix4f shiftedPose = new Matrix4f(pose);
        if (dropShadow) {
            this.renderLiteralText(text, x, y, color, true, pose, bufferSource, displayMode, backgroundColor, packedLight);
            shiftedPose.translate(SHADOW_OFFSET);
        }
        float renderedX = this.renderLiteralText(text, x, y, color, false, shiftedPose, bufferSource, displayMode, backgroundColor, packedLight);
        return (int)renderedX + (dropShadow ? 1 : 0);
    }

    private int drawInternal(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        if (!this.shouldParseEffectTags(text)) {
            return this.drawPlainFormattedInternal(text, x, y, color, dropShadow, pose, bufferSource, displayMode, backgroundColor, packedLight);
        }

        ParsedText parsedText = this.parseFormattedSequence(text, true);
        dropShadow = dropShadow && this.shadowEnabled;
        color = adjustColor(color);
        Matrix4f shiftedPose = new Matrix4f(pose);
        if (dropShadow) {
            this.renderParsedText(parsedText, x, y, color, true, pose, bufferSource, displayMode, backgroundColor, packedLight);
            shiftedPose.translate(SHADOW_OFFSET);
        }
        float renderedX = this.renderParsedText(parsedText, x, y, color, false, shiftedPose, bufferSource, displayMode, backgroundColor, packedLight);
        return (int)renderedX + (dropShadow ? 1 : 0);
    }

    private int drawPlainFormattedInternal(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        dropShadow = dropShadow && this.shadowEnabled;
        color = adjustColor(color);
        Matrix4f shiftedPose = new Matrix4f(pose);
        if (dropShadow) {
            this.renderFormattedSequence(text, x, y, color, true, pose, bufferSource, displayMode, backgroundColor, packedLight);
            shiftedPose.translate(SHADOW_OFFSET);
        }
        float renderedX = this.renderFormattedSequence(text, x, y, color, false, shiftedPose, bufferSource, displayMode, backgroundColor, packedLight);
        return (int)renderedX + (dropShadow ? 1 : 0);
    }

    private float renderLiteralText(
        String text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        RenderOutput output = new RenderOutput(bufferSource, x, y, color, dropShadow, pose, displayMode, packedLight, Map.of());
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (index, style, codepoint) -> output.accept(index, style, codepoint, EffectStyle.defaultStyle()));
        return output.finish(backgroundColor, x);
    }

    private float renderFormattedSequence(
        FormattedCharSequence text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        RenderOutput output = new RenderOutput(bufferSource, x, y, color, dropShadow, pose, displayMode, packedLight, Map.of());
        text.accept((index, style, codepoint) -> output.accept(index, style, codepoint, EffectStyle.defaultStyle()));
        return output.finish(backgroundColor, x);
    }

    private float renderParsedText(
        ParsedText parsedText,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int packedLight
    ) {
        RenderOutput output = new RenderOutput(bufferSource, x, y, color, dropShadow, pose, displayMode, packedLight, parsedText.segmentWidths());
        this.renderParsedText(parsedText, output);
        return output.finish(backgroundColor, x);
    }

    private void renderParsedText(ParsedText parsedText, RenderOutput output) {
        for (ParsedGlyph glyph : parsedText.glyphs()) {
            output.accept(glyph.index(), glyph.style(), glyph.codepoint(), glyph.effectStyle());
        }
    }

    private final class EffectAwareStringSplitter extends StringSplitter {
        private EffectAwareStringSplitter() {
            super((codepoint, style) -> ZhiFontRenderer.this.resolveAdvance(style, codepoint));
        }

        @Override
        public float stringWidth(@Nullable String content) {
            if (content == null) {
                return 0.0F;
            }
            if (ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return ZhiFontRenderer.this.parseLiteralString(content, true).totalWidth();
            }
            return ZhiFontRenderer.this.customSplitter.stringWidth(content);
        }

        @Override
        public float stringWidth(FormattedText content) {
            FlatStyledText flatText = this.flatten(content, Style.EMPTY);
            if (ZhiFontRenderer.this.shouldParseEffectTags(flatText.text())) {
                return this.measure(flatText, EffectStyle.defaultStyle());
            }
            return ZhiFontRenderer.this.customSplitter.stringWidth(content);
        }

        @Override
        public float stringWidth(FormattedCharSequence content) {
            if (ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return ZhiFontRenderer.this.parseFormattedSequence(content, true).totalWidth();
            }
            return ZhiFontRenderer.this.customSplitter.stringWidth(content);
        }

        @Override
        public int plainIndexAtWidth(String content, int maxWidth, Style style) {
            if (ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return this.indexByWidth(content, maxWidth, style);
            }
            return ZhiFontRenderer.this.customSplitter.plainIndexAtWidth(content, maxWidth, style);
        }

        @Override
        public String plainHeadByWidth(String content, int maxWidth, Style style) {
            if (ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return content.substring(0, this.indexByWidth(content, maxWidth, style));
            }
            return ZhiFontRenderer.this.customSplitter.plainHeadByWidth(content, maxWidth, style);
        }

        @Override
        public int formattedIndexByWidth(String content, int maxWidth, Style style) {
            if (ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return this.indexByWidth(content, maxWidth, style);
            }
            return ZhiFontRenderer.this.customSplitter.formattedIndexByWidth(content, maxWidth, style);
        }

        @Override
        public String formattedHeadByWidth(String content, int maxWidth, Style style) {
            if (ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return content.substring(0, this.indexByWidth(content, maxWidth, style));
            }
            return ZhiFontRenderer.this.customSplitter.formattedHeadByWidth(content, maxWidth, style);
        }

        @Override
        public FormattedText headByWidth(FormattedText content, int maxWidth, Style style) {
            FlatStyledText flatText = this.flatten(content, style);
            if (!ZhiFontRenderer.this.shouldParseEffectTags(flatText.text())) {
                return ZhiFontRenderer.this.customSplitter.headByWidth(content, maxWidth, style);
            }

            int endIndex = this.indexByWidth(flatText, maxWidth);
            return this.toFormattedText(flatText.slice(0, endIndex), EffectStyle.defaultStyle());
        }

        @Override
        @Nullable
        public Style componentStyleAtWidth(FormattedText content, int maxWidth) {
            FlatStyledText flatText = this.flatten(content, Style.EMPTY);
            if (!ZhiFontRenderer.this.shouldParseEffectTags(flatText.text())) {
                return ZhiFontRenderer.this.customSplitter.componentStyleAtWidth(content, maxWidth);
            }

            int index = this.indexByWidth(flatText, maxWidth);
            return index >= flatText.length() ? null : flatText.styleAt(index);
        }

        @Override
        public int findLineBreak(String content, int maxWidth, Style style) {
            if (!ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return ZhiFontRenderer.this.customSplitter.findLineBreak(content, maxWidth, style);
            }

            StyledLineBreak lineBreak = this.findLineBreak(content, 0, Math.max(1, maxWidth), style, EffectStyle.defaultStyle());
            return lineBreak.found() ? lineBreak.lineEndIndex() : content.length();
        }

        @Override
        public List<FormattedText> splitLines(String content, int maxWidth, Style style) {
            if (!ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                return ZhiFontRenderer.this.customSplitter.splitLines(content, maxWidth, style);
            }

            List<FormattedText> lines = new ArrayList<>();
            for (String line : ZhiFontRenderer.this.splitEffectTaggedText(content, maxWidth)) {
                lines.add(FormattedText.of(line, style));
            }
            return lines;
        }

        @Override
        public void splitLines(String content, int maxWidth, Style style, boolean withNewLines, StringSplitter.LinePosConsumer linePos) {
            if (!ZhiFontRenderer.this.shouldParseEffectTags(content)) {
                ZhiFontRenderer.this.customSplitter.splitLines(content, maxWidth, style, withNewLines, linePos);
                return;
            }

            int index = 0;
            int effectiveMaxWidth = Math.max(1, maxWidth);
            EffectStyle carryEffect = EffectStyle.defaultStyle();
            while (index < content.length()) {
                StyledLineBreak lineBreak = this.findLineBreak(content, index, effectiveMaxWidth, style, carryEffect);
                if (!lineBreak.found()) {
                    linePos.accept(style, index, content.length());
                    return;
                }

                linePos.accept(style, index, withNewLines ? lineBreak.resumeIndex() : lineBreak.lineEndIndex());
                index = lineBreak.resumeIndex();
                carryEffect = lineBreak.carryEffect();
            }
        }

        @Override
        public List<FormattedText> splitLines(FormattedText content, int maxWidth, Style style) {
            List<FormattedText> lines = new ArrayList<>();
            this.splitLines(content, maxWidth, style, (line, continued) -> lines.add(line));
            return lines;
        }

        @Override
        public void splitLines(FormattedText content, int maxWidth, Style style, BiConsumer<FormattedText, Boolean> splitifier) {
            FlatStyledText remaining = this.flatten(content, style);
            if (!ZhiFontRenderer.this.shouldParseEffectTags(remaining.text())) {
                ZhiFontRenderer.this.customSplitter.splitLines(content, maxWidth, style, splitifier);
                return;
            }

            int effectiveMaxWidth = Math.max(1, maxWidth);
            boolean continuedFromWrap = false;
            boolean endedWithNewline = false;
            EffectStyle carryEffect = EffectStyle.defaultStyle();

            while (!remaining.isEmpty()) {
                StyledLineBreak lineBreak = this.findLineBreak(remaining, effectiveMaxWidth, carryEffect);
                if (!lineBreak.found()) {
                    splitifier.accept(this.toFormattedText(remaining.parts(), carryEffect), continuedFromWrap);
                    return;
                }

                SplitStyledText splitText = remaining.splitAt(lineBreak.lineEndIndex(), lineBreak.resumeIndex());
                splitifier.accept(this.toFormattedText(splitText.head(), carryEffect), continuedFromWrap);
                remaining = splitText.tail();
                continuedFromWrap = !lineBreak.newline();
                endedWithNewline = lineBreak.newline();
                carryEffect = lineBreak.carryEffect();
            }

            if (endedWithNewline) {
                splitifier.accept(FormattedText.EMPTY, false);
            }
        }

        private FlatStyledText flatten(FormattedText content, Style style) {
            List<StyledTextPart> parts = new ArrayList<>();
            content.visit((partStyle, partText) -> {
                if (!partText.isEmpty()) {
                    parts.add(new StyledTextPart(partText, partStyle));
                }
                return Optional.empty();
            }, style);
            return new FlatStyledText(parts);
        }

        private float measure(FlatStyledText text, EffectStyle initialEffect) {
            float width = 0.0F;
            int index = 0;
            EffectStyle activeEffect = initialEffect;
            String rawText = text.text();
            while (index < rawText.length()) {
                TagMatch tagMatch = tryParseEffectTagAt(rawText, index);
                if (tagMatch != null) {
                    activeEffect = activeEffect.apply(tagMatch.parsedTag(), 0);
                    index = tagMatch.nextIndex();
                    continue;
                }

                int codepoint = rawText.codePointAt(index);
                width += ZhiFontRenderer.this.resolveAdvance(text.styleAt(index), codepoint, activeEffect);
                index += Character.charCount(codepoint);
            }
            return width;
        }

        private int indexByWidth(String text, int maxWidth, Style style) {
            return this.indexByWidth(new FlatStyledText(List.of(new StyledTextPart(text, style))), maxWidth);
        }

        private int indexByWidth(FlatStyledText text, int maxWidth) {
            float width = 0.0F;
            int index = 0;
            EffectStyle activeEffect = EffectStyle.defaultStyle();
            String rawText = text.text();
            while (index < rawText.length()) {
                TagMatch tagMatch = tryParseEffectTagAt(rawText, index);
                if (tagMatch != null) {
                    activeEffect = activeEffect.apply(tagMatch.parsedTag(), 0);
                    index = tagMatch.nextIndex();
                    continue;
                }

                int codepoint = rawText.codePointAt(index);
                float advance = ZhiFontRenderer.this.resolveAdvance(text.styleAt(index), codepoint, activeEffect);
                if (width + advance > (float)maxWidth) {
                    return index;
                }

                width += advance;
                index += Character.charCount(codepoint);
            }
            return rawText.length();
        }

        private StyledLineBreak findLineBreak(FlatStyledText text, int maxWidth, EffectStyle carryEffect) {
            return this.findLineBreak(text.text(), 0, maxWidth, text::styleAt, carryEffect);
        }

        private StyledLineBreak findLineBreak(String text, int startIndex, int maxWidth, Style style, EffectStyle carryEffect) {
            return this.findLineBreak(text, startIndex, maxWidth, ignored -> style, carryEffect);
        }

        private StyledLineBreak findLineBreak(String text, int startIndex, int maxWidth, StyleResolver styleResolver, EffectStyle carryEffect) {
            int index = startIndex;
            float width = 0.0F;
            int visibleGlyphCount = 0;
            int lastBreakIndex = -1;
            int lastResumeIndex = -1;
            EffectStyle activeEffect = carryEffect;
            EffectStyle carryEffectAtBreak = carryEffect;

            while (index < text.length()) {
                TagMatch tagMatch = tryParseEffectTagAt(text, index);
                if (tagMatch != null) {
                    activeEffect = activeEffect.apply(tagMatch.parsedTag(), 0);
                    index = tagMatch.nextIndex();
                    continue;
                }

                int codepoint = text.codePointAt(index);
                int charCount = Character.charCount(codepoint);
                if (codepoint == '\r' || codepoint == '\n') {
                    int nextIndex = index + charCount;
                    if (codepoint == '\r' && nextIndex < text.length() && text.charAt(nextIndex) == '\n') {
                        nextIndex++;
                    }
                    return new StyledLineBreak(true, index, nextIndex, true, activeEffect);
                }

                float advance = ZhiFontRenderer.this.resolveAdvance(styleResolver.styleAt(index), codepoint, activeEffect);
                if (visibleGlyphCount > 0 && width + advance > (float)maxWidth) {
                    if (lastBreakIndex >= startIndex) {
                        return new StyledLineBreak(true, lastBreakIndex, lastResumeIndex, false, carryEffectAtBreak);
                    }
                    return new StyledLineBreak(true, index, index, false, activeEffect);
                }

                if (Character.isWhitespace(codepoint) && visibleGlyphCount > 0) {
                    lastBreakIndex = index;
                    lastResumeIndex = index + charCount;
                    carryEffectAtBreak = activeEffect;
                }

                width += advance;
                visibleGlyphCount++;
                index += charCount;
            }

            return new StyledLineBreak(false, text.length(), text.length(), false, activeEffect);
        }

        private FormattedText toFormattedText(List<StyledTextPart> parts, EffectStyle carryEffect) {
            if (parts.isEmpty()) {
                return FormattedText.EMPTY;
            }

            List<FormattedText> formattedParts = new ArrayList<>();
            if (!carryEffect.isDefault()) {
                formattedParts.add(new StyledTextPart(carryEffect.toTagPrefix(), Style.EMPTY));
            }
            formattedParts.addAll(parts);
            return new CompositeFormattedText(formattedParts);
        }
    }

    private ParsedText parseFormattedSequence(FormattedCharSequence text, boolean enableEffects) {
        ParsedTextBuilder builder = new ParsedTextBuilder(enableEffects);
        text.accept(builder);
        return builder.build();
    }

    private ParsedText parseLiteralString(String text, boolean enableEffects) {
        ParsedTextBuilder builder = new ParsedTextBuilder(enableEffects);
        StringDecomposer.iterateFormatted(text, Style.EMPTY, builder);
        return builder.build();
    }

    private float measureFormattedSequence(FormattedCharSequence text) {
        final float[] totalWidth = {0.0F};
        text.accept((index, style, codepoint) -> {
            totalWidth[0] += this.resolveAdvance(style, codepoint);
            return true;
        });
        return totalWidth[0];
    }

    private float resolveAdvance(Style style, int codepoint) {
        return this.resolveAdvance(style, codepoint, EffectStyle.defaultStyle());
    }

    private float resolveAdvance(Style style, int codepoint, EffectStyle effectStyle) {
        GlyphInfo glyphInfo = this.resolveGlyphInfo(style, codepoint, effectStyle);
        return glyphInfo.getAdvance(this.shouldApplyBold(style, codepoint, effectStyle));
    }

    private boolean shouldApplyBold(Style style, int codepoint) {
        return this.shouldApplyBold(style, codepoint, EffectStyle.defaultStyle());
    }

    private boolean shouldApplyBold(Style style, int codepoint, EffectStyle effectStyle) {
        return style.isBold() && !this.usesCustomGlyph(style, codepoint, effectStyle);
    }

    private boolean usesCustomGlyph(Style style, int codepoint) {
        return this.usesCustomGlyph(style, codepoint, EffectStyle.defaultStyle());
    }

    private boolean usesCustomGlyph(Style style, int codepoint, EffectStyle effectStyle) {
        return !effectStyle.vanilla() && this.shouldUseCustomFont(style.getFont()) && this.customAtlas.findGlyphInfo(codepoint) != null;
    }

    private GlyphInfo resolveGlyphInfo(Style style, int codepoint) {
        return this.resolveGlyphInfo(style, codepoint, EffectStyle.defaultStyle());
    }

    private GlyphInfo resolveGlyphInfo(Style style, int codepoint, EffectStyle effectStyle) {
        if (!effectStyle.vanilla() && this.shouldUseCustomFont(style.getFont())) {
            GlyphInfo customGlyphInfo = this.customAtlas.findGlyphInfo(codepoint);
            if (customGlyphInfo != null) {
                return customGlyphInfo;
            }
        }
        return this.getVanillaFontSet(style.getFont()).getGlyphInfo(codepoint, this.filterFishyGlyphs);
    }

    private BakedGlyph resolveGlyph(Style style, int codepoint, GlyphInfo glyphInfo) {
        return this.resolveGlyph(style, codepoint, glyphInfo, EffectStyle.defaultStyle());
    }

    private BakedGlyph resolveGlyph(Style style, int codepoint, GlyphInfo glyphInfo, EffectStyle effectStyle) {
        if (!effectStyle.vanilla() && this.shouldUseCustomFont(style.getFont())) {
            BakedGlyph customGlyph = this.customAtlas.findGlyph(codepoint);
            if (customGlyph != null) {
                return customGlyph;
            }
        }
        FontSet fontSet = this.getVanillaFontSet(style.getFont());
        if (style.isObfuscated() && codepoint != 32) {
            return fontSet.getRandomGlyph(glyphInfo);
        }
        return fontSet.getGlyph(codepoint);
    }

    private BakedGlyph whiteGlyph() {
        return this.getVanillaFontSet(Style.DEFAULT_FONT).whiteGlyph();
    }

    private FontSet getVanillaFontSet(ResourceLocation fontId) {
        return this.vanillaFontManagerAccessor.zhifontgo$invokeGetFontSetRaw(fontId);
    }

    private boolean shouldUseCustomFont(ResourceLocation fontId) {
        return DEFAULT_FONT_ID.equals(fontId) || UNIFORM_FONT_ID.equals(fontId);
    }

    private static void renderChar(
        BakedGlyph glyph,
        boolean bold,
        boolean italic,
        float boldOffset,
        float x,
        float y,
        Matrix4f pose,
        VertexConsumer vertexConsumer,
        float red,
        float green,
        float blue,
        float alpha,
        int packedLight
    ) {
        glyph.render(italic, x, y, pose, vertexConsumer, red, green, blue, alpha, packedLight);
        if (bold) {
            glyph.render(italic, x + boldOffset, y, pose, vertexConsumer, red, green, blue, alpha, packedLight);
        }
    }

    private static int adjustColor(int color) {
        return (color & -67108864) == 0 ? color | 0xFF000000 : color;
    }

    private static String stripEffectTags(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int openIndex = text.indexOf('<', index);
            if (openIndex < 0) {
                builder.append(text, index, text.length());
                break;
            }

            builder.append(text, index, openIndex);
            int closeIndex = text.indexOf('>', openIndex);
            if (closeIndex < 0) {
                builder.append(text.substring(openIndex));
                break;
            }

            String candidate = text.substring(openIndex, closeIndex + 1);
            if (!parseEffectTag(candidate).valid()) {
                builder.append(candidate);
            }
            index = closeIndex + 1;
        }
        return builder.toString();
    }

    private static ParsedTag parseEffectTag(String tagText) {
        if (!tagText.startsWith("<") || !tagText.endsWith(">")) {
            return ParsedTag.invalid();
        }

        String innerText = tagText.substring(1, tagText.length() - 1).trim();
        String innerTextLower = innerText.toLowerCase(Locale.ROOT);
        if (innerTextLower.equals(EFFECT_TAG_NAMESPACE)) {
            return ParsedTag.resetTag();
        }
        if (!innerTextLower.startsWith(EFFECT_TAG_NAMESPACE + ":")) {
            return ParsedTag.invalid();
        }

        String payload = innerText.substring(EFFECT_TAG_NAMESPACE.length() + 1).trim();
        String payloadLower = payload.toLowerCase(Locale.ROOT);
        if (payloadLower.equals("shine")) {
            return ParsedTag.shine();
        }
        if (payloadLower.equals("wave")) {
            return ParsedTag.wave();
        }
        if (payloadLower.equals("shake")) {
            return ParsedTag.shake();
        }
        if (payloadLower.equals("rainbow")) {
            return ParsedTag.rainbow();
        }
        if (payloadLower.equals("pulse")) {
            return ParsedTag.pulse();
        }
        if (payloadLower.equals("vanilla")) {
            return ParsedTag.vanilla();
        }

        int dashIndex = payload.indexOf('-');
        if (dashIndex >= 0) {
            String firstColor = payload.substring(0, dashIndex).trim();
            String secondColor = payload.substring(dashIndex + 1).trim();
            Integer startColor = parseHexColor(firstColor);
            Integer endColor = parseHexColor(secondColor);
            if (startColor != null && endColor != null) {
                return ParsedTag.gradient(startColor, endColor);
            }
            return ParsedTag.invalid();
        }

        Integer solidColor = parseHexColor(payload);
        return solidColor != null ? ParsedTag.solid(solidColor) : ParsedTag.invalid();
    }

    @Nullable
    private static TagMatch tryParseEffectTagAt(String text, int startIndex) {
        if (startIndex < 0 || startIndex >= text.length() || text.charAt(startIndex) != '<') {
            return null;
        }

        int closeIndex = text.indexOf('>', startIndex);
        if (closeIndex < 0) {
            return null;
        }

        String candidate = text.substring(startIndex, closeIndex + 1);
        ParsedTag parsedTag = parseEffectTag(candidate);
        return parsedTag.valid() ? new TagMatch(candidate, closeIndex + 1, parsedTag) : null;
    }

    private static String applyCarryTag(String rawLine, EffectStyle carryEffect) {
        if (rawLine.isEmpty() || carryEffect.isDefault()) {
            return rawLine;
        }
        return carryEffect.toTagPrefix() + rawLine;
    }

    @Nullable
    private static Integer parseHexColor(String text) {
        if (!text.startsWith("#") || text.length() != 7) {
            return null;
        }
        try {
            return Integer.parseInt(text.substring(1), 16);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String toHexColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private final class RenderOutput {
        private final MultiBufferSource bufferSource;
        private final boolean dropShadow;
        private final float dimFactor;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;
        private final Matrix4f pose;
        private final Font.DisplayMode mode;
        private final int packedLight;
        private final Map<Integer, Float> segmentWidths;
        private float x;
        private float y;
        private int activeSegmentId = Integer.MIN_VALUE;
        private float segmentStartX;
        @Nullable
        private List<BakedGlyph.Effect> effects;

        private RenderOutput(
            MultiBufferSource bufferSource,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f pose,
            Font.DisplayMode mode,
            int packedLight,
            Map<Integer, Float> segmentWidths
        ) {
            this.bufferSource = bufferSource;
            this.x = x;
            this.y = y;
            this.dropShadow = dropShadow;
            this.dimFactor = dropShadow ? 0.25F : 1.0F;
            this.red = (float)(color >> 16 & 0xFF) / 255.0F * this.dimFactor;
            this.green = (float)(color >> 8 & 0xFF) / 255.0F * this.dimFactor;
            this.blue = (float)(color & 0xFF) / 255.0F * this.dimFactor;
            this.alpha = (float)(color >> 24 & 0xFF) / 255.0F;
            this.pose = pose;
            this.mode = mode;
            this.packedLight = packedLight;
            this.segmentWidths = segmentWidths;
        }

        private void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        private boolean accept(int index, Style style, int codepoint, EffectStyle effectStyle) {
            GlyphInfo glyphInfo = ZhiFontRenderer.this.resolveGlyphInfo(style, codepoint, effectStyle);
            BakedGlyph bakedGlyph = ZhiFontRenderer.this.resolveGlyph(style, codepoint, glyphInfo, effectStyle);
            boolean bold = ZhiFontRenderer.this.shouldApplyBold(style, codepoint, effectStyle);
            if (effectStyle.segmentId() != this.activeSegmentId) {
                this.activeSegmentId = effectStyle.segmentId();
                this.segmentStartX = this.x;
            }

            float[] rgb = this.resolveBaseRgb(style, effectStyle);
            float redValue = rgb[0];
            float greenValue = rgb[1];
            float blueValue = rgb[2];
            float alphaValue = this.resolveAlpha(effectStyle);
            float effectOffsetX = this.resolveEffectOffsetX(index, effectStyle);
            float effectOffsetY = this.resolveEffectOffsetY(index, effectStyle);

            if (!(bakedGlyph instanceof EmptyGlyph)) {
                float boldOffset = bold ? glyphInfo.getBoldOffset() : 0.0F;
                float shadowOffset = this.dropShadow ? glyphInfo.getShadowOffset() : 0.0F;
                VertexConsumer vertexConsumer = this.bufferSource.getBuffer(bakedGlyph.renderType(this.mode));
                ZhiFontRenderer.renderChar(
                    bakedGlyph,
                    bold,
                    style.isItalic(),
                    boldOffset,
                    this.x + effectOffsetX + shadowOffset,
                    this.y + effectOffsetY + shadowOffset,
                    this.pose,
                    vertexConsumer,
                    redValue,
                    greenValue,
                    blueValue,
                    alphaValue,
                    this.packedLight
                );

                if (effectStyle.shine() && !this.dropShadow) {
                    float advance = glyphInfo.getAdvance(bold);
                    float shimmerAlpha = this.computeShimmerAlpha(advance);
                    if (shimmerAlpha > 0.0F) {
                        ZhiFontRenderer.renderChar(
                            bakedGlyph,
                            bold,
                            style.isItalic(),
                            boldOffset,
                            this.x + effectOffsetX + shadowOffset,
                            this.y + effectOffsetY + shadowOffset,
                            this.pose,
                            vertexConsumer,
                            1.0F,
                            1.0F,
                            1.0F,
                            shimmerAlpha,
                            this.packedLight
                        );
                    }
                }
            }

            float advance = glyphInfo.getAdvance(bold);
            float decorationOffset = this.dropShadow ? 1.0F : 0.0F;
            if (style.isStrikethrough()) {
                this.addEffect(new BakedGlyph.Effect(
                    this.x + effectOffsetX + decorationOffset - 1.0F,
                    this.y + effectOffsetY + decorationOffset + 4.5F,
                    this.x + effectOffsetX + decorationOffset + advance,
                    this.y + effectOffsetY + decorationOffset + 3.5F,
                    0.01F,
                    redValue,
                    greenValue,
                    blueValue,
                    alphaValue
                ));
            }
            if (style.isUnderlined()) {
                this.addEffect(new BakedGlyph.Effect(
                    this.x + effectOffsetX + decorationOffset - 1.0F,
                    this.y + effectOffsetY + decorationOffset + 9.0F,
                    this.x + effectOffsetX + decorationOffset + advance,
                    this.y + effectOffsetY + decorationOffset + 8.0F,
                    0.01F,
                    redValue,
                    greenValue,
                    blueValue,
                    alphaValue
                ));
            }

            this.x += advance;
            return true;
        }

        private float[] resolveBaseRgb(Style style, EffectStyle effectStyle) {
            float[] baseRgb;
            if (effectStyle.colorMode() == EffectColorMode.SOLID) {
                baseRgb = rgbFromColor(effectStyle.primaryColor(), this.dimFactor);
            } else if (effectStyle.colorMode() == EffectColorMode.GRADIENT) {
                float segmentWidth = Math.max(1.0F, this.segmentWidths.getOrDefault(effectStyle.segmentId(), 1.0F));
                float localCenter = this.x - this.segmentStartX;
                float t = Mth.clamp(localCenter / segmentWidth, 0.0F, 1.0F);
                baseRgb = lerpRgb(effectStyle.primaryColor(), effectStyle.secondaryColor(), t, this.dimFactor);
            } else {
                TextColor color = style.getColor();
                if (color != null) {
                    baseRgb = rgbFromColor(color.getValue(), this.dimFactor);
                } else {
                    baseRgb = new float[]{this.red, this.green, this.blue};
                }
            }

            if (effectStyle.rainbow()) {
                float[] rainbowRgb = this.resolveRainbowRgb(effectStyle);
                if (effectStyle.colorMode() == EffectColorMode.NONE) {
                    baseRgb = rainbowRgb;
                } else {
                    baseRgb = new float[]{
                        Mth.lerp(0.65F, baseRgb[0], rainbowRgb[0]),
                        Mth.lerp(0.65F, baseRgb[1], rainbowRgb[1]),
                        Mth.lerp(0.65F, baseRgb[2], rainbowRgb[2])
                    };
                }
            }

            if (effectStyle.pulse()) {
                float pulse = this.computePulseStrength(effectStyle);
                baseRgb = new float[]{
                    Mth.clamp(baseRgb[0] * pulse, 0.0F, 1.0F),
                    Mth.clamp(baseRgb[1] * pulse, 0.0F, 1.0F),
                    Mth.clamp(baseRgb[2] * pulse, 0.0F, 1.0F)
                };
            }

            return baseRgb;
        }

        private float computeShimmerAlpha(float advance) {
            float localCenterX = this.x - this.segmentStartX + advance * 0.5F;
            float cycle = (float)(Util.getMillis() % 1600L) / 1600.0F;
            float sweepCenter = cycle * 220.0F - 32.0F;
            float distance = Math.abs(localCenterX - sweepCenter);
            float strength = Math.max(0.0F, 1.0F - distance / 26.0F);
            float boost = ZhiFontRenderer.this.worldIrisCompatible ? 1.35F : 0.9F;
            return Mth.clamp(this.alpha * boost * strength * strength, 0.0F, 1.0F);
        }

        private float[] resolveRainbowRgb(EffectStyle effectStyle) {
            float time = (float)(Util.getMillis() % 6000L) / 6000.0F;
            float segmentWidth = Math.max(1.0F, this.segmentWidths.getOrDefault(effectStyle.segmentId(), 1.0F));
            float localCenter = this.x - this.segmentStartX;
            float spatial = localCenter / Math.max(36.0F, segmentWidth);
            return hsvToRgb(time + spatial, 0.75F, 1.0F, this.dimFactor);
        }

        private float computePulseStrength(EffectStyle effectStyle) {
            float time = (float)(Util.getMillis() % 2200L) / 2200.0F;
            float phase = time * Mth.TWO_PI + effectStyle.segmentId() * 0.35F;
            if (ZhiFontRenderer.this.worldIrisCompatible) {
                return 0.95F + (Mth.sin(phase) * 0.24F + 0.24F);
            }
            return 0.82F + (Mth.sin(phase) * 0.18F + 0.18F);
        }

        private float resolveAlpha(EffectStyle effectStyle) {
            if (!effectStyle.pulse()) {
                return this.alpha;
            }

            float time = (float)(Util.getMillis() % 1800L) / 1800.0F;
            float phase = time * Mth.TWO_PI + effectStyle.segmentId() * 0.41F;
            float factor = ZhiFontRenderer.this.worldIrisCompatible
                ? 0.9F + (Mth.sin(phase) * 0.15F + 0.15F)
                : 0.78F + (Mth.sin(phase) * 0.12F + 0.12F);
            return Mth.clamp(this.alpha * factor, 0.0F, 1.0F);
        }

        private float resolveEffectOffsetX(int index, EffectStyle effectStyle) {
            float offset = 0.0F;
            if (effectStyle.shake()) {
                offset += this.computeShakeOffset(index, effectStyle.segmentId(), 0);
            }
            return offset;
        }

        private float resolveEffectOffsetY(int index, EffectStyle effectStyle) {
            float offset = 0.0F;
            if (effectStyle.wave()) {
                offset += this.computeWaveOffset(index, effectStyle.segmentId());
            }
            if (effectStyle.shake()) {
                offset += this.computeShakeOffset(index, effectStyle.segmentId(), 1);
            }
            return offset;
        }

        private float computeWaveOffset(int index, int segmentId) {
            float time = (float)(Util.getMillis() % 2400L) / 2400.0F;
            float phase = time * Mth.TWO_PI * 1.7F + index * 0.55F + segmentId * 0.2F;
            return Mth.sin(phase) * 1.35F;
        }

        private float computeShakeOffset(int index, int segmentId, int axis) {
            long tick = Util.getMillis() / 45L;
            long seed = tick * 1315423911L + (long)index * 73428767L + (long)segmentId * 912931L + axis * 17L;
            seed ^= seed << 13;
            seed ^= seed >>> 7;
            seed ^= seed << 17;
            return ((seed & 1023L) / 1023.0F - 0.5F) * 1.15F;
        }

        private float finish(int backgroundColor, float startX) {
            if (backgroundColor != 0) {
                float alpha = (float)(backgroundColor >> 24 & 0xFF) / 255.0F;
                float red = (float)(backgroundColor >> 16 & 0xFF) / 255.0F;
                float green = (float)(backgroundColor >> 8 & 0xFF) / 255.0F;
                float blue = (float)(backgroundColor & 0xFF) / 255.0F;
                this.addEffect(new BakedGlyph.Effect(startX - 1.0F, this.y + 9.0F, this.x + 1.0F, this.y - 1.0F, 0.01F, red, green, blue, alpha));
            }

            if (this.effects != null) {
                BakedGlyph whiteGlyph = ZhiFontRenderer.this.whiteGlyph();
                VertexConsumer vertexConsumer = this.bufferSource.getBuffer(whiteGlyph.renderType(this.mode));
                for (BakedGlyph.Effect effect : this.effects) {
                    whiteGlyph.renderEffect(effect, this.pose, vertexConsumer, this.packedLight);
                }
            }

            return this.x;
        }

        private void addEffect(BakedGlyph.Effect effect) {
            if (this.effects == null) {
                this.effects = Lists.newArrayList();
            }
            this.effects.add(effect);
        }
    }

    private final class ParsedTextBuilder implements FormattedCharSink {
        private final boolean enableEffects;
        private final List<ParsedGlyph> glyphs = new ArrayList<>();
        private final List<TagToken> buffer = new ArrayList<>();
        private EffectStyle currentEffect = EffectStyle.defaultStyle();
        private int nextSegmentId = 1;

        private ParsedTextBuilder(boolean enableEffects) {
            this.enableEffects = enableEffects;
        }

        @Override
        public boolean accept(int index, Style style, int codepoint) {
            if (!this.enableEffects) {
                this.glyphs.add(new ParsedGlyph(index, style, codepoint, this.currentEffect));
                return true;
            }
            if (this.buffer.isEmpty() && codepoint != '<') {
                this.glyphs.add(new ParsedGlyph(index, style, codepoint, this.currentEffect));
                return true;
            }

            this.buffer.add(new TagToken(index, style, codepoint));
            return this.drain(false);
        }

        private ParsedText build() {
            this.drain(true);
            Map<Integer, Float> segmentWidths = new HashMap<>();
            float totalWidth = 0.0F;
            for (ParsedGlyph glyph : this.glyphs) {
                float advance = ZhiFontRenderer.this.resolveAdvance(glyph.style(), glyph.codepoint(), glyph.effectStyle());
                totalWidth += advance;
                segmentWidths.merge(glyph.effectStyle().segmentId(), advance, Float::sum);
            }
            return new ParsedText(this.glyphs, segmentWidths, totalWidth);
        }

        private boolean drain(boolean flushAll) {
            while (!this.buffer.isEmpty()) {
                String bufferText = this.bufferText();
                ParsedTag parsedTag = parseEffectTag(bufferText);
                if (parsedTag.valid()) {
                    this.buffer.clear();
                    this.currentEffect = this.currentEffect.apply(parsedTag, this.nextSegmentId++);
                    continue;
                }

                if (!flushAll && this.couldStillBeTag(bufferText)) {
                    return true;
                }

                TagToken token = this.buffer.remove(0);
                this.glyphs.add(new ParsedGlyph(token.index(), token.style(), token.codepoint(), this.currentEffect));
            }
            return true;
        }

        private boolean couldStillBeTag(String bufferText) {
            if (!bufferText.startsWith("<") || bufferText.indexOf('>') >= 0) {
                return false;
            }

            String lower = bufferText.toLowerCase(Locale.ROOT);
            if (EFFECT_TAG_OPEN.startsWith(lower)) {
                return true;
            }
            return lower.startsWith(EFFECT_TAG_OPEN);
        }

        private String bufferText() {
            StringBuilder builder = new StringBuilder(this.buffer.size());
            for (TagToken token : this.buffer) {
                builder.appendCodePoint(token.codepoint());
            }
            return builder.toString();
        }
    }

    private static float[] rgbFromColor(int color, float dimFactor) {
        return new float[]{
            (float)(color >> 16 & 0xFF) / 255.0F * dimFactor,
            (float)(color >> 8 & 0xFF) / 255.0F * dimFactor,
            (float)(color & 0xFF) / 255.0F * dimFactor
        };
    }

    private static float[] lerpRgb(int startColor, int endColor, float t, float dimFactor) {
        float[] start = rgbFromColor(startColor, dimFactor);
        float[] end = rgbFromColor(endColor, dimFactor);
        return new float[]{
            Mth.lerp(t, start[0], end[0]),
            Mth.lerp(t, start[1], end[1]),
            Mth.lerp(t, start[2], end[2])
        };
    }

    private static float[] hsvToRgb(float hue, float saturation, float value, float dimFactor) {
        float wrappedHue = hue - Mth.floor(hue);
        int color = Mth.hsvToRgb(wrappedHue, Mth.clamp(saturation, 0.0F, 1.0F), Mth.clamp(value, 0.0F, 1.0F));
        return rgbFromColor(color, dimFactor);
    }

    @FunctionalInterface
    private interface StyleResolver {
        Style styleAt(int index);
    }

    private static final class FlatStyledText {
        private final List<StyledTextPart> parts;
        private final String text;

        private FlatStyledText(List<StyledTextPart> parts) {
            this.parts = List.copyOf(parts);
            StringBuilder builder = new StringBuilder();
            for (StyledTextPart part : parts) {
                builder.append(part.contents());
            }
            this.text = builder.toString();
        }

        private List<StyledTextPart> parts() {
            return this.parts;
        }

        private String text() {
            return this.text;
        }

        private int length() {
            return this.text.length();
        }

        private boolean isEmpty() {
            return this.text.isEmpty();
        }

        private Style styleAt(int index) {
            if (this.parts.isEmpty()) {
                return Style.EMPTY;
            }

            int cursor = 0;
            for (StyledTextPart part : this.parts) {
                int nextCursor = cursor + part.contents().length();
                if (index < nextCursor) {
                    return part.style();
                }
                cursor = nextCursor;
            }
            return this.parts.get(this.parts.size() - 1).style();
        }

        private List<StyledTextPart> slice(int beginIndex, int endIndex) {
            int begin = Math.max(0, Math.min(beginIndex, this.text.length()));
            int end = Math.max(begin, Math.min(endIndex, this.text.length()));
            List<StyledTextPart> slicedParts = new ArrayList<>();
            int cursor = 0;

            for (StyledTextPart part : this.parts) {
                String contents = part.contents();
                int nextCursor = cursor + contents.length();
                int localBegin = Math.max(begin, cursor) - cursor;
                int localEnd = Math.min(end, nextCursor) - cursor;
                if (localBegin < localEnd) {
                    slicedParts.add(new StyledTextPart(contents.substring(localBegin, localEnd), part.style()));
                }
                cursor = nextCursor;
                if (cursor >= end) {
                    break;
                }
            }

            return slicedParts;
        }

        private SplitStyledText splitAt(int lineEndIndex, int resumeIndex) {
            int lineEnd = Math.max(0, Math.min(lineEndIndex, this.text.length()));
            int resume = Math.max(lineEnd, Math.min(resumeIndex, this.text.length()));
            return new SplitStyledText(this.slice(0, lineEnd), new FlatStyledText(this.slice(resume, this.text.length())));
        }
    }

    private record SplitStyledText(List<StyledTextPart> head, FlatStyledText tail) {
    }

    private record StyledLineBreak(boolean found, int lineEndIndex, int resumeIndex, boolean newline, EffectStyle carryEffect) {
    }

    private record StyledTextPart(String contents, Style style) implements FormattedText {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> acceptor) {
            return acceptor.accept(this.contents);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> acceptor, Style style) {
            return acceptor.accept(this.style.applyTo(style), this.contents);
        }
    }

    private record CompositeFormattedText(List<FormattedText> parts) implements FormattedText {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> acceptor) {
            for (FormattedText part : this.parts) {
                Optional<T> result = part.visit(acceptor);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> acceptor, Style style) {
            for (FormattedText part : this.parts) {
                Optional<T> result = part.visit(acceptor, style);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        }
    }

    private record ParsedText(List<ParsedGlyph> glyphs, Map<Integer, Float> segmentWidths, float totalWidth) {
    }

    private record ParsedGlyph(int index, Style style, int codepoint, EffectStyle effectStyle) {
    }

    private record TagToken(int index, Style style, int codepoint) {
    }

    private record TagMatch(String rawText, int nextIndex, ParsedTag parsedTag) {
    }

    private record WrappedTagLine(String lineText, int nextIndex, EffectStyle carryEffect) {
    }

    private record EffectStyle(
        boolean shine,
        boolean wave,
        boolean shake,
        boolean rainbow,
        boolean pulse,
        EffectColorMode colorMode,
        int primaryColor,
        int secondaryColor,
        boolean vanilla,
        int segmentId
    ) {
        private static EffectStyle defaultStyle() {
            return defaultStyle(0);
        }

        private static EffectStyle defaultStyle(int segmentId) {
            return new EffectStyle(false, false, false, false, false, EffectColorMode.NONE, 0, 0, false, segmentId);
        }

        private static EffectStyle shineStyle(int segmentId) {
            return new EffectStyle(true, false, false, false, false, EffectColorMode.NONE, 0, 0, false, segmentId);
        }

        private static EffectStyle waveStyle(int segmentId) {
            return new EffectStyle(false, true, false, false, false, EffectColorMode.NONE, 0, 0, false, segmentId);
        }

        private static EffectStyle shakeStyle(int segmentId) {
            return new EffectStyle(false, false, true, false, false, EffectColorMode.NONE, 0, 0, false, segmentId);
        }

        private static EffectStyle rainbowStyle(int segmentId) {
            return new EffectStyle(false, false, false, true, false, EffectColorMode.NONE, 0, 0, false, segmentId);
        }

        private static EffectStyle pulseStyle(int segmentId) {
            return new EffectStyle(false, false, false, false, true, EffectColorMode.NONE, 0, 0, false, segmentId);
        }

        private static EffectStyle solidStyle(int color, int segmentId) {
            return new EffectStyle(false, false, false, false, false, EffectColorMode.SOLID, color, color, false, segmentId);
        }

        private static EffectStyle gradientStyle(int startColor, int endColor, int segmentId) {
            return new EffectStyle(false, false, false, false, false, EffectColorMode.GRADIENT, startColor, endColor, false, segmentId);
        }

        private static EffectStyle vanillaStyle(int segmentId) {
            return new EffectStyle(false, false, false, false, false, EffectColorMode.NONE, 0, 0, true, segmentId);
        }

        private boolean isDefault() {
            return !this.vanilla && !this.shine && !this.wave && !this.shake && !this.rainbow && !this.pulse && this.colorMode == EffectColorMode.NONE;
        }

        private EffectStyle outlineStyle() {
            return this.vanilla ? vanillaStyle(this.segmentId) : defaultStyle(this.segmentId);
        }

        private EffectStyle apply(ParsedTag parsedTag, int segmentId) {
            if (parsedTag.resetsEffect()) {
                return defaultStyle(segmentId);
            }
            if (parsedTag.action() == TagAction.VANILLA) {
                return vanillaStyle(segmentId);
            }
            if (this.vanilla) {
                return vanillaStyle(segmentId);
            }

            return switch (parsedTag.action()) {
                case SHINE -> new EffectStyle(true, this.wave, this.shake, this.rainbow, this.pulse, this.colorMode, this.primaryColor, this.secondaryColor, false, segmentId);
                case WAVE -> new EffectStyle(this.shine, true, this.shake, this.rainbow, this.pulse, this.colorMode, this.primaryColor, this.secondaryColor, false, segmentId);
                case SHAKE -> new EffectStyle(this.shine, this.wave, true, this.rainbow, this.pulse, this.colorMode, this.primaryColor, this.secondaryColor, false, segmentId);
                case RAINBOW -> new EffectStyle(this.shine, this.wave, this.shake, true, this.pulse, this.colorMode, this.primaryColor, this.secondaryColor, false, segmentId);
                case PULSE -> new EffectStyle(this.shine, this.wave, this.shake, this.rainbow, true, this.colorMode, this.primaryColor, this.secondaryColor, false, segmentId);
                case SOLID -> new EffectStyle(this.shine, this.wave, this.shake, this.rainbow, this.pulse, EffectColorMode.SOLID, parsedTag.primaryColor(), parsedTag.secondaryColor(), false, segmentId);
                case GRADIENT -> new EffectStyle(this.shine, this.wave, this.shake, this.rainbow, this.pulse, EffectColorMode.GRADIENT, parsedTag.primaryColor(), parsedTag.secondaryColor(), false, segmentId);
                case NONE -> new EffectStyle(this.shine, this.wave, this.shake, this.rainbow, this.pulse, this.colorMode, this.primaryColor, this.secondaryColor, false, segmentId);
                case VANILLA -> vanillaStyle(segmentId);
            };
        }

        private String toTagPrefix() {
            if (this.isDefault()) {
                return "";
            }
            if (this.vanilla) {
                return "<" + EFFECT_TAG_NAMESPACE + ":vanilla>";
            }

            StringBuilder builder = new StringBuilder();
            if (this.shine) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":shine>");
            }
            if (this.wave) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":wave>");
            }
            if (this.shake) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":shake>");
            }
            if (this.rainbow) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":rainbow>");
            }
            if (this.pulse) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":pulse>");
            }
            if (this.colorMode == EffectColorMode.SOLID) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":").append(toHexColor(this.primaryColor)).append(">");
            } else if (this.colorMode == EffectColorMode.GRADIENT) {
                builder.append("<").append(EFFECT_TAG_NAMESPACE).append(":")
                    .append(toHexColor(this.primaryColor))
                    .append("-")
                    .append(toHexColor(this.secondaryColor))
                    .append(">");
            }
            return builder.toString();
        }
    }

    private enum EffectColorMode {
        NONE,
        SOLID,
        GRADIENT
    }

    private enum TagAction {
        NONE,
        SHINE,
        WAVE,
        SHAKE,
        RAINBOW,
        PULSE,
        VANILLA,
        SOLID,
        GRADIENT
    }

    private record ParsedTag(boolean valid, boolean resetsEffect, TagAction action, int primaryColor, int secondaryColor) {
        private static ParsedTag invalid() {
            return new ParsedTag(false, false, TagAction.NONE, 0, 0);
        }

        private static ParsedTag resetTag() {
            return new ParsedTag(true, true, TagAction.NONE, 0, 0);
        }

        private static ParsedTag shine() {
            return new ParsedTag(true, false, TagAction.SHINE, 0, 0);
        }

        private static ParsedTag wave() {
            return new ParsedTag(true, false, TagAction.WAVE, 0, 0);
        }

        private static ParsedTag shake() {
            return new ParsedTag(true, false, TagAction.SHAKE, 0, 0);
        }

        private static ParsedTag rainbow() {
            return new ParsedTag(true, false, TagAction.RAINBOW, 0, 0);
        }

        private static ParsedTag pulse() {
            return new ParsedTag(true, false, TagAction.PULSE, 0, 0);
        }

        private static ParsedTag vanilla() {
            return new ParsedTag(true, false, TagAction.VANILLA, 0, 0);
        }

        private static ParsedTag solid(int color) {
            return new ParsedTag(true, false, TagAction.SOLID, color, color);
        }

        private static ParsedTag gradient(int startColor, int endColor) {
            return new ParsedTag(true, false, TagAction.GRADIENT, startColor, endColor);
        }
    }
}
