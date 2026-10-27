package studio.q.qevent.resourcepack;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import studio.q.qevent.QEventPlugin;
import studio.q.qevent.config.PluginSettings;
import studio.q.qevent.util.TextFormatter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers qEvent's optional artwork resource pack and tracks what each client did with it.
 *
 * <p>The announcement never depends on the pack. Players who decline it, fail to download it
 * or play on a client that cannot render the custom font simply get the vanilla-safe
 * template instead of the branded one - see {@link #hasArtwork(Player)}.</p>
 */
public final class ResourcePackService {

    /** What a client did with the offered pack. */
    public enum Status {
        /** Nothing was offered yet. */
        UNKNOWN,
        /** The offer was sent and no answer has arrived yet. */
        OFFERED,
        /** The client accepted and is downloading. */
        DOWNLOADING,
        /** The pack is loaded and its glyphs can be used. */
        LOADED,
        /** The player declined the pack. */
        DECLINED,
        /** The download or reload failed on the client. */
        FAILED
    }

    /**
     * Stable identifier for qEvent's pack. Reusing one UUID lets the client replace the pack
     * without extra prompts on 1.20.3+.
     */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("qEvent-artwork-pack".getBytes(StandardCharsets.UTF_8));

    private final QEventPlugin plugin;
    private final Map<UUID, Status> statuses = new ConcurrentHashMap<>();

    private volatile PluginSettings.ResourcePackSection settings;

    public ResourcePackService(QEventPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings.resourcePack();
    }

    public void applySettings(PluginSettings updated) {
        this.settings = updated.resourcePack();
        statuses.clear();
    }

    /** True when this player has the artwork pack fully loaded. */
    public boolean hasArtwork(Player player) {
        return settings.enabled() && statuses.get(player.getUniqueId()) == Status.LOADED;
    }

    /** True when the pack is configured and ready to be sent. */
    public boolean isConfigured() {
        return settings.enabled() && settings.url() != null && !settings.url().isBlank();
    }

    public Status statusOf(UUID playerId) {
        return statuses.getOrDefault(playerId, Status.UNKNOWN);
    }

    /** Offers the pack to a joining player when one is configured. */
    public void offer(Player player) {
        if (!settings.enabled() || !settings.sendOnJoin()) {
            return;
        }
        String url = settings.url();
        if (url == null || url.isBlank()) {
            return;
        }

        byte[] hash = parseSha1(settings.sha1());
        if (hash == null && settings.required()) {
            plugin.getLogger().warning("resource-pack.required is true but resource-pack.sha1 is missing or invalid; "
                    + "clients on 1.20.3+ cannot verify the pack and may refuse it.");
        }

        try {
            Component prompt = TextFormatter.parse(settings.prompt());
            player.setResourcePack(PACK_ID, url, hash, prompt, settings.required());
            statuses.put(player.getUniqueId(), Status.OFFERED);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not offer the resource pack to " + player.getName() + ": "
                    + throwable.getMessage());
            statuses.put(player.getUniqueId(), Status.FAILED);
        }
    }

    /** Updates the tracked state from a client status callback. */
    public void handleStatus(Player player, UUID packId, String statusName) {
        if (packId != null && !PACK_ID.equals(packId)) {
            // Another plugin's pack; not ours to track.
            return;
        }
        Status status = switch (statusName) {
            case "SUCCESSFULLY_LOADED" -> Status.LOADED;
            case "DECLINED" -> Status.DECLINED;
            case "FAILED_DOWNLOAD", "FAILED_RELOAD", "DISCARDED" -> Status.FAILED;
            case "ACCEPTED", "DOWNLOADED" -> Status.DOWNLOADING;
            default -> Status.UNKNOWN;
        };
        statuses.put(player.getUniqueId(), status);
        if (status == Status.DECLINED || status == Status.FAILED) {
            plugin.debugLog("Resource pack " + status.name().toLowerCase() + " for " + player.getName()
                    + "; announcements will use the fallback artwork.");
        }
    }

    /** Forgets a player who disconnected. */
    public void forget(UUID playerId) {
        statuses.remove(playerId);
    }

    /** Human readable summary used by {@code /event help} style diagnostics. */
    public String describe() {
        if (!settings.enabled()) {
            return "disabled";
        }
        if (!isConfigured()) {
            return "enabled but no url configured";
        }
        return "enabled (" + settings.url() + ", required=" + settings.required() + ")";
    }

    static byte[] parseSha1(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replace("-", "");
        if (cleaned.length() != 40) {
            return null;
        }
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            int high = Character.digit(cleaned.charAt(i * 2), 16);
            int low = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
