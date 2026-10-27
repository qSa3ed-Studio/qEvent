package studio.q.qevent.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * <p>A new snapshot is built on every reload, so a running announcement keeps behaving the
 * way it was started and no field has to be re-read mid-flight.</p>
 */
public record PluginSettings(
        String language,
        boolean debug,
        AnnouncementSection announcement,
        TextSection title,
        TextSection subtitle,
        ActionBarSection actionBar,
        BossBarSection bossBar,
        SoundSection sounds,
        HeadSection head,
        AnimationSection animation,
        SkinSection skin,
        ResourcePackSection resourcePack,
        AdminSection admin
) {

    /** Section: {@code announcement}. */
    public record AnnouncementSection(
            int durationTicks,
            int cooldownSeconds,
            boolean queueEnabled,
            int queueMaxSize,
            int maxMessageLength,
            boolean broadcastToAnnouncer,
            String consoleName
    ) {
    }

    /**
     * Section: {@code title} and {@code subtitle}.
     *
     * <p>{@code packTemplate} is used instead of {@code template} for viewers who have
     * qEvent's artwork resource pack loaded, which is what lets the branded glyphs and font
     * appear without ever showing missing-glyph boxes to players who declined the pack.</p>
     */
    public record TextSection(
            boolean enabled,
            String template,
            String packTemplate,
            int fadeInTicks,
            int fadeOutTicks
    ) {
    }

    /** Section: {@code action-bar}. */
    public record ActionBarSection(boolean enabled, String template) {
    }

    /** Section: {@code boss-bar}. */
    public record BossBarSection(
            boolean enabled,
            String template,
            BossBar.Color color,
            BossBar.Overlay overlay,
            boolean showCountdown
    ) {
    }

    /** One entry of the {@code sounds.play} list. */
    public record SoundCue(String sound, float volume, float pitch, int delayTicks) {
    }

    /** Section: {@code sounds}. */
    public record SoundSection(boolean enabled, List<SoundCue> cues) {
    }

    /** Section: {@code head}. */
    public record HeadSection(
            boolean enabled,
            HeadMode mode,
            MissingSkinBehaviour missingSkin,
            double distance,
            double offsetX,
            double offsetY,
            float scale,
            ItemDisplay.ItemDisplayTransform itemTransform,
            Display.Billboard billboard,
            boolean followView,
            boolean fullBrightness,
            boolean glow
    ) {
    }

    /** Section: {@code animation}. */
    public record AnimationSection(int entranceTicks, int exitTicks, AnimationStyle style, double slideDistance) {
    }

    /** Section: {@code skin}. */
    public record SkinSection(
            boolean networkEnabled,
            int connectTimeoutMs,
            int readTimeoutMs,
            int cacheHours,
            String lookupUrlTemplate,
            String profileUrlTemplate,
            String consoleSkin
    ) {
    }

    /** Section: {@code resource-pack}. */
    public record ResourcePackSection(
            boolean enabled,
            String url,
            String sha1,
            boolean required,
            String prompt,
            boolean sendOnJoin,
            boolean fallbackWhenUnavailable
    ) {
    }

    /** Section: {@code admin}. */
    public record AdminSection(boolean bypassCooldown, boolean logAnnouncements) {
    }

    /** True when the floating head can be rendered at all, ignoring per-player failures. */
    public boolean rendersHead() {
        return head.enabled() && head.mode() != HeadMode.TEXT_ONLY;
    }

    /** Total lifetime of an announcement, guaranteed to leave room for both animations. */
    public int lifetimeTicks() {
        int minimum = animation.entranceTicks() + animation.exitTicks() + 10;
        return Math.max(minimum, announcement.durationTicks());
    }

    /**
     * Reads the configuration, clamping invalid values instead of failing to start.
     *
     * @param config the loaded configuration
     * @param warn   sink for validation warnings, usually the plugin logger
     */
    public static PluginSettings load(FileConfiguration config, Consumer<String> warn) {
        String language = config.getString("general.language", "en");

        var announcement = new AnnouncementSection(
                clamp(config.getInt("announcement.duration-ticks", 120), 20, 12_000,
                        "announcement.duration-ticks", warn),
                clamp(config.getInt("announcement.cooldown-seconds", 10), 0, 3_600,
                        "announcement.cooldown-seconds", warn),
                config.getBoolean("announcement.queue.enabled", true),
                clamp(config.getInt("announcement.queue.max-size", 3), 0, 100,
                        "announcement.queue.max-size", warn),
                clamp(config.getInt("announcement.max-message-length", 220), 1, 2_000,
                        "announcement.max-message-length", warn),
                config.getBoolean("announcement.broadcast-to-announcer", true),
                config.getString("announcement.console-name", "&bConsole")
        );

        var title = new TextSection(
                config.getBoolean("title.enabled", true),
                config.getString("title.template", "<gradient:#FFD166:#FF6B6B><bold>✦ EVENT ANNOUNCEMENT ✦</bold></gradient>"),
                config.getString("title.pack-template",
                        "<gradient:#FFD166:#FF6B6B><font:qevent:display>EVENT ANNOUNCEMENT</font></gradient>"),
                clamp(config.getInt("title.fade-in-ticks", 8), 0, 200, "title.fade-in-ticks", warn),
                clamp(config.getInt("title.fade-out-ticks", 14), 0, 200, "title.fade-out-ticks", warn)
        );

        var subtitle = new TextSection(
                config.getBoolean("subtitle.enabled", true),
                config.getString("subtitle.template", "<white>%message%</white>"),
                config.getString("subtitle.pack-template", ""),
                0,
                0
        );

        var actionBar = new ActionBarSection(
                config.getBoolean("action-bar.enabled", true),
                config.getString("action-bar.template", "&7Announced by &f%announcer%")
        );

        var bossBar = new BossBarSection(
                config.getBoolean("boss-bar.enabled", false),
                config.getString("boss-bar.template", "<gold>%message%</gold>"),
                enumValue(BossBar.Color.class, config.getString("boss-bar.color"), BossBar.Color.PURPLE,
                        "boss-bar.color", warn),
                enumValue(BossBar.Overlay.class, config.getString("boss-bar.overlay"), BossBar.Overlay.PROGRESS,
                        "boss-bar.overlay", warn),
                config.getBoolean("boss-bar.show-countdown", true)
        );

        var sounds = new SoundSection(
                config.getBoolean("sounds.enabled", true),
                readSounds(config, warn)
        );

        var head = new HeadSection(
                config.getBoolean("head.enabled", true),
                enumValue(HeadMode.class, config.getString("head.mode"), HeadMode.AUTO, "head.mode", warn),
                enumValue(MissingSkinBehaviour.class, config.getString("head.on-missing-skin"),
                        MissingSkinBehaviour.SKIP_HEAD, "head.on-missing-skin", warn),
                clamp(config.getDouble("head.distance", 2.2), 0.5, 16.0, "head.distance", warn),
                clamp(config.getDouble("head.offset-x", 0.0), -8.0, 8.0, "head.offset-x", warn),
                clamp(config.getDouble("head.offset-y", -0.42), -8.0, 8.0, "head.offset-y", warn),
                (float) clamp(config.getDouble("head.scale", 0.85), 0.05, 8.0, "head.scale", warn),
                enumValue(ItemDisplay.ItemDisplayTransform.class, config.getString("head.item-transform"),
                        ItemDisplay.ItemDisplayTransform.HEAD, "head.item-transform", warn),
                enumValue(Display.Billboard.class, config.getString("head.billboard"),
                        Display.Billboard.CENTER, "head.billboard", warn),
                config.getBoolean("head.follow-view", true),
                config.getBoolean("head.full-brightness", true),
                config.getBoolean("head.glow", false)
        );

        var animation = new AnimationSection(
                clamp(config.getInt("animation.entrance-ticks", 8), 0, 200, "animation.entrance-ticks", warn),
                clamp(config.getInt("animation.exit-ticks", 10), 0, 200, "animation.exit-ticks", warn),
                enumValue(AnimationStyle.class, config.getString("animation.style"), AnimationStyle.SCALE_SLIDE,
                        "animation.style", warn),
                clamp(config.getDouble("animation.slide-distance", 0.6), 0.0, 8.0, "animation.slide-distance", warn)
        );

        var skin = new SkinSection(
                config.getBoolean("skin.network.enabled", true),
                clamp(config.getInt("skin.network.connect-timeout-ms", 3000), 500, 30_000,
                        "skin.network.connect-timeout-ms", warn),
                clamp(config.getInt("skin.network.read-timeout-ms", 5000), 500, 30_000,
                        "skin.network.read-timeout-ms", warn),
                clamp(config.getInt("skin.network.cache-hours", 24), 0, 8_760, "skin.network.cache-hours", warn),
                config.getString("skin.network.api.lookup",
                        "https://api.mojang.com/users/profiles/minecraft/%name%"),
                config.getString("skin.network.api.profile",
                        "https://sessionserver.mojang.com/session/minecraft/profile/%uuid%"),
                config.getString("skin.console-skin", "")
        );

        var resourcePack = new ResourcePackSection(
                config.getBoolean("resource-pack.enabled", false),
                config.getString("resource-pack.url", ""),
                config.getString("resource-pack.sha1", ""),
                config.getBoolean("resource-pack.required", false),
                config.getString("resource-pack.prompt", "<gold>qEvent</gold> uses a small resource pack."),
                config.getBoolean("resource-pack.send-on-join", true),
                config.getBoolean("resource-pack.fallback-when-unavailable", true)
        );

        var admin = new AdminSection(
                config.getBoolean("admin.bypass-cooldown", true),
                config.getBoolean("admin.log-announcements", true)
        );

        if (resourcePack.enabled() && (resourcePack.url() == null || resourcePack.url().isBlank())) {
            warn.accept("resource-pack.enabled is true but resource-pack.url is empty; the pack will not be sent.");
        }
        return new PluginSettings(language, config.getBoolean("general.debug", false), announcement, title, subtitle,
                actionBar, bossBar, sounds, head, animation, skin, resourcePack, admin);
    }

    private static List<SoundCue> readSounds(FileConfiguration config, Consumer<String> warn) {
        List<SoundCue> cues = new ArrayList<>();
        List<?> raw = config.getList("sounds.play");
        if (raw == null) {
            return cues;
        }
        for (Object element : raw) {
            if (element instanceof ConfigurationSection section) {
                SoundCue cue = cueFrom(section.getString("sound", ""), section.getDouble("volume", 1.0),
                        section.getDouble("pitch", 1.0), section.getInt("delay-ticks", 0), warn);
                if (cue != null) {
                    cues.add(cue);
                }
            } else if (element instanceof Map<?, ?> map) {
                Object sound = map.get("sound");
                SoundCue cue = cueFrom(sound == null ? "" : String.valueOf(sound),
                        number(map.get("volume"), 1.0), number(map.get("pitch"), 1.0),
                        (int) number(map.get("delay-ticks"), 0.0), warn);
                if (cue != null) {
                    cues.add(cue);
                }
            } else if (element instanceof String sound && !sound.isBlank()) {
                cues.add(new SoundCue(sound, 1.0f, 1.0f, 0));
            } else {
                warn.accept("Ignoring an unrecognised sounds.play entry.");
            }
        }
        return List.copyOf(cues);
    }

    private static SoundCue cueFrom(String sound, double volume, double pitch, int delayTicks,
                                     Consumer<String> warn) {
        if (sound.isBlank()) {
            warn.accept("Ignoring a sounds.play entry without a 'sound' key.");
            return null;
        }
        return new SoundCue(sound,
                (float) clamp(volume, 0.0, 10.0, "sounds.play.volume", warn),
                (float) clamp(pitch, 0.0, 4.0, "sounds.play.pitch", warn),
                clamp(delayTicks, 0, 12_000, "sounds.play.delay-ticks", warn));
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int clamp(int value, int min, int max, String path, Consumer<String> warn) {
        if (value < min || value > max) {
            warn.accept("Value " + value + " for '" + path + "' is outside " + min + ".." + max
                    + " and was clamped.");
            return Math.min(max, Math.max(min, value));
        }
        return value;
    }

    private static double clamp(double value, double min, double max, String path, Consumer<String> warn) {
        if (value < min || value > max) {
            warn.accept("Value " + value + " for '" + path + "' is outside " + min + ".." + max
                    + " and was clamped.");
            return Math.min(max, Math.max(min, value));
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type,
            String raw,
            E fallback,
            String path,
            Consumer<String> warn
    ) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warn.accept("Unknown value '" + raw + "' for '" + path + "'; using " + fallback + ".");
            return fallback;
        }
    }
}
