package studio.q.qevent.util;

import org.bukkit.Bukkit;

import java.util.regex.Pattern;

/**
 * Minecraft server version, parsed from {@link Bukkit#getBukkitVersion()}.
 *
 * <p>qEvent supports every Paper release whose API is a superset of the Paper 1.20.6 API it
 * is compiled against. Version parsing is intentionally tolerant so that the date based
 * versioning used by recent releases (for example {@code 26.3-R0.1-SNAPSHOT}) is handled
 * exactly like the classic {@code 1.21.8-R0.1-SNAPSHOT} form.</p>
 */
public record ServerVersion(int major, int minor, int patch) implements Comparable<ServerVersion> {

    private static final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    public static ServerVersion parse(String raw) {
        if (raw == null) {
            return new ServerVersion(0, 0, 0);
        }
        var matcher = VERSION.matcher(raw);
        if (!matcher.find()) {
            return new ServerVersion(0, 0, 0);
        }
        return new ServerVersion(
                parseInt(matcher.group(1)),
                parseInt(matcher.group(2)),
                parseInt(matcher.group(3))
        );
    }

    public static ServerVersion current() {
        return parse(Bukkit.getBukkitVersion());
    }

    private static int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(new ServerVersion(major, minor, patch)) >= 0;
    }

    /** True when this version is at or above the API qEvent was compiled against. */
    public boolean isSupported() {
        return isAtLeast(1, 20, 5);
    }

    @Override
    public int compareTo(ServerVersion other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append(major);
        if (minor > 0 || patch > 0) {
            builder.append('.').append(minor);
        }
        if (patch > 0) {
            builder.append('.').append(patch);
        }
        return builder.toString();
    }
}
