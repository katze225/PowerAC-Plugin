package me.katze.powerac.manager;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import me.katze.powerac.api.event.PowerACAlertEvent;
import me.katze.powerac.api.event.PowerACDetectEvent;
import me.katze.powerac.module.Module;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.utility.StringUtility;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Getter
public final class ViolationManager {

    private final PowerPlayer player;

    public ViolationManager(PowerPlayer player) {
        this.player = player;
    }

    public void flag(Module module, double probability, String reason, boolean globalModel) {
        if (module == null) {
            return;
        }

        if (
            module.getPlugin().getPerformanceManager() != null &&
            module.getPlugin().getPerformanceManager().isChecksPausedByTps()
        ) {
            return;
        }

        module.getPlugin().getTaskScheduler().runGlobal(() -> {
            int addedVl = 1;
            int moduleVl = player.addViolation(addedVl);
            int totalVl = player.getViolationLevel();
            int detectionCount = player.incrementDetectionCount();
            String resolvedReason = reason == null ? "" : reason;

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName() == null ? "" : player.getName());
            placeholders.put("uuid", player.getUuid().toString());
            placeholders.put("module", module.getName());
            placeholders.put("reason", resolvedReason);
            placeholders.put("vl", Integer.toString(moduleVl));
            placeholders.put("added_vl", Integer.toString(addedVl));
            placeholders.put("total_vl", Integer.toString(totalVl));
            placeholders.put("detection_count", Integer.toString(detectionCount));
            placeholders.put(
                "probability",
                probability < 0 ? "?" : String.format(Locale.US, "%.3f", probability)
            );
            placeholders.put("global", globalModel ? "True" : "False");

            String formattedAlert = module.getPlugin().getConfigManager().getAlertMessage();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formattedAlert = formattedAlert.replace(
                    "{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue()
                );
            }
            formattedAlert = StringUtility.getString(formattedAlert);
            final String alertMessage = formattedAlert;

            Bukkit.getPluginManager().callEvent(
                new PowerACDetectEvent(
                    player.getName() == null ? "" : player.getName(),
                    player.getUuid().toString(),
                    module.getName(),
                    resolvedReason,
                    probability,
                    moduleVl,
                    addedVl,
                    detectionCount
                )
            );

            if (module.getPlugin().isConsoleAlerts()) {
                Bukkit.getConsoleSender().sendMessage(alertMessage);
            }

            List<UUID> recipients = new ArrayList<UUID>(module.getPlayerManager().getAlertsEnabled());
            for (UUID recipientId : recipients) {
                module.getPlugin().getTaskScheduler().runPlayer(recipientId, () -> {
                    Player online = Bukkit.getPlayer(recipientId);
                    if (online == null || !online.isOnline()) {
                        return;
                    }
                    if (!online.hasPermission("powerac.alerts")) {
                        return;
                    }
                    online.sendMessage(alertMessage);
                });
            }

            Bukkit.getPluginManager().callEvent(
                new PowerACAlertEvent(
                    player.getName() == null ? "" : player.getName(),
                    player.getUuid().toString(),
                    module.getName(),
                    resolvedReason,
                    alertMessage,
                    recipients.size(),
                    module.getPlugin().isConsoleAlerts()
                )
            );

            if (module.getPlugin().getFileLogManager() != null) {
                module.getPlugin().getFileLogManager().logDetection(
                    player,
                    module.getName(),
                    resolvedReason,
                    probability,
                    moduleVl,
                    addedVl
                );
            }

            if (module.getPlugin().getDiscordWebhookManager() != null) {
                module.getPlugin().getDiscordWebhookManager().sendDetection(placeholders, totalVl);
            }

            int maxViolations = module.getPlugin().getConfigManager().getMaxViolations();
            if (maxViolations > 0 && totalVl >= maxViolations) {
                player.resetViolationLevels();
                executePunishmentCommandsAsync(
                    module,
                    placeholders,
                    resolvedReason,
                    probability,
                    moduleVl,
                    totalVl,
                    maxViolations
                );
            }
        });
    }

    private void executePunishmentCommandsAsync(
        Module module,
        Map<String, String> placeholders,
        String reason,
        double probability,
        int moduleVl,
        int totalVl,
        int maxViolations
    ) {
        List<String> commands = module.getPlugin().getConfigManager().getPunishmentCommands();
        if (commands == null || commands.isEmpty()) {
            return;
        }

        Map<String, String> commandPlaceholders = new HashMap<>(placeholders);
        commandPlaceholders.put("total_vl", Integer.toString(totalVl));
        commandPlaceholders.put("max_vl", Integer.toString(maxViolations));

        module.getPlugin().getTaskScheduler().runAsync(() -> {
            for (String rawCommand : commands) {
                if (rawCommand == null) {
                    continue;
                }

                String command = rawCommand;
                for (Map.Entry<String, String> entry : commandPlaceholders.entrySet()) {
                    command = command.replace(
                        "{" + entry.getKey() + "}",
                        entry.getValue() == null ? "" : entry.getValue()
                    );
                }
                command = command.trim();
                if (command.isEmpty()) {
                    continue;
                }

                if (module.getPlugin().getFileLogManager() != null) {
                    module.getPlugin().getFileLogManager().logPunishmentCommand(
                        player,
                        module.getName(),
                        reason,
                        probability,
                        moduleVl,
                        totalVl,
                        maxViolations,
                        command
                    );
                }

                String finalCommand = command;
                module.getPlugin().getTaskScheduler().runGlobal(
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
                );
            }
        });
    }
}
