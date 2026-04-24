package me.katze.powerac.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.utility.StringUtility;

public class BrandCapturePacket extends PacketListenerAbstract {
    private static final String CHANNEL =
        PacketEvents.getAPI().getServerManager().getVersion()
            .isNewerThanOrEquals(ServerVersion.V_1_13)
            ? "minecraft:brand" : "MC|Brand";
    private static final long ENTRY_TTL_MILLIS = 2L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 30L * 1000L;

    private final Map<UUID, CachedBrandEntry> pendingBrands = new ConcurrentHashMap<>();
    private volatile long nextCleanupAt = System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS;
    @Setter
    private PlayerManager playerManager;

    public BrandCapturePacket() {
        super(PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getUser() == null) {
            return;
        }

        UUID uuid = event.getUser().getUUID();
        if (uuid == null) {
            return;
        }

        cleanupExpiredIfNeeded();

        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet =
                new WrapperPlayClientPluginMessage(event);
            capture(uuid, packet.getChannelName(), packet.getData(), event.getUser().getClientVersion());
            return;
        }
        if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet =
                new WrapperConfigClientPluginMessage(event);
            capture(uuid, packet.getChannelName(), packet.getData(), event.getUser().getClientVersion());
        }
    }

    public CapturedBrand consume(UUID uuid) {
        cleanupExpiredIfNeeded();
        CachedBrandEntry entry = pendingBrands.remove(uuid);
        if (entry == null || entry.isExpired(System.currentTimeMillis())) {
            return null;
        }
        return new CapturedBrand(entry.getBrand(), entry.getVersion());
    }

    public void remove(UUID uuid) {
        if (uuid == null) {
            return;
        }
        pendingBrands.remove(uuid);
    }

    private void capture(UUID uuid, String channel, byte[] data, ClientVersion version) {
        if (channel == null || !CHANNEL.equals(channel)) {
            return;
        }

        long now = System.currentTimeMillis();
        String brand = parseBrand(data);
        pendingBrands.put(uuid, new CachedBrandEntry(brand, version, now + ENTRY_TTL_MILLIS));

        PlayerManager currentPlayerManager = playerManager;
        if (currentPlayerManager == null) {
            return;
        }

        currentPlayerManager.handleBrandUpdate(uuid, brand, version);
    }

    private void cleanupExpiredIfNeeded() {
        long now = System.currentTimeMillis();
        if (now < nextCleanupAt) {
            return;
        }

        nextCleanupAt = now + CLEANUP_INTERVAL_MILLIS;
        Iterator<Map.Entry<UUID, CachedBrandEntry>> iterator = pendingBrands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CachedBrandEntry> entry = iterator.next();
            if (entry.getValue() == null || entry.getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private String parseBrand(byte[] data) {
        if (data == null || data.length == 0 || data.length > 64) {
            return "unknown";
        }

        byte[] payload = new byte[Math.max(0, data.length - 1)];
        if (payload.length > 0) {
            System.arraycopy(data, 1, payload, 0, payload.length);
        }

        String parsed = new String(payload, StandardCharsets.UTF_8)
            .replace(" (Velocity)", "");
        parsed = StringUtility.strip(parsed).trim();
        return parsed.isEmpty() ? "unknown" : parsed;
    }

    @Getter
    @AllArgsConstructor
    public static final class CapturedBrand {
        private final String brand;
        private final ClientVersion version;
    }

    @Getter
    @AllArgsConstructor
    private static final class CachedBrandEntry {
        private final String brand;
        private final ClientVersion version;
        private final long expiresAt;

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
