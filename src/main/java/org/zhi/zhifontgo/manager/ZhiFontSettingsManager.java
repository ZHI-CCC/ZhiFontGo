package org.zhi.zhifontgo.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.zhi.zhifontgo.data.ZhiFontSettingsData;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontSettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SETTINGS_FILE_NAME = "zhifontgo-settings.json";

    private ZhiFontSettingsManager() {
    }

    public static Path settingsFilePath() {
        Path fontDirectory = ZhiFontDiscoveryManager.getFontDirectory();
        ensureFontDirectory(fontDirectory);
        return fontDirectory.resolve(SETTINGS_FILE_NAME);
    }

    public static ZhiFontSettingsData load(
        float defaultSize,
        float defaultOversample,
        float defaultShiftX,
        float defaultShiftY,
        boolean defaultShadowEnabled
    ) {
        Path settingsFile = settingsFilePath();
        if (!Files.isRegularFile(settingsFile)) {
            return new ZhiFontSettingsData(null, defaultSize, defaultOversample, defaultShiftX, defaultShiftY, defaultShadowEnabled);
        }

        try {
            JsonObject jsonObject = JsonParser.parseString(Files.readString(settingsFile, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ZhiFontSettingsData(
                optionalString(jsonObject, "selectedFont"),
                optionalFloat(jsonObject, "size", defaultSize),
                optionalFloat(jsonObject, "oversample", defaultOversample),
                optionalFloat(jsonObject, "shiftX", defaultShiftX),
                optionalFloat(jsonObject, "shiftY", defaultShiftY),
                optionalBoolean(jsonObject, "shadowEnabled", defaultShadowEnabled)
            );
        } catch (RuntimeException | IOException exception) {
            throw new UncheckedIOException(
                "Failed to read ZhiFontGo settings from " + settingsFile,
                exception instanceof IOException ioException ? ioException : new IOException(exception)
            );
        }
    }

    public static void save(ZhiFontSettingsData settingsData) {
        Path settingsFile = settingsFilePath();
        JsonObject jsonObject = new JsonObject();
        if (settingsData.selectedFontRelativePath() != null && !settingsData.selectedFontRelativePath().isBlank()) {
            jsonObject.addProperty("selectedFont", settingsData.selectedFontRelativePath());
        }
        jsonObject.addProperty("size", settingsData.size());
        jsonObject.addProperty("oversample", settingsData.oversample());
        jsonObject.addProperty("shiftX", settingsData.shiftX());
        jsonObject.addProperty("shiftY", settingsData.shiftY());
        jsonObject.addProperty("shadowEnabled", settingsData.shadowEnabled());

        try {
            Files.writeString(settingsFile, GSON.toJson(jsonObject), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save ZhiFontGo settings to " + settingsFile, exception);
        }
    }

    private static void ensureFontDirectory(Path fontDirectory) {
        try {
            Files.createDirectories(fontDirectory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create font directory " + fontDirectory, exception);
        }
    }

    private static String optionalString(JsonObject jsonObject, String key) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsString() : null;
    }

    private static float optionalFloat(JsonObject jsonObject, String key, float defaultValue) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsFloat() : defaultValue;
    }

    private static boolean optionalBoolean(JsonObject jsonObject, String key, boolean defaultValue) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsBoolean() : defaultValue;
    }
}
