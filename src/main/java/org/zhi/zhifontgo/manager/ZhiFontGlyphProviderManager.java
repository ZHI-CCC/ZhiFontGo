package org.zhi.zhifontgo.manager;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.data.ZhiFontData;
import org.zhi.zhifontgo.util.ZhiFontCodepointUtil;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontGlyphProviderManager implements GlyphProvider {
    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(null, true, true);
    private static final int INITIAL_COMMON_HAN_END = 0x7FFF;
    private static final int SDF_SCALE = 4;
    private static final int SDF_SPREAD = 12;
    private static final float IRIS_COMPAT_EDGE_WIDTH = 0.065F;
    private static final double OUTLINE_FLATNESS = 0.1D;
    private final java.awt.Font font;
    private final float oversample;
    private final float shiftX;
    private final float shiftY;
    private final IntSet skip = new IntArraySet();
    private final IntSet seedGlyphs;
    private final IntSet initialWarmupGlyphs = new IntArraySet();
    private final ConcurrentHashMap<Integer, GlyphInfo> glyphCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CachedGlyphData> glyphDataCache = new ConcurrentHashMap<>();
    private final Set<Integer> missingGlyphs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean runtimeSaveQueued = new AtomicBoolean(false);
    @Nullable
    private volatile String runtimeCacheKey;
    @Nullable
    private volatile String runtimeCacheFileName;

    private ZhiFontGlyphProviderManager(java.awt.Font font, ZhiFontData fontData) {
        this.oversample = fontData.oversample() * SDF_SCALE;
        this.font = font.deriveFont(fontData.size() * this.oversample);
        this.shiftX = fontData.shiftX() * this.oversample;
        this.shiftY = -fontData.shiftY() * this.oversample;
        fontData.skip().codePoints().forEach(this.skip::add);
        this.seedGlyphs = ZhiFontCodepointUtil.buildSeedGlyphs(fontData.seedCharacters());
        createBasePreloadCodepoints(fontData.seedCharacters()).forEach(this.initialWarmupGlyphs::add);
    }

    public static ZhiFontGlyphProviderManager create(ZhiFontData fontData, byte[] fontBytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fontBytes)) {
            java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream);
            return new ZhiFontGlyphProviderManager(font, fontData);
        } catch (java.awt.FontFormatException | IOException exception) {
            throw new IllegalStateException("Failed to initialize SDF font renderer", exception);
        }
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int codepoint) {
        if (this.skip.contains(codepoint) || this.missingGlyphs.contains(codepoint)) {
            return null;
        }

        GlyphInfo cachedGlyph = this.glyphCache.get(codepoint);
        if (cachedGlyph != null) {
            return cachedGlyph;
        }

        CachedGlyphData glyphData = this.glyphDataCache.get(codepoint);
        if (glyphData != null) {
            GlyphInfo glyphInfo = this.createGlyphInfo(glyphData);
            GlyphInfo previousGlyph = this.glyphCache.putIfAbsent(codepoint, glyphInfo);
            return previousGlyph != null ? previousGlyph : glyphInfo;
        }

        if (this.warmUpGlyph(codepoint)) {
            return this.glyphCache.get(codepoint);
        }
        return null;
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return this.seedGlyphs;
    }

    @Override
    public void close() {
        this.glyphCache.clear();
        this.glyphDataCache.clear();
        this.missingGlyphs.clear();
        this.runtimeSaveQueued.set(false);
    }

    public void warmUpInitialGlyphs() {
        for (Integer codepoint : this.initialWarmupGlyphs) {
            this.warmUpGlyph(codepoint);
        }
    }

    public boolean isMissingGlyph(int codepoint) {
        return this.missingGlyphs.contains(codepoint);
    }

    public void enableRuntimeCachePersistence(String cacheKey, String cacheFileName) {
        this.runtimeCacheKey = cacheKey;
        this.runtimeCacheFileName = cacheFileName;
    }

    public boolean warmUpGlyph(int codepoint) {
        if (this.skip.contains(codepoint) || this.missingGlyphs.contains(codepoint) || this.glyphCache.containsKey(codepoint)) {
            return false;
        }
        if (!this.font.canDisplay(codepoint)) {
            this.missingGlyphs.add(codepoint);
            return false;
        }

        CachedGlyphData glyphData = this.createGlyphData(codepoint);
        this.glyphDataCache.put(codepoint, glyphData);
        this.glyphCache.put(codepoint, this.createGlyphInfo(glyphData));
        this.scheduleRuntimeCacheSave();
        return true;
    }

    public void loadCachedGlyphs(Iterable<CachedGlyphData> cachedGlyphs) {
        for (CachedGlyphData glyphData : cachedGlyphs) {
            if (glyphData == null || this.skip.contains(glyphData.codepoint())) {
                continue;
            }
            this.glyphDataCache.put(glyphData.codepoint(), glyphData);
            this.glyphCache.put(glyphData.codepoint(), this.createGlyphInfo(glyphData));
        }
    }

    public Collection<CachedGlyphData> exportCachedGlyphs() {
        return new ArrayList<>(this.glyphDataCache.values());
    }

    public int cachedGlyphCount() {
        return this.glyphDataCache.size();
    }

    public IntList preloadCodepoints() {
        return new IntArrayList(this.initialWarmupGlyphs);
    }

    public IntSet initialWarmupGlyphs() {
        return this.initialWarmupGlyphs;
    }

    public static IntList createBasePreloadCodepoints(String seedCharacters) {
        IntSet baseCodepoints = new IntArraySet();
        addPreloadRange(baseCodepoints, 0x0020, 0x00FF);
        addPreloadRange(baseCodepoints, 0x2000, 0x206F);
        addPreloadRange(baseCodepoints, 0x3000, 0x303F);
        addPreloadRange(baseCodepoints, 0x3040, 0x30FF);
        addPreloadRange(baseCodepoints, 0x3400, 0x4DBF);
        addPreloadRange(baseCodepoints, 0x4E00, INITIAL_COMMON_HAN_END);
        addPreloadRange(baseCodepoints, 0xF900, 0xFAFF);
        addPreloadRange(baseCodepoints, 0xFF00, 0xFFEF);
        seedCharacters.codePoints().forEach(baseCodepoints::add);
        baseCodepoints.add(' ');
        return new IntArrayList(baseCodepoints);
    }

    private void scheduleRuntimeCacheSave() {
        String cacheKey = this.runtimeCacheKey;
        String cacheFileName = this.runtimeCacheFileName;
        if (cacheKey == null || cacheFileName == null) {
            return;
        }
        if (!this.runtimeSaveQueued.compareAndSet(false, true)) {
            return;
        }

        ZhiFontCacheManager.saveGlyphsAsync(cacheKey, cacheFileName, this::exportCachedGlyphs, () -> this.runtimeSaveQueued.set(false));
    }

    private CachedGlyphData createGlyphData(int codepoint) {
        String text = new String(Character.toChars(codepoint));
        GlyphVector glyphVector = this.font.createGlyphVector(FONT_RENDER_CONTEXT, text);
        GlyphMetrics glyphMetrics = glyphVector.getGlyphMetrics(0);
        float advance = (float)glyphMetrics.getAdvanceX() / this.oversample;

        Shape outline = glyphVector.getGlyphOutline(0);
        Shape transformedOutline = AffineTransform.getTranslateInstance(this.shiftX, this.shiftY).createTransformedShape(outline);
        Rectangle2D bounds = transformedOutline.getBounds2D();
        int minX = (int)Math.floor(bounds.getMinX()) - SDF_SPREAD;
        int minY = (int)Math.floor(bounds.getMinY()) - SDF_SPREAD;
        int maxX = (int)Math.ceil(bounds.getMaxX()) + SDF_SPREAD;
        int maxY = (int)Math.ceil(bounds.getMaxY()) + SDF_SPREAD;
        int width = maxX - minX;
        int height = maxY - minY;
        if (width <= 0 || height <= 0) {
            return CachedGlyphData.space(codepoint, advance);
        }

        List<Segment> segments = flattenOutline(transformedOutline);
        if (segments.isEmpty()) {
            return CachedGlyphData.space(codepoint, advance);
        }

        return new CachedGlyphData(
            codepoint,
            buildSdfPixels(transformedOutline, segments, width, height, minX, minY),
            width,
            height,
            advance,
            (float)minX / this.oversample,
            (float)(-minY) / this.oversample,
            false
        );
    }

    private GlyphInfo createGlyphInfo(CachedGlyphData glyphData) {
        if (glyphData.spaceGlyph()) {
            return (GlyphInfo.SpaceGlyphInfo)(() -> glyphData.advance());
        }
        return new Glyph(glyphData);
    }

    private static List<Segment> flattenOutline(Shape outline) {
        List<Segment> segments = new ArrayList<>();
        PathIterator pathIterator = outline.getPathIterator(null, OUTLINE_FLATNESS);
        double[] coords = new double[6];
        double startX = 0.0D;
        double startY = 0.0D;
        double previousX = 0.0D;
        double previousY = 0.0D;

        while (!pathIterator.isDone()) {
            switch (pathIterator.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO -> {
                    startX = coords[0];
                    startY = coords[1];
                    previousX = coords[0];
                    previousY = coords[1];
                }
                case PathIterator.SEG_LINETO -> {
                    segments.add(new Segment(previousX, previousY, coords[0], coords[1]));
                    previousX = coords[0];
                    previousY = coords[1];
                }
                case PathIterator.SEG_CLOSE -> {
                    segments.add(new Segment(previousX, previousY, startX, startY));
                    previousX = startX;
                    previousY = startY;
                }
                default -> {
                }
            }
            pathIterator.next();
        }

        return segments;
    }

    private static float signedDistance(Shape outline, List<Segment> segments, double sampleX, double sampleY) {
        double minDistanceSquared = Double.POSITIVE_INFINITY;
        for (Segment segment : segments) {
            double distanceSquared = segment.distanceSquared(sampleX, sampleY);
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
            }
        }

        float distance = (float)Math.sqrt(minDistanceSquared);
        return outline.contains(sampleX, sampleY) ? distance : -distance;
    }

    private static byte[] buildSdfPixels(Shape outline, List<Segment> segments, int width, int height, int minX, int minY) {
        byte[] pixels = new byte[width * height];
        int pixelIndex = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                double sampleX = minX + column + 0.5D;
                double sampleY = minY + row + 0.5D;
                float distance = signedDistance(outline, segments, sampleX, sampleY);
                float normalized = 0.5F + distance / (2.0F * SDF_SPREAD);
                float clamped = Math.max(0.0F, Math.min(1.0F, normalized));
                pixels[pixelIndex++] = (byte)Math.round(clamped * 255.0F);
            }
        }
        return pixels;
    }

    private void addInitialWarmupRange(int startInclusive, int endInclusive) {
        addPreloadRange(this.initialWarmupGlyphs, startInclusive, endInclusive);
    }

    private static void addPreloadRange(IntSet target, int startInclusive, int endInclusive) {
        for (int codepoint = startInclusive; codepoint <= endInclusive; codepoint++) {
            if (!Character.isSurrogate((char)codepoint)) {
                target.add(codepoint);
            }
        }
    }

    public record CachedGlyphData(
        int codepoint,
        byte[] sdfPixels,
        int width,
        int height,
        float advance,
        float bearingLeft,
        float bearingTop,
        boolean spaceGlyph
    ) {
        public static CachedGlyphData space(int codepoint, float advance) {
            return new CachedGlyphData(codepoint, new byte[0], 0, 0, advance, 0.0F, 0.0F, true);
        }
    }

    public interface RuntimeSheetGlyphInfo extends SheetGlyphInfo {
        void upload(int x, int y, boolean irisCompatible);
    }

    private final class Glyph implements GlyphInfo {
        private final CachedGlyphData glyphData;

        private Glyph(CachedGlyphData glyphData) {
            this.glyphData = glyphData;
        }

        @Override
        public float getAdvance() {
            return this.glyphData.advance();
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitcher) {
            return stitcher.apply(new RuntimeSheetGlyphInfo() {
                @Override
                public int getPixelWidth() {
                    return Glyph.this.glyphData.width();
                }

                @Override
                public int getPixelHeight() {
                    return Glyph.this.glyphData.height();
                }

                @Override
                public void upload(int x, int y) {
                    this.upload(x, y, false);
                }

                @Override
                public void upload(int x, int y, boolean irisCompatible) {
                    NativeImage nativeImage = new NativeImage(
                        NativeImage.Format.LUMINANCE,
                        Glyph.this.glyphData.width(),
                        Glyph.this.glyphData.height(),
                        false
                    );
                    try {
                        int pixelIndex = 0;
                        for (int row = 0; row < Glyph.this.glyphData.height(); row++) {
                            for (int column = 0; column < Glyph.this.glyphData.width(); column++) {
                                byte pixel = Glyph.this.glyphData.sdfPixels()[pixelIndex++];
                                nativeImage.setPixelLuminance(
                                    column,
                                    row,
                                    irisCompatible ? toIrisCompatibleAlpha(pixel) : pixel
                                );
                            }
                        }
                        nativeImage.upload(0, x, y, false);
                    } finally {
                        nativeImage.close();
                    }
                }

                @Override
                public boolean isColored() {
                    return false;
                }

                @Override
                public float getOversample() {
                    return ZhiFontGlyphProviderManager.this.oversample;
                }

                @Override
                public float getBearingLeft() {
                    return Glyph.this.glyphData.bearingLeft();
                }

                @Override
                public float getBearingTop() {
                    return Glyph.this.glyphData.bearingTop();
                }
            });
        }
    }

    private static byte toIrisCompatibleAlpha(byte sdfPixel) {
        float distanceValue = (sdfPixel & 0xFF) / 255.0F;
        float alpha = smoothstep(0.5F - IRIS_COMPAT_EDGE_WIDTH, 0.5F + IRIS_COMPAT_EDGE_WIDTH, distanceValue);
        return (byte)Math.round(alpha * 255.0F);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private record Segment(double x0, double y0, double x1, double y1) {
        private double distanceSquared(double px, double py) {
            double dx = this.x1 - this.x0;
            double dy = this.y1 - this.y0;
            double lengthSquared = dx * dx + dy * dy;
            if (lengthSquared == 0.0D) {
                double diffX = px - this.x0;
                double diffY = py - this.y0;
                return diffX * diffX + diffY * diffY;
            }

            double projection = ((px - this.x0) * dx + (py - this.y0) * dy) / lengthSquared;
            double clampedProjection = Math.max(0.0D, Math.min(1.0D, projection));
            double nearestX = this.x0 + clampedProjection * dx;
            double nearestY = this.y0 + clampedProjection * dy;
            double diffX = px - nearestX;
            double diffY = py - nearestY;
            return diffX * diffX + diffY * diffY;
        }
    }
}
