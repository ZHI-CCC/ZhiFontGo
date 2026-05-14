package org.zhi.zhifontgo.manager;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ZhiFontServerGuardManager {
    private static final String ALLOWED_DOMAIN = "pengs7.top";

    private ZhiFontServerGuardManager() {
    }

    public static void validateCurrentServer(@Nullable Connection connection) {
        if (connection == null || connection.isMemoryConnection()) {
            return;
        }

        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        String serverAddress = serverData == null ? null : serverData.ip;
        if (isAllowedServer(serverAddress)) {
            return;
        }

        connection.disconnect(disallowedServerMessage());
    }

    public static void clear() {
    }

    private static boolean isAllowedServer(@Nullable String serverAddress) {
        String host = normalizeHost(serverAddress);
        return ALLOWED_DOMAIN.equals(host) || host.endsWith("." + ALLOWED_DOMAIN);
    }

    private static String normalizeHost(@Nullable String serverAddress) {
        if (serverAddress == null) {
            return "";
        }

        String host = serverAddress.trim().toLowerCase(Locale.ROOT);
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }

        if (host.startsWith("[")) {
            int bracketIndex = host.indexOf(']');
            return bracketIndex > 0 ? host.substring(1, bracketIndex) : host;
        }

        int colonIndex = host.indexOf(':');
        if (colonIndex >= 0) {
            host = host.substring(0, colonIndex);
        }

        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static Component disallowedServerMessage() {
        return Component.empty()
            .append(Component.translatable("disconnect.zhifontgo.disallowed_server.line1", ALLOWED_DOMAIN))
            .append(Component.literal("\n"))
            .append(Component.translatable("disconnect.zhifontgo.disallowed_server.line2"));
    }
}
