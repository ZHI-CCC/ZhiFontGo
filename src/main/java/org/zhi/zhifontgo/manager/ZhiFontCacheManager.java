package org.zhi.zhifontgo.manager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.Main;
import org.zhi.zhifontgo.data.ZhiFontData;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontCacheManager {
    private static final String CACHE_DIRECTORY_NAME = ".zhifontgo-cache";
    private static final int CACHE_VERSION = 4;
    private static final int CACHE_MAGIC = 0x5A484647;
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZhiFontGo-CacheSave");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, Integer> SAVE_VERSIONS = new ConcurrentHashMap<>();

    private ZhiFontCacheManager() {
    }

    public static String buildCacheKey(byte[] fontBytes, ZhiFontData fontData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("zhifontgo-cache-v" + CACHE_VERSION).getBytes(StandardCharsets.UTF_8));
            digest.update(fontBytes);
            updateDigest(digest, String.format(Locale.ROOT, "%.4f", fontData.size()));
            updateDigest(digest, String.format(Locale.ROOT, "%.4f", fontData.oversample()));
            updateDigest(digest, String.format(Locale.ROOT, "%.4f", fontData.shiftX()));
            updateDigest(digest, String.format(Locale.ROOT, "%.4f", fontData.shiftY()));
            updateDigest(digest, fontData.skip());
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available for glyph cache keys.", exception);
        }
    }

    public static String buildCacheFileName(ZhiFontData fontData, String cacheKey) {
        String family = sanitizeFileNamePart(fontData.familyName());
        String parameters = String.format(
            Locale.ROOT,
            "s%s_o%s_x%s_y%s",
            formatNumber(fontData.size()),
            formatNumber(fontData.oversample()),
            formatNumber(fontData.shiftX()),
            formatNumber(fontData.shiftY())
        );
        return family + "__" + parameters + "__" + cacheKey.substring(0, 12) + ".bin";
    }

    public static List<ZhiFontGlyphProviderManager.CachedGlyphData> loadGlyphs(String cacheKey, String cacheFileName) {
        return loadGlyphs(cacheKey, cacheFileName, null);
    }

    public static List<ZhiFontGlyphProviderManager.CachedGlyphData> loadGlyphs(
        String cacheKey,
        String cacheFileName,
        @javax.annotation.Nullable Iterable<Integer> requestedCodepoints
    ) {
        Path cacheFile = cacheFile(cacheKey, cacheFileName);
        if (!Files.isRegularFile(cacheFile)) {
            return List.of();
        }

        Set<Integer> requestedCodepointSet = null;
        if (requestedCodepoints != null) {
            requestedCodepointSet = new HashSet<>();
            for (Integer codepoint : requestedCodepoints) {
                if (codepoint != null) {
                    requestedCodepointSet.add(codepoint);
                }
            }
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(cacheFile))))) {
            if (input.readInt() != CACHE_MAGIC) {
                Main.LOGGER.warn("ZhiFontGo cache file '{}' has an invalid header. Ignoring it.", cacheFile);
                return List.of();
            }
            if (input.readInt() != CACHE_VERSION) {
                Main.LOGGER.info("ZhiFontGo cache file '{}' is from an older format. It will be rebuilt.", cacheFile);
                return List.of();
            }

            int glyphCount = input.readInt();
            List<ZhiFontGlyphProviderManager.CachedGlyphData> glyphs = new ArrayList<>(glyphCount);
            for (int index = 0; index < glyphCount; index++) {
                int codepoint = input.readInt();
                boolean spaceGlyph = input.readBoolean();
                float advance = input.readFloat();
                float bearingLeft = input.readFloat();
                float bearingTop = input.readFloat();
                int width = input.readInt();
                int height = input.readInt();
                int pixelLength = input.readInt();
                validateGlyphRecord(cacheFile, codepoint, spaceGlyph, advance, bearingLeft, bearingTop, width, height, pixelLength);
                boolean shouldKeepGlyph = requestedCodepointSet == null || requestedCodepointSet.contains(codepoint);
                if (shouldKeepGlyph) {
                    byte[] pixels = new byte[pixelLength];
                    input.readFully(pixels);
                    glyphs.add(new ZhiFontGlyphProviderManager.CachedGlyphData(codepoint, pixels, width, height, advance, bearingLeft, bearingTop, spaceGlyph));
                } else {
                    input.skipNBytes(pixelLength);
                }
            }
            return glyphs;
        } catch (IOException exception) {
            Main.LOGGER.warn("ZhiFontGo failed to read glyph cache '{}'. It will be rebuilt.", cacheFile, exception);
            return List.of();
        }
    }

    public static boolean hasCacheFile(String cacheKey, String cacheFileName) {
        return Files.isRegularFile(cacheFile(cacheKey, cacheFileName));
    }

    public static void saveGlyphs(String cacheKey, String cacheFileName, Collection<ZhiFontGlyphProviderManager.CachedGlyphData> glyphs) {
        Path cacheDirectory = cacheDirectory();
        Path targetFile = cacheFile(cacheKey, cacheFileName);
        try {
            Files.createDirectories(cacheDirectory);
            Path tempFile = Files.createTempFile(cacheDirectory, cacheKey + "-", ".tmp");
            List<ZhiFontGlyphProviderManager.CachedGlyphData> sortedGlyphs = new ArrayList<>(glyphs);
            sortedGlyphs.sort(Comparator.comparingInt(ZhiFontGlyphProviderManager.CachedGlyphData::codepoint));

            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tempFile))))) {
                output.writeInt(CACHE_MAGIC);
                output.writeInt(CACHE_VERSION);
                output.writeInt(sortedGlyphs.size());
                for (ZhiFontGlyphProviderManager.CachedGlyphData glyph : sortedGlyphs) {
                    output.writeInt(glyph.codepoint());
                    output.writeBoolean(glyph.spaceGlyph());
                    output.writeFloat(glyph.advance());
                    output.writeFloat(glyph.bearingLeft());
                    output.writeFloat(glyph.bearingTop());
                    output.writeInt(glyph.width());
                    output.writeInt(glyph.height());
                    output.writeInt(glyph.sdfPixels().length);
                    output.write(glyph.sdfPixels());
                }
            }

            try {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            Path legacyFile = legacyCacheFile(cacheKey);
            if (!legacyFile.equals(targetFile)) {
                Files.deleteIfExists(legacyFile);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save glyph cache to " + targetFile, exception);
        }
    }

    public static void saveGlyphsAsync(String cacheKey, String cacheFileName, Collection<ZhiFontGlyphProviderManager.CachedGlyphData> glyphs) {
        saveGlyphsAsync(cacheKey, cacheFileName, () -> glyphs, null);
    }

    public static void saveGlyphsAsync(
        String cacheKey,
        String cacheFileName,
        Supplier<? extends Collection<ZhiFontGlyphProviderManager.CachedGlyphData>> glyphSupplier,
        Runnable completionCallback
    ) {
        int saveVersion = SAVE_VERSIONS.merge(cacheKey, 1, Integer::sum);
        SAVE_EXECUTOR.execute(() -> {
            Integer latestVersion = SAVE_VERSIONS.get(cacheKey);
            if (latestVersion != null && saveVersion < latestVersion) {
                if (completionCallback != null) {
                    completionCallback.run();
                }
                return;
            }

            try {
                Collection<ZhiFontGlyphProviderManager.CachedGlyphData> glyphs = glyphSupplier.get();
                List<ZhiFontGlyphProviderManager.CachedGlyphData> snapshot = new ArrayList<>(glyphs);
                saveGlyphs(cacheKey, cacheFileName, snapshot);
            } catch (RuntimeException exception) {
                Main.LOGGER.warn("ZhiFontGo failed to save glyph cache '{}' in the background.", cacheFileName, exception);
            } finally {
                SAVE_VERSIONS.computeIfPresent(cacheKey, (ignoredKey, currentVersion) -> currentVersion == saveVersion ? null : currentVersion);
                if (completionCallback != null) {
                    completionCallback.run();
                }
            }
        });
    }

    public static Path cacheDirectory() {
        Path directory = ZhiFontManager.fontDirectory().resolve(CACHE_DIRECTORY_NAME);
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create font cache directory " + directory, exception);
        }
    }

    private static Path cacheFile(String cacheKey, String cacheFileName) {
        Path preferredFile = cacheDirectory().resolve(cacheFileName);
        if (Files.isRegularFile(preferredFile)) {
            return preferredFile;
        }

        Path legacyFile = legacyCacheFile(cacheKey);
        if (Files.isRegularFile(legacyFile)) {
            return legacyFile;
        }
        return preferredFile;
    }

    private static Path legacyCacheFile(String cacheKey) {
        return cacheDirectory().resolve(cacheKey + ".bin");
    }

    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte)0);
    }

    private static void validateGlyphRecord(
        Path cacheFile,
        int codepoint,
        boolean spaceGlyph,
        float advance,
        float bearingLeft,
        float bearingTop,
        int width,
        int height,
        int pixelLength
    ) throws IOException {
        if (!Float.isFinite(advance) || !Float.isFinite(bearingLeft) || !Float.isFinite(bearingTop)) {
            throw new IOException("Glyph record contains non-finite metrics in " + cacheFile + " for codepoint " + codepoint);
        }
        if (spaceGlyph) {
            if (width != 0 || height != 0 || pixelLength != 0) {
                throw new IOException("Space glyph record is malformed in " + cacheFile + " for codepoint " + codepoint);
            }
            return;
        }
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
            throw new IOException("Glyph dimensions are invalid in " + cacheFile + " for codepoint " + codepoint + ": " + width + "x" + height);
        }
        if (pixelLength != width * height) {
            throw new IOException("Glyph pixel payload length is invalid in " + cacheFile + " for codepoint " + codepoint);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte currentByte : bytes) {
            builder.append(Character.forDigit((currentByte >> 4) & 0xF, 16));
            builder.append(Character.forDigit(currentByte & 0xF, 16));
        }
        return builder.toString();
    }

    private static String sanitizeFileNamePart(String text) {
        String sanitized = text
            .replaceAll("[\\\\/:*?\"<>|]+", "_")
            .replaceAll("\\s+", "_")
            .replaceAll("_+", "_")
            .trim();
        if (sanitized.isEmpty()) {
            return "font";
        }
        return sanitized.length() > 48 ? sanitized.substring(0, 48) : sanitized;
    }

    private static String formatNumber(float value) {
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        return formatted.replace('-', 'n').replace('.', '_');
    }
}
