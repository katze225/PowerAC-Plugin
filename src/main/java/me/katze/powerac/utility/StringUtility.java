package me.katze.powerac.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import me.katze.powerac.PowerAC;
import org.bukkit.ChatColor;

@UtilityClass
public final class StringUtility {

    private static final Pattern HEX_PATTERN = Pattern.compile(
        "&#([A-Fa-f0-9]{6})"
    );
    private static final Pattern LEGACY_PATTERN = Pattern.compile(
            "(?i)(?:§|&)[0-9A-FK-ORX]"
    );
    private static PowerAC plugin = null;

    public static String getString(String input) {
        if (input == null || input.isEmpty()) return "";

        // replace prefix
        if (plugin == null) plugin = PowerAC.getInstance();
        String replaced = input.replace(
            "{prefix}",
                plugin.getPrefix()
        );

        // apple hex colors
        Matcher matcher = HEX_PATTERN.matcher(replaced);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00a7').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return LEGACY_PATTERN.matcher(input).replaceAll("");
    }
}
