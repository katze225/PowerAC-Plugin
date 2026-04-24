package me.katze.powerac;

import co.aikar.commands.BukkitCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import me.katze.powerac.api.PowerACApi;
import me.katze.powerac.api.PowerACApiImpl;
import me.katze.powerac.command.PowerACCommand;
import me.katze.powerac.manager.ConfigManager;
import me.katze.powerac.manager.DiscordWebhookManager;
import me.katze.powerac.manager.FileLogManager;
import me.katze.powerac.manager.PerformanceManager;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.packet.BrandCapturePacket;
import me.katze.powerac.packet.ConnectionPacket;
import me.katze.powerac.packet.EspPacket;
import me.katze.powerac.packet.TrackerPacket;
import me.katze.powerac.scheduler.PlatformTaskScheduler;
import me.katze.powerac.scheduler.TaskHandle;
import me.katze.powerac.scheduler.TaskSchedulers;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.task.PerformanceTask;
import me.katze.powerac.task.ResetVLTask;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class PowerAC extends JavaPlugin {

    public final String primaryUrl = "wss://api.powerac.net/ws";

    private PacketEventsAPI<?> packetEvents;
    private PlayerManager playerManager;
    private BrandCapturePacket brandCapturePacket;
    private ConnectionPacket connectionPacket;
    private TrackerPacket trackerPacket;
    private EspPacket espPacket;
    private BukkitCommandManager commandManager;
    private PowerACApi api;
    private ConfigManager configManager;

    @Getter
    public static PowerAC instance;

    private TaskHandle vlClearTask = TaskHandle.NO_OP;
    private TaskHandle performanceTask = TaskHandle.NO_OP;
    private FileLogManager fileLogManager;
    private DiscordWebhookManager discordWebhookManager;
    private SocketClient socketClient;
    private String pluginVersion;
    private PerformanceManager performanceManager;
    private PlatformTaskScheduler taskScheduler;

    @Override
    public void onLoad() {
        packetEvents = SpigotPacketEventsBuilder.build(this);
        PacketEvents.setAPI(packetEvents);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new ConfigManager(this);

        fileLogManager = new FileLogManager(this);
        discordWebhookManager = new DiscordWebhookManager(this);
        performanceManager = new PerformanceManager(this);
        taskScheduler = TaskSchedulers.create(this);
        reloadSettings();
        pluginVersion = getDescription().getVersion();

        socketClient = new SocketClient(this);
        socketClient.connect();

        getLogger().info("by @core2k21 (tg)");
        getLogger().info("discord - https://discord.gg/zewRxfEANC");
        logMissingGrimAC();

        new Metrics(this, 26396);

        brandCapturePacket = new BrandCapturePacket();
        playerManager = new PlayerManager(this, brandCapturePacket);
        brandCapturePacket.setPlayerManager(playerManager);
        api = new PowerACApiImpl(this, playerManager);
        connectionPacket = new ConnectionPacket(this, playerManager);
        trackerPacket = new TrackerPacket(playerManager);
        espPacket = new EspPacket(this);

        PacketEvents.getAPI().getEventManager().registerListener(connectionPacket);
        PacketEvents.getAPI().getEventManager().registerListener(brandCapturePacket);
        PacketEvents.getAPI().getEventManager().registerListener(trackerPacket);
        PacketEvents.getAPI().getEventManager().registerListener(espPacket);
        PacketEvents.getAPI().init();

        commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new PowerACCommand(this, playerManager));

        reloadTasks();
    }

    @Override
    public void onDisable() {
        vlClearTask.cancel();
        vlClearTask = TaskHandle.NO_OP;
        performanceTask.cancel();
        performanceTask = TaskHandle.NO_OP;
        if (trackerPacket != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(trackerPacket);
        }
        if (espPacket != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(espPacket);
        }
        if (brandCapturePacket != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(brandCapturePacket);
        }
        if (connectionPacket != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(connectionPacket);
        }
        PacketEvents.getAPI().terminate();
        commandManager = null;
        api = null;
        fileLogManager = null;
        discordWebhookManager = null;
        if (playerManager != null) {
            playerManager.shutdown();
        }
        if (socketClient != null) {
            socketClient.shutdown();
            socketClient = null;
        }
    }

    private void logMissingGrimAC() {
        if (getServer().getPluginManager().getPlugin("GrimAC") != null) {
            return;
        }

        getLogger().warning("<--->");
        getLogger().warning("GrimAC was not found on this server.");
        getLogger().warning("Rotation checks will continue to work, but GrimAC is still recommended for movement/world protection.");
        getLogger().warning("GrimAC: https://modrinth.com/plugin/grimac");
        getLogger().warning("<--->");
    }

    private void reloadTasks() {
        if (playerManager == null) {
            return;
        }

        vlClearTask.cancel();
        vlClearTask = TaskHandle.NO_OP;
        performanceTask.cancel();
        performanceTask = TaskHandle.NO_OP;

        if (configManager.getVlClearIntervalSeconds() >= 0) {
            long intervalTicks = configManager.getVlClearIntervalSeconds() * 20L;
            vlClearTask = taskScheduler.runGlobalTimer(
                new ResetVLTask(playerManager),
                intervalTicks,
                intervalTicks
            );
        }
        if (performanceManager != null) {
            performanceTask = taskScheduler.runGlobalTimer(
                new PerformanceTask(performanceManager),
                100L,
                100L
            );
        }
    }

    public void reloadSettings() {
        configManager.reload();
        if (performanceManager != null) {
            performanceManager.reload();
        }

        if (fileLogManager != null) {
            fileLogManager.reload(
                configManager.isLogsEnabled(),
                configManager.getDeleteLogsAfterDays()
            );
        }
        if (discordWebhookManager != null) {
            discordWebhookManager.reload();
        }
        if (playerManager != null) {
            playerManager.reloadChecks();
        }

        reloadTasks();

        if (socketClient != null) {
            socketClient.reconnect();
        }
    }

    public String getPrefix() {
        return configManager.getPrefix();
    }

    public String getApiKey() {
        return configManager.getApiKey();
    }

    public boolean isConsoleAlerts() {
        return configManager.isConsoleAlerts();
    }

    public String getPlayerLimitKickMessage() {
        return configManager.getPlayerLimitKickMessage();
    }

    public boolean isBlockSuspiciousBrands() {
        return configManager.isBlockSuspiciousBrands();
    }

    public String getSuspiciousBrandKickMessage() {
        return configManager.getSuspiciousBrandKickMessage();
    }
}
