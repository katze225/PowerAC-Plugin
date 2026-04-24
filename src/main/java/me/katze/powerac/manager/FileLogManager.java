package me.katze.powerac.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.Getter;
import me.katze.powerac.PowerAC;
import me.katze.powerac.player.PowerPlayer;

@Getter
public class FileLogManager {
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PowerAC plugin;
    private final Path logsDir;

    private boolean enabled;
    private int deleteAfterDays;
    private LocalDate lastCleanupDate;

    public FileLogManager(PowerAC plugin) {
        this.plugin = plugin;
        this.logsDir = plugin.getDataFolder().toPath().resolve("logs");
    }

    public synchronized void reload(boolean enabled, int deleteAfterDays) {
        this.enabled = enabled;
        this.deleteAfterDays = deleteAfterDays;
        if (!enabled) {
            return;
        }
        ensureLogsDirectory();
        cleanupOldLogs(true);
    }

    public synchronized void logDetection(
        PowerPlayer player,
        String module,
        String reason,
        double probability,
        int vl,
        int addedVl
    ) {
        if (!enabled) {
            return;
        }
        String probabilityValue =
            probability < 0
                ? "?"
                : String.format(Locale.US, "%.3f", probability);
        String line =
            formatPrefix("DETECT") +
            " player=" +
            safe(player.getName()) +
            " uuid=" +
            player.getUuid() +
            " module=" +
            safe(module) +
            " reason=" +
            safe(reason) +
            " probability=" +
            probabilityValue +
            " vl=" +
            vl +
            " added=" +
            addedVl;
        writeLine(line);
    }

    public synchronized void logPunishmentCommand(
        PowerPlayer player,
        String module,
        String reason,
        double probability,
        int vl,
        int totalVl,
        int maxVl,
        String command
    ) {
        if (!enabled) {
            return;
        }
        String probabilityValue =
            probability < 0
                ? "?"
                : String.format(Locale.US, "%.3f", probability);
        String line =
            formatPrefix("PUNISH_CMD") +
            " player=" +
            safe(player.getName()) +
            " uuid=" +
            player.getUuid() +
            " module=" +
            safe(module) +
            " reason=" +
            safe(reason) +
            " probability=" +
            probabilityValue +
            " vl=" +
            vl +
            " total_vl=" +
            totalVl +
            " max_vl=" +
            maxVl +
            " command=" +
            safe(command);
        writeLine(line);
    }

    private String formatPrefix(String type) {
        return "[" + LocalTime.now().format(TIME_FORMAT) + "] [" + type + "]";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void writeLine(String line) {
        ensureLogsDirectory();
        cleanupOldLogs(false);

        String fileName = LocalDate.now().format(DATE_FORMAT) + ".log";
        Path logPath = logsDir.resolve(fileName);
        String message = line + System.lineSeparator();

        try {
            Files.write(
                logPath,
                message.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            plugin
                .getLogger()
                .warning("Failed to write log file: " + exception.getMessage());
        }
    }

    private void ensureLogsDirectory() {
        try {
            Files.createDirectories(logsDir);
        } catch (IOException exception) {
            plugin
                .getLogger()
                .warning(
                    "Failed to create logs directory: " + exception.getMessage()
                );
        }
    }

    private void cleanupOldLogs(boolean force) {
        if (deleteAfterDays <= 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        if (!force && today.equals(lastCleanupDate)) {
            return;
        }
        lastCleanupDate = today;

        LocalDate threshold = today.minusDays(deleteAfterDays);
        try (Stream<Path> paths = Files.list(logsDir)) {
            paths
                .filter(Files::isRegularFile)
                .forEach(path -> cleanupFile(path, threshold));
        } catch (IOException exception) {
            plugin
                .getLogger()
                .warning(
                    "Failed to cleanup old logs: " + exception.getMessage()
                );
        }
    }

    private void cleanupFile(Path path, LocalDate threshold) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".log")) {
            return;
        }

        String datePart = name.substring(0, name.length() - 4);
        try {
            LocalDate fileDate = LocalDate.parse(datePart, DATE_FORMAT);
            if (fileDate.isBefore(threshold)) {
                Files.deleteIfExists(path);
            }
        } catch (DateTimeParseException | IOException ignored) {}
    }
}
