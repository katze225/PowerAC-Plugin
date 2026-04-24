package me.katze.powerac.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
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
        @Optional String labelRaw,
        @Optional String continuousRaw
    ) {
        if (isNotAuthenticated(sender)) {
            return;
        }

        PowerPlayer target = null;
        if (sender instanceof Player) {
            target = playerManager.get(((Player) sender).getUniqueId());
        }
        RotationModule rotationModule = target != null ? target.getRotationModule() : null;
        if (target == null || rotationModule == null) {
            sender.sendMessage(
                StringUtility.getString("&cThis command can only be used by an in-game player.")
            );
            return;
        }

        if (labelRaw == null) {
            if (rotationModule.stopTrainingSession()) {
                sender.sendMessage(
                    StringUtility.getString(
                        plugin.getConfigManager().getTrainMessage("stopped", "&aTraining session stopped.")
                    )
                );
                return;
            }
            sender.sendMessage(
                StringUtility.getString(
                    plugin
                        .getConfigManager()
                        .getTrainMessage(
                            "usage",
                            "&fUsage: &#00A4FB/powerac train <legit|cheater> [true|false] &7- send rotation samples to training"
                        )
                )
            );
            return;
        }

        String normalizedLabel = labelRaw.trim().toLowerCase();
        if ("clear".equals(normalizedLabel)) {
            rotationModule.stopTrainingSession();
            rotationModule.clearTrainingData(sender);
            return;
        }
        int label;
        if ("legit".equals(normalizedLabel)) {
            label = 0;
        } else if ("cheater".equals(normalizedLabel)) {
            label = 1;
        } else {
            sender.sendMessage(
                StringUtility.getString(
                    plugin
                        .getConfigManager()
                        .getTrainMessage("only-legit-or-cheater", "&cLabel must be legit or cheater.")
                )
            );
            return;
        }

        boolean continuous = false;
        if (continuousRaw != null) {
            String normalizedContinuous = continuousRaw.trim().toLowerCase();
            if ("true".equals(normalizedContinuous)) {
                continuous = true;
            } else if (!normalizedContinuous.isEmpty() && !"false".equals(normalizedContinuous)) {
                sender.sendMessage(
                    StringUtility.getString(
                        plugin
                            .getConfigManager()
                            .getTrainMessage("invalid-continuous", "&cContinuous must be true or false.")
                    )
                );
                return;
            }
        }

        if (rotationModule.stopTrainingSession()) {
            sender.sendMessage(
                StringUtility.getString(
                    plugin.getConfigManager().getTrainMessage("stopped", "&aTraining session stopped.")
                )
            );
            return;
        }

        rotationModule.startTrainingSession(sender, label, continuous);
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
