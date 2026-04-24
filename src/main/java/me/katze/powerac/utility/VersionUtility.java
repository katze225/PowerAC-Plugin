package me.katze.powerac.utility;

public final class VersionUtility {

    private VersionUtility() {}

    public static boolean matchesRelease(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return false;
        }
        if (normalizedLeft.equalsIgnoreCase(normalizedRight)) {
            return true;
        }
        return baseVersion(normalizedLeft).equalsIgnoreCase(baseVersion(normalizedRight));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String baseVersion(String value) {
        int suffixIndex = value.indexOf('-');
        return suffixIndex >= 0 ? value.substring(0, suffixIndex) : value;
    }
}
