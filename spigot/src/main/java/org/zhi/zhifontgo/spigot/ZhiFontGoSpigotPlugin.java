package org.zhi.zhifontgo.spigot;

import java.nio.ByteBuffer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ZhiFontGoSpigotPlugin extends JavaPlugin implements PluginMessageListener {
    private static final String CHANNEL_ID = "zhifontgo:server_guard";
    private static final byte ACTION_HELLO = 1;
    private static final byte ACTION_ACK = 2;
    private static final int PROTOCOL_VERSION = 1;
    private static final int MESSAGE_SIZE = 5;

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL_ID, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL_ID);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL_ID, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL_ID);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL_ID.equals(channel) || message.length < MESSAGE_SIZE) {
            return;
        }

        ByteBuffer input = ByteBuffer.wrap(message);
        byte action = input.get();
        int protocolVersion = input.getInt();
        if (action != ACTION_HELLO || protocolVersion != PROTOCOL_VERSION) {
            return;
        }

        ByteBuffer output = ByteBuffer.allocate(MESSAGE_SIZE);
        output.put(ACTION_ACK);
        output.putInt(PROTOCOL_VERSION);
        player.sendPluginMessage(this, CHANNEL_ID, output.array());
    }
}
