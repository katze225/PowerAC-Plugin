package me.katze.powerac.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.katze.powerac.PowerAC;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
@RequiredArgsConstructor
public final class ConfigManager {

    private final PowerAC plugin;

    private String prefix;
    private String apiKey;
    private boolean consoleAlerts;
    private boolean logsEnabled;
    private int deleteLogsAfterDays;
    private int vlClearIntervalSeconds;
    private String playerLimitKickMessage;
    private boolean blockSuspiciousBrands;
    private String suspiciousBrandKickMessage;
    private int maxViolations;
    private List<String> punishmentCommands = Collections.emptyList();
    private double minTpsToCheck;
    private boolean espHideEnchants;
    private boolean espHideDurability;
    private boolean espHideCount;
    private boolean espHideHealth;
    private boolean discordEnabled;
    private String discordUrl;
    private String discordEmbedColor;
    private String discordEmbedTitle;
    private List<String> discordContent = Collections.emptyList();
    private int discordSendEveryVl;
    private String monitorActionBarFormat;
    private boolean privateModelEnabled;
    private boolean privateModelAlsoCheckGlobal;

    public void reload() {
        plugin.reloadConfig();

        prefix = getString("settings.prefix", "");
        apiKey = getString("settings.apiKey", "YOUR_KEY_HERE");
        consoleAlerts = getBoolean("settings.console-alerts", true);
        playerLimitKickMessage = getString(
            "messages.player-limit-reached",
            getString("settings.messages.player-limit-reached", "")
        );
        blockSuspiciousBrands = getSettingBoolean(
            "settings.suspicious.brands.block",
            "settings.block-suspicious-brands",
            false
        );
        suspiciousBrandKickMessage = getString(
            "messages.suspicious-brand-kick",
            "&cSuspicious client brand detected."
        );

        logsEnabled = getBoolean("logs.enable", true);
        deleteLogsAfterDays = getInt("logs.delete-after-days", 14);
        vlClearIntervalSeconds = getSettingInt(
            "settings.violations.clear-interval-seconds",
            "settings.vl-clear-interval-seconds",
            1500
        );

        maxViolations = Math.max(
            0,
            getSettingInt("settings.violations.max-vl", "settings.maxVL", 4)
        );
        List<String> configuredPunishments = getSettingStringList(
            "settings.violations.punishment-commands",
            "settings.punishmentCommands"
        );
        if (configuredPunishments == null || configuredPunishments.isEmpty()) {
            configuredPunishments = Collections.singletonList("kick {player} Cheating");
        }
        punishmentCommands = Collections.unmodifiableList(new ArrayList<>(configuredPunishments));

        minTpsToCheck = Math.max(
            0D,
            getDouble("settings.performance.min-tps-to-check", 18.0D)
        );

        espHideEnchants = getBoolean("settings.esp.hide-enchants", true);
        espHideDurability = getBoolean("settings.esp.hide-durability", true);
        espHideCount = getBoolean("settings.esp.hide-count", true);
        espHideHealth = getBoolean("settings.esp.hide-health", true);

        discordEnabled = getBoolean("settings.discord.enabled", false);
        discordUrl = getString("settings.discord.url", "").trim();
        discordEmbedColor = getString("settings.discord.embed-color", "#00A4FB");
        discordEmbedTitle = getString("settings.discord.embed-title", "PowerAC Detection");
        List<String> configuredDiscordContent = getStringList("settings.discord.content");
        if (configuredDiscordContent == null || configuredDiscordContent.isEmpty()) {
            configuredDiscordContent = new ArrayList<>();
            configuredDiscordContent.add("Player: {player}");
            configuredDiscordContent.add("UUID: {uuid}");
            configuredDiscordContent.add("Module: {module}");
            configuredDiscordContent.add("Reason: {reason}");
            configuredDiscordContent.add("Probability: {probability}");
            configuredDiscordContent.add("Added VL: {added_vl}");
            configuredDiscordContent.add("Check VL: {vl}");
            configuredDiscordContent.add("Total VL: {total_vl}");
            configuredDiscordContent.add("Detection Count: {detection_count}");
        }
        discordContent = Collections.unmodifiableList(new ArrayList<>(configuredDiscordContent));
        discordSendEveryVl = Math.max(1, getInt("settings.discord.send-every-vl", 3));
        privateModelEnabled = getBoolean("settings.private-model.enabled", false);
        privateModelAlsoCheckGlobal = getBoolean(
            "settings.private-model.also-check-global",
            false
        );
        monitorActionBarFormat = getMessage(
            "monitor-actionbar",
            "&bprobability: &f{probability}&7, &bdetected: &f{detected}&7, &bg: &f{global}&7, &bt: &f{time}&7, &bp: &f{player}"
        );
    }

    public String getMessage(String path, String defaultValue) {
        return getString("messages." + path, defaultValue);
    }

    public List<String> getMessageList(String path) {
        return getStringList("messages." + path);
    }

    public String getTrainMessage(String path, String defaultValue) {
        return getString("messages.train." + path, defaultValue);
    }

    public String getAlertMessage() {
        return getMessage("alert", "");
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private String getString(String path, String defaultValue) {
        return config().getString(path, defaultValue);
    }

    private boolean getBoolean(String path, boolean defaultValue) {
        return config().getBoolean(path, defaultValue);
    }

    private int getInt(String path, int defaultValue) {
        return config().getInt(path, defaultValue);
    }

    private double getDouble(String path, double defaultValue) {
        return config().getDouble(path, defaultValue);
    }

    private List<String> getStringList(String path) {
        return config().getStringList(path);
    }

    private boolean getSettingBoolean(String newPath, String legacyPath, boolean defaultValue) {
        if (config().contains(newPath)) {
            return config().getBoolean(newPath, defaultValue);
        }
        return config().getBoolean(legacyPath, defaultValue);
    }

    private int getSettingInt(String newPath, String legacyPath, int defaultValue) {
        if (config().contains(newPath)) {
            return config().getInt(newPath, defaultValue);
        }
        return config().getInt(legacyPath, defaultValue);
    }

    private List<String> getSettingStringList(String newPath, String legacyPath) {
        if (config().contains(newPath)) {
            return config().getStringList(newPath);
        }
        return config().getStringList(legacyPath);
    }
}
