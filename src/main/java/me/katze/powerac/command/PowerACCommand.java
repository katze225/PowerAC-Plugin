package me.katze.powerac.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import java.util.Locale;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.katze.powerac.PowerAC;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.module.impl.RotationModule;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.utility.StringUtility;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("powerac|pac")
@RequiredArgsConstructor
public final class PowerACCommand extends BaseCommand {

    private final PowerAC plugin;
    private final PlayerManager playerManager;

    private boolean isNotAuthenticated(CommandSender sender) {
        if (!plugin.getSocketClient().isAuthenticated()) {
            sender.sendMessage(
                StringUtility.getString(
                    "&cPowerAC is not connected to the server. Please check your API key and connection."
                )
            );
            return true;
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        List<String> lines = plugin.getConfigManager().getMessageList("help");
        for (String line : lines) {
            sender.sendMessage(StringUtility.getString(line == null ? "" : line));
        }
    }

    @Default
    @Description("PowerAC help")
    public void onDefault(CommandSender sender) {
        if (isNotAuthenticated(sender)) {
            return;
        }
        sendHelp(sender);
    }

    @Subcommand("help")
    @Description("PowerAC help")
    public void onHelp(CommandSender sender) {
        if (isNotAuthenticated(sender)) {
            return;
        }
        sendHelp(sender);
    }

    @Subcommand("alerts")
    @CommandPermission("powerac.alerts")
    public void onAlerts(CommandSender sender) {
        if (isNotAuthenticated(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("only-player", "")
                )
            );
            return;
        }

        Player player = (Player) sender;
        boolean enabled = !playerManager.isAlertsEnabled(player.getUniqueId());
        playerManager.setAlertsEnabled(player.getUniqueId(), enabled);

        String messagePath = enabled ? "messages.alerts-enabled" : "messages.alerts-disabled";
        player.sendMessage(
            StringUtility.getString(
                plugin.getConfigManager().getMessage(
                    enabled ? "alerts-enabled" : "alerts-disabled",
                    ""
                )
            )
        );
    }

    @Subcommand("reload")
    @CommandPermission("powerac.reload")
    public void onReload(CommandSender sender) {
        plugin.reloadSettings();
        sender.sendMessage(
            StringUtility.getString(plugin.getConfigManager().getMessage("reload", ""))
        );
    }

    @Subcommand("status")
    @CommandPermission("powerac.status")
    public void onStatus(CommandSender sender) {
        boolean connected = plugin.getSocketClient().isAuthenticated();
        String message = plugin
            .getConfigManager()
            .getMessage(
                connected ? "socket-status-connected" : "socket-status-disconnected",
                connected
                    ? "{prefix} &aConnected to socket server."
                    : "{prefix} &cDisconnected from socket server."
            );
        sender.sendMessage(StringUtility.getString(message));
    }

    @Subcommand("train")
    @CommandPermission("powerac.train")
    public void onTrain(
        CommandSender sender,
        @Optional String firstArg,
        @Optional String secondArg,
        @Optional String thirdArg
    ) {
        if (isNotAuthenticated(sender)) {
            return;
        }

        String first = normalizeTrainArgument(firstArg);
        String second = normalizeTrainArgument(secondArg);
        String third = normalizeTrainArgument(thirdArg);
        String firstLower = toLowerCase(first);
        String secondLower = toLowerCase(second);
        String thirdLower = toLowerCase(third);
        if (first == null) {
            if (!(sender instanceof Player)) {
                sendTrainUsage(sender);
                return;
            }

            RotationModule selfRotationModule = getSenderRotationModule(sender);
            if (selfRotationModule == null) {
                sendOnlyPlayerMessage(sender);
                return;
            }
            if (selfRotationModule.stopTrainingSession()) {
                sendTrainMessage(sender, "stopped", "&aTraining session stopped.");
                return;
            }
            sendTrainUsage(sender);
            return;
        }

        if ("clear".equals(firstLower)) {
            if (second != null || third != null) {
                sendTrainClearUsage(sender);
                return;
            }

            RotationModule selfRotationModule = getSenderRotationModule(sender);
            if (selfRotationModule == null) {
                sendOnlyPlayerMessage(sender);
                return;
            }
            selfRotationModule.stopTrainingSession();
            selfRotationModule.clearTrainingData(sender);
            return;
        }

        if ("stop".equals(firstLower)) {
            if (third != null) {
                sendTrainStopUsage(sender);
                return;
            }

            RotationModule targetRotationModule = second == null
                ? getSenderRotationModule(sender)
                : getRotationModuleByName(sender, second);
            if (targetRotationModule == null) {
                if (second == null) {
                    sendTrainStopUsage(sender);
                }
                return;
            }
            if (targetRotationModule.stopTrainingSession()) {
                sendTrainMessage(sender, "stopped", "&aTraining session stopped.");
                return;
            }
            sendTrainMessage(sender, "not-running", "&cNo training session is currently running.");
            return;
        }

        if (isTrainingLabel(firstLower)) {
            if (third != null) {
                sendTrainUsage(sender);
                return;
            }

            RotationModule selfRotationModule = getSenderRotationModule(sender);
            if (selfRotationModule == null) {
                sendOnlyPlayerMessage(sender);
                return;
            }

            Boolean continuous = parseContinuous(sender, secondLower);
            if (continuous == null) {
                return;
            }

            if (selfRotationModule.stopTrainingSession()) {
                sendTrainMessage(sender, "stopped", "&aTraining session stopped.");
                return;
            }
            selfRotationModule.startTrainingSession(sender, parseLabel(firstLower), continuous.booleanValue());
            return;
        }

        RotationModule targetRotationModule = getRotationModuleByName(sender, first);
        if (targetRotationModule == null) {
            return;
        }
        if (!isTrainingLabel(secondLower)) {
            sendTrainUsage(sender);
            return;
        }

        Boolean continuous = parseContinuous(sender, thirdLower);
        if (continuous == null) {
            return;
        }

        if (targetRotationModule.stopTrainingSession()) {
            sendTrainMessage(sender, "stopped", "&aTraining session stopped.");
            return;
        }
        targetRotationModule.startTrainingSession(sender, parseLabel(secondLower), continuous.booleanValue());
    }

    private RotationModule getSenderRotationModule(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return null;
        }

        PowerPlayer powerPlayer = playerManager.get(((Player) sender).getUniqueId());
        if (powerPlayer == null) {
            return null;
        }
        return powerPlayer.getRotationModule();
    }

    private RotationModule getRotationModuleByName(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("player-not-found", "")
                )
            );
            return null;
        }

        PowerPlayer powerPlayer = playerManager.get(target.getUniqueId());
        if (powerPlayer == null || powerPlayer.getRotationModule() == null) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("player-not-found", "")
                )
            );
            return null;
        }
        return powerPlayer.getRotationModule();
    }

    private Boolean parseContinuous(CommandSender sender, String value) {
        if (value == null) {
            return Boolean.FALSE;
        }

        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }

        sendTrainMessage(sender, "invalid-continuous", "&cContinuous must be true or false.");
        return null;
    }

    private boolean isTrainingLabel(String value) {
        return "legit".equals(value) || "cheater".equals(value);
    }

    private int parseLabel(String value) {
        return "cheater".equals(value) ? 1 : 0;
    }

    private String normalizeTrainArgument(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String toLowerCase(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private void sendOnlyPlayerMessage(CommandSender sender) {
        sender.sendMessage(
            StringUtility.getString(
                plugin.getConfigManager().getMessage("only-player", "")
            )
        );
    }

    private void sendTrainUsage(CommandSender sender) {
        sendTrainMessage(
            sender,
            "usage",
            "&fUsage: &#00A4FB/powerac train <legit|cheater> [true|false] &7or &#00A4FB/powerac train <player> <legit|cheater> [true|false]"
        );
    }

    private void sendTrainStopUsage(CommandSender sender) {
        sendTrainMessage(
            sender,
            "stop-usage",
            "&fUsage: &#00A4FB/powerac train stop [player] &7- stop an active training session"
        );
    }

    private void sendTrainClearUsage(CommandSender sender) {
        sendTrainMessage(
            sender,
            "clear-usage",
            "&fUsage: &#00A4FB/powerac train clear &7- clear your trained model data"
        );
    }

    private void sendTrainMessage(CommandSender sender, String path, String fallback) {
        sender.sendMessage(
            StringUtility.getString(plugin.getConfigManager().getTrainMessage(path, fallback))
        );
    }

    @Subcommand("monitor")
    @CommandPermission("powerac.monitor")
    @CommandCompletion("@players")
    public void onMonitor(CommandSender sender, @Optional String playerName) {
        if (isNotAuthenticated(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("only-player", "")
                )
            );
            return;
        }

        Player viewer = (Player) sender;
        Player target = viewer;
        if (playerName != null && !playerName.trim().isEmpty()) {
            target = Bukkit.getPlayerExact(playerName);
            if (target == null) {
                sender.sendMessage(
                    StringUtility.getString(
                        plugin.getConfigManager().getMessage("player-not-found", "")
                    )
                );
                return;
            }
        }

        boolean enabled = playerManager.toggleMonitor(
            viewer.getUniqueId(),
            target.getUniqueId(),
            target.getName()
        );
        if (!enabled) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin
                        .getConfigManager()
                        .getMessage("monitor-disabled", "{prefix} &cMonitor disabled for &f{player}&c.")
                        .replace("{player}", target.getName())
                )
            );
            return;
        }

        sender.sendMessage(
            StringUtility.getString(
                plugin
                    .getConfigManager()
                    .getMessage("monitor-enabled", "{prefix} &aMonitor enabled for &f{player}&a.")
                    .replace("{player}", target.getName())
            )
        );
    }

    @Subcommand("player|profile")
    @CommandPermission("powerac.player")
    @CommandCompletion("@players")
    public void onPlayer(CommandSender sender, String playerName) {
        if (isNotAuthenticated(sender)) {
            return;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("player-not-found", "")
                )
            );
            return;
        }

        PowerPlayer powerPlayer = playerManager.get(target.getUniqueId());
        if (powerPlayer == null) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getMessage("player-not-found", "")
                )
            );
            return;
        }

        List<String> lines = plugin.getConfigManager().getMessageList("player-info");
        for (String line : lines) {
            String version = powerPlayer.getClientVersion() != null
                ? powerPlayer.getClientVersion().toString()
                : "unknown";
            String result = (line == null ? "" : line)
                .replace("{player}", powerPlayer.getName())
                .replace("{uuid}", powerPlayer.getUuid().toString())
                .replace("{vl}", Integer.toString(powerPlayer.getViolationLevel()))
                .replace("{version}", version)
                .replace("{brand}", powerPlayer.getBrand());
            sender.sendMessage(StringUtility.getString(result));
        }
    }
}
