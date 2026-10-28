package studio.q.qevent.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Applies one-time upgrades to previously generated qEvent configuration files. */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 2;
    public static final double HEAD_OFFSET_Y = 0.45;
    public static final double HEAD_SCALE = 0.8;
    public static final String ARTWORK_PACK_URL =
            "https://github.com/qSa3ed-Studio/qEvent/releases/download/v1.0.1/qEvent-ResourcePack-1.0.0.zip";
    public static final String ARTWORK_PACK_SHA1 = "b56a93891f1afd73c4321952296634ec777623ab";

    private ConfigMigrator() {
    }

    /** Describes whether migration ran and whether any shipped default values changed. */
    public record Result(boolean migrated, boolean defaultsUpdated) {
    }

    /**
     * Upgrades known stock values from qEvent 1.0.0/1.0.1. Values that differ from those
     * shipped defaults are treated as intentional server-owner choices and left untouched.
     */
    public static Result migrate(FileConfiguration config) {
        if (config.getInt("qevent-config-version", 0) >= CURRENT_VERSION) {
            return new Result(false, false);
        }

        boolean changed = false;
        double offset = config.getDouble("head.offset-y", -0.42);
        if (approximately(offset, -0.42) || approximately(offset, 1.2)) {
            config.set("head.offset-y", HEAD_OFFSET_Y);
            changed = true;
        }

        double scale = config.getDouble("head.scale", 0.85);
        if (approximately(scale, 0.85) || approximately(scale, 0.55)) {
            config.set("head.scale", HEAD_SCALE);
            changed = true;
        }

        String packUrl = config.getString("resource-pack.url", "");
        if (!config.getBoolean("resource-pack.enabled", false)
                && (packUrl == null || packUrl.isBlank())) {
            config.set("resource-pack.enabled", true);
            config.set("resource-pack.url", ARTWORK_PACK_URL);
            config.set("resource-pack.sha1", ARTWORK_PACK_SHA1);
            changed = true;
        }

        config.set("qevent-config-version", CURRENT_VERSION);
        return new Result(true, changed);
    }

    private static boolean approximately(double left, double right) {
        return Math.abs(left - right) < 0.0001;
    }
}
