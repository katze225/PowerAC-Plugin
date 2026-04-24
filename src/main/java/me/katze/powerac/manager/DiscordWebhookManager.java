package me.katze.powerac.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.katze.powerac.PowerAC;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class DiscordWebhookManager {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final PowerAC plugin;
    private final OkHttpClient httpClient = new OkHttpClient();

    private volatile boolean enabled;
    private volatile String url;
    private volatile int embedColor;
    private volatile String embedTitle;
    private volatile List<String> contentLines = Collections.emptyList();
    private volatile int sendEveryVl;

    public DiscordWebhookManager(PowerAC plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.getConfigManager().isDiscordEnabled();
        url = plugin.getConfigManager().getDiscordUrl();
        embedColor = parseColor(plugin.getConfigManager().getDiscordEmbedColor());
        embedTitle = plugin.getConfigManager().getDiscordEmbedTitle();

        List<String> configuredLines = plugin.getConfigManager().getDiscordContent();
        if (configuredLines == null || configuredLines.isEmpty()) {
            configuredLines = new ArrayList<>();
            configuredLines.add("Player: {player}");
            configuredLines.add("UUID: {uuid}");
            configuredLines.add("Module: {module}");
            configuredLines.add("Reason: {reason}");
            configuredLines.add("Probability: {probability}");
            configuredLines.add("Added VL: {added_vl}");
            configuredLines.add("Check VL: {vl}");
            configuredLines.add("Total VL: {total_vl}");
            configuredLines.add("Detection Count: {detection_count}");
        }
        contentLines = Collections.unmodifiableList(new ArrayList<>(configuredLines));
        sendEveryVl = plugin.getConfigManager().getDiscordSendEveryVl();
    }

    public void sendDetection(Map<String, String> placeholders, int totalVl) {
        if (!shouldSend(totalVl)) {
            return;
        }

        String webhookUrl = url;
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        String description = buildDescription(placeholders);
        if (description.isEmpty()) {
            return;
        }

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", applyPlaceholders(embedTitle, placeholders));
        embed.addProperty("description", description);
        embed.addProperty("color", embedColor);
        embeds.add(embed);
        payload.add("embeds", embeds);

        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(RequestBody.create(JSON, payload.toString()))
            .build();

        plugin.getTaskScheduler().runAsync(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning(
                        "Discord webhook returned HTTP " + response.code()
                    );
                }
            } catch (Exception exception) {
                plugin.getLogger().warning(
                    "Failed to send Discord webhook: " + exception.getMessage()
                );
            }
        });
    }

    private boolean shouldSend(int totalVl) {
        if (!enabled || totalVl <= 0) {
            return false;
        }
        return totalVl == 1 || sendEveryVl <= 1 || totalVl % sendEveryVl == 0;
    }

    private String buildDescription(Map<String, String> placeholders) {
        StringBuilder builder = new StringBuilder();
        for (String line : contentLines) {
            String formatted = applyPlaceholders(line, placeholders).trim();
            if (formatted.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(formatted);
        }
        return builder.toString();
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(
                "{" + entry.getKey() + "}",
                entry.getValue() == null ? "" : entry.getValue()
            );
        }
        return result;
    }

    private int parseColor(String rawColor) {
        if (rawColor == null) {
            return 0x00A4FB;
        }

        String normalized = rawColor.trim();
        if (normalized.isEmpty()) {
            return 0x00A4FB;
        }

        try {
            if (normalized.startsWith("#")) {
                return Integer.parseInt(normalized.substring(1), 16);
            }
            if (normalized.toLowerCase(Locale.ROOT).startsWith("0x")) {
                return Integer.parseInt(normalized.substring(2), 16);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning(
                "Invalid Discord embed color '" + normalized + "', using default."
            );
            return 0x00A4FB;
        }
    }
}
