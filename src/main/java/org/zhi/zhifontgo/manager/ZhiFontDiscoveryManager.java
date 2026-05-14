package org.zhi.zhifontgo.manager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.data.ZhiFontEntryData;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontDiscoveryManager {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".ttf", ".otf", ".ttc");

    private ZhiFontDiscoveryManager() {
    }

    public static Path getFontDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getResourcePackDirectory().resolve("font").toAbsolutePath().normalize();
    }

    public static List<ZhiFontEntryData> discoverFonts() {
        Path fontDirectory = getFontDirectory();
        ensureFontDirectory(fontDirectory);

        try (Stream<Path> pathStream = Files.walk(fontDirectory)) {
            return pathStream
                .filter(Files::isRegularFile)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(ZhiFontDiscoveryManager::isSupportedFontFile)
                .map(filePath -> toEntry(fontDirectory, filePath))
                .sorted(Comparator.comparing(ZhiFontEntryData::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to scan font directory " + fontDirectory, exception);
        }
    }

    private static void ensureFontDirectory(Path fontDirectory) {
        try {
            Files.createDirectories(fontDirectory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create font directory " + fontDirectory, exception);
        }
    }

    private static boolean isSupportedFontFile(Path filePath) {
        String lowercaseName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowercaseName::endsWith);
    }

    private static ZhiFontEntryData toEntry(Path fontDirectory, Path filePath) {
        String relativePath = fontDirectory.relativize(filePath).toString().replace('\\', '/');
        String displayName = stripExtension(relativePath);
        return new ZhiFontEntryData(displayName, relativePath, filePath);
    }

    private static String stripExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
    }
}
