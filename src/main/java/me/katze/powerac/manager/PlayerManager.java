package me.katze.powerac.manager;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import me.katze.powerac.PowerAC;
import me.katze.powerac.module.impl.RotationModule;
import me.katze.powerac.object.socket.AuthInfo;
import me.katze.powerac.packet.BrandCapturePacket;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.scheduler.TaskHandle;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.object.socket.SocketPlayerInfo;
import me.katze.powerac.tracker.impl.ActionTracker;
import me.katze.powerac.tracker.impl.RotationTracker;
import me.katze.powerac.utility.StringUtility;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class PlayerManager {

    private final PowerAC plugin;
    private final BrandCapturePacket brandCapturePacket;
    private final Map<UUID, PowerPlayer> players = new ConcurrentHashMap<>();
    private final Set<UUID> alertsEnabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> monitoredTargets = new ConcurrentHashMap<>();
    private final Map<UUID, String> monitorActionBars = new ConcurrentHashMap<>();
    private TaskHandle monitorActionBarTask = TaskHandle.NO_OP;

    public PlayerManager(PowerAC plugin, BrandCapturePacket brandCapturePacket) {
        this.plugin = plugin;
        this.brandCapturePacket = brandCapturePacket;
        this.monitorActionBarTask = plugin
            .getTaskScheduler()
            .runGlobalTimer(this::broadcastMonitorActionBars, 1L, 20L);
    }

    public PowerPlayer handleJoin(User user) {
        if (user == null || user.getUUID() == null) {
            return null;
        }

        PowerPlayer powerPlayer = players.compute(
            user.getUUID(),
            (id, existing) -> {
                if (existing == null) {
                    return createPowerPlayer(
                        user.getUUID(),
                        user.getName(),
                        user,
                        user.getEntityId()
                    );
                }
                existing.setName(user.getName());
                existing.setUser(user);
                existing.setEntityId(user.getEntityId());
                return existing;
            }
        );

        BrandCapturePacket.CapturedBrand captured =
            brandCapturePacket != null ? brandCapturePacket.consume(user.getUUID()) : null;
        if (captured != null) {
            applyBrandData(powerPlayer, captured.getBrand(), captured.getVersion());
        } else {
            if (powerPlayer.getBrand() == null || powerPlayer.getBrand().trim().isEmpty()) {
                powerPlayer.setBrand("unknown");
            }
            if (powerPlayer.getClientVersion() == null) {
                powerPlayer.setClientVersion(user.getClientVersion());
            }
        }

        enableAlertsOnJoinIfNeeded(user.getUUID());
        return powerPlayer;
    }

    public void handleBrandUpdate(UUID uuid, String brand, ClientVersion version) {
        if (uuid == null) {
            return;
        }

        PowerPlayer powerPlayer = players.get(uuid);
        if (powerPlayer == null) {
            return;
        }

        applyBrandData(powerPlayer, brand, version);
    }

    public PowerPlayer ensureTrackedPlayer(User user) {
        if (user == null || user.getUUID() == null) {
            return null;
        }
        return players.computeIfAbsent(
            user.getUUID(),
            ignored -> createPowerPlayer(
                user.getUUID(),
                user.getName(),
                user,
                user.getEntityId()
            )
        );
    }

    public void handleQuit(UUID uuid) {
        players.remove(uuid);
        alertsEnabled.remove(uuid);
        monitoredTargets.remove(uuid);
        monitorActionBars.remove(uuid);
        for (Map.Entry<UUID, UUID> entry : monitoredTargets.entrySet()) {
            if (uuid.equals(entry.getValue())) {
                monitoredTargets.remove(entry.getKey());
                monitorActionBars.remove(entry.getKey());
            }
        }
        if (brandCapturePacket != null) {
            brandCapturePacket.remove(uuid);
        }
    }

    public void shutdown() {
        monitorActionBarTask.cancel();
        monitorActionBarTask = TaskHandle.NO_OP;
        monitorActionBars.clear();
        monitoredTargets.clear();
    }

    public PowerPlayer get(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return players.get(uuid);
    }

    public boolean isAlertsEnabled(UUID uuid) {
        return alertsEnabled.contains(uuid);
    }

    public void setAlertsEnabled(UUID uuid, boolean enabled) {
        if (enabled) {
            alertsEnabled.add(uuid);
        } else {
            alertsEnabled.remove(uuid);
        }
    }

    public UUID getMonitoredTarget(UUID viewerUuid) {
        if (viewerUuid == null) {
            return null;
        }
        return monitoredTargets.get(viewerUuid);
    }

    public boolean toggleMonitor(UUID viewerUuid, UUID targetUuid, String targetName) {
        if (viewerUuid == null || targetUuid == null) {
            return false;
        }
        UUID currentTarget = monitoredTargets.get(viewerUuid);
        if (targetUuid.equals(currentTarget)) {
            monitoredTargets.remove(viewerUuid);
            monitorActionBars.remove(viewerUuid);
            return false;
        }
        monitoredTargets.put(viewerUuid, targetUuid);
        monitorActionBars.put(
            viewerUuid,
            formatMonitorActionBar(targetUuid, targetName, -1D, false, true, "--:--:--")
        );
        return true;
    }

    public void publishMonitorUpdate(
        UUID monitoredPlayerUuid,
        String monitoredPlayerName,
        double probability,
        boolean detected,
        boolean globalModel
    ) {
        if (monitoredPlayerUuid == null) {
            return;
        }
        String probabilityText = probability < 0D
            ? "?"
            : String.format(Locale.US, "%.3f", probability);
        String timeText = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String message = formatMonitorActionBar(
            monitoredPlayerUuid,
            monitoredPlayerName,
            probability,
            detected,
            globalModel,
            timeText
        );
        for (Map.Entry<UUID, UUID> entry : monitoredTargets.entrySet()) {
            if (!monitoredPlayerUuid.equals(entry.getValue())) {
                continue;
            }
            monitorActionBars.put(entry.getKey(), message);
        }
    }

    private void broadcastMonitorActionBars() {
        for (Map.Entry<UUID, String> entry : monitorActionBars.entrySet()) {
            UUID viewerUuid = entry.getKey();
            if (!monitoredTargets.containsKey(viewerUuid)) {
                monitorActionBars.remove(viewerUuid);
                continue;
            }
            plugin.getTaskScheduler().runPlayer(viewerUuid, () -> {
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer == null || !viewer.isOnline()) {
                    monitoredTargets.remove(viewerUuid);
                    monitorActionBars.remove(viewerUuid);
                    return;
                }
                String message = monitorActionBars.get(viewerUuid);
                if (message == null || message.isEmpty()) {
                    return;
                }
                viewer.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(message)
                );
            });
        }
    }

    private String formatMonitorActionBar(
        UUID monitoredPlayerUuid,
        String monitoredPlayerName,
        double probability,
        boolean detected,
        boolean globalModel,
        String timeText
    ) {
        String probabilityText = probability < 0D
            ? "?"
            : String.format(Locale.US, "%.3f", probability);
        String playerText = monitoredPlayerName == null || monitoredPlayerName.trim().isEmpty()
            ? monitoredPlayerUuid.toString()
            : monitoredPlayerName;
        String template = plugin
            .getConfigManager()
            .getMonitorActionBarFormat();
        return StringUtility.getString(
            (template == null ? "" : template)
                .replace("{probability}", probabilityText)
                .replace("{detected}", detected ? "True" : "False")
                .replace("{global}", globalModel ? "true" : "false")
                .replace("{time}", timeText == null ? "--:--:--" : timeText)
                .replace("{player}", playerText)
        );
    }

    private void enableAlertsOnJoinIfNeeded(UUID uuid) {
        if (uuid == null || isAlertsEnabled(uuid)) {
            return;
        }

        plugin.getTaskScheduler().runPlayer(uuid, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (!player.hasPermission("powerac.alerts-on-join")) {
                return;
            }

            setAlertsEnabled(uuid, true);
            player.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("alerts-enabled", "")
                )
            );
        });
    }

    public void clearAllViolationLevels() {
        for (PowerPlayer player : players.values()) {
            player.resetViolationLevels();
        }
    }

    public void reloadChecks() {
    }

    public int resolvePlayerLimit() {
        SocketClient socketClient = plugin.getSocketClient();
        if (socketClient == null) {
            return 15;
        }
        AuthInfo authInfo = socketClient.getSocketAuthInfo();
        Integer authLimit = authInfo != null ? authInfo.getPlayerLimit() : null;
        if (authLimit != null && authLimit.intValue() > 0) {
            return authLimit.intValue();
        }
        int socketLimit = socketClient.getPlayerLimit();
        return socketLimit > 0 ? socketLimit : 15;
    }

    public boolean isPlayerLimitReached() {
        return players.size() >= resolvePlayerLimit();
    }

    public boolean isTrackedOnline(UUID uuid) {
        return uuid != null && players.containsKey(uuid);
    }

    public List<SocketPlayerInfo> createSocketPlayerSnapshot() {
        List<SocketPlayerInfo> snapshot = new ArrayList<>();
        for (PowerPlayer powerPlayer : players.values()) {
            if (powerPlayer.getUuid() == null) {
                continue;
            }
            snapshot.add(
                new SocketPlayerInfo(
                    powerPlayer.getUuid().toString(),
                    powerPlayer.getName() == null ? "" : powerPlayer.getName()
                )
            );
        }
        return snapshot;
    }

    private void applyBrandData(PowerPlayer player, String brand, ClientVersion version) {
        if (player == null) {
            return;
        }

        if (brand != null && !brand.trim().isEmpty()) {
            player.setBrand(brand);
        }
        if (version != null) {
            player.setClientVersion(version);
        }

        if (plugin.isBlockSuspiciousBrands() && isSuspiciousBrand(player)) {
            disconnectForSuspiciousBrand(player);
        }
    }

    private boolean isSuspiciousBrand(PowerPlayer player) {
        if (player == null || player.getClientVersion() == null || player.getBrand() == null) {
            return false;
        }

        String brand = player.getBrand().toLowerCase(Locale.ROOT);
        ClientVersion version = player.getClientVersion();

        boolean suspiciousForge =
            brand.contains("forge") &&
            version.isNewerThanOrEquals(ClientVersion.V_1_18_2) &&
            version.isOlderThan(ClientVersion.V_1_19_4);

        boolean suspiciousVanilla =
            brand.contains("vanilla") &&
            version == ClientVersion.V_1_16_4;

        return suspiciousForge || suspiciousVanilla;
    }

    private void disconnectForSuspiciousBrand(PowerPlayer player) {
        if (player == null) {
            return;
        }
        User user = player.getUser();
        if (user == null) {
            return;
        }

        String kickMessage = plugin.getSuspiciousBrandKickMessage();
        if (kickMessage == null) {
            kickMessage = "";
        }
        String resolvedMessage = kickMessage
            .replace("{brand}", player.getBrand() == null ? "unknown" : player.getBrand())
            .replace(
                "{version}",
                player.getClientVersion() == null ? "unknown" : player.getClientVersion().toString()
            );

        kickUser(player, resolvedMessage);
    }

    private void kickUser(PowerPlayer player, String rawMessage) {
        if (player == null) {
            return;
        }
        User user = player.getUser();
        if (user == null) {
            return;
        }

        String message = StringUtility.getString(rawMessage == null ? "" : rawMessage);
        plugin.getTaskScheduler().runPlayer(player.getUuid(), () -> {
            Player bukkitPlayer = Bukkit.getPlayer(player.getUuid());
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.kickPlayer(message);
            }
        });

        user.sendMessage(message);
        user.closeConnection();
    }

    private PowerPlayer createPowerPlayer(UUID uuid, String name, User user, int entityId) {
        PowerPlayer created = new PowerPlayer(uuid, name);
        created.setUser(user);
        created.setEntityId(entityId);
        if (user != null) {
            created.setClientVersion(user.getClientVersion());
        }

        RotationModule rotationModule = new RotationModule(plugin, this, created);
        created.getModuleManager().addModule(rotationModule);

        created.addTracker(new ActionTracker(created));
        created.addTracker(new RotationTracker(created, batch -> {
            if (created.getRotationModule() != null) {
                created.getRotationModule().enqueueAiBatch(batch);
            }
        }));
        return created;
    }
}
