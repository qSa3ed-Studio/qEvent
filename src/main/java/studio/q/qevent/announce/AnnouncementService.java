package studio.q.qevent.announce;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import studio.q.qevent.QEventPlugin;
import studio.q.qevent.config.PluginSettings;
import studio.q.qevent.skin.SkinLookup;
import studio.q.qevent.skin.SkinResolver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Runs announcements: enforces the cooldown, keeps a bounded queue and drives the per-tick
 * updates of every active announcement.
 *
 * <p>At most one broadcast announcement runs at a time; previews are private to the player
 * who asked for them and may coexist.</p>
 */
public final class AnnouncementService {

    /** Result of asking for an announcement. */
    public enum Status {
        /** The announcement is on screen now. */
        STARTED,
        /** Queued behind the running announcement; {@code value} is the queue size. */
        QUEUED,
        /** The sender must wait; {@code value} is the remaining seconds. */
        COOLDOWN,
        /** A broadcast is running and queueing is disabled. */
        IN_PROGRESS,
        /** The queue is full and the request was dropped. */
        QUEUE_FULL
    }

    /** Outcome of a request; {@code value} depends on {@link Status}. */
    public record Outcome(Status status, int value) {
    }

    private record QueuedRequest(AnnouncementRequest request, CommandSender sender) {
    }

    private final QEventPlugin plugin;
    private final SkinResolver skins;
    private final Deque<QueuedRequest> queue = new ArrayDeque<>();
    private final Map<UUID, Announcement> previews = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    private volatile PluginSettings settings;
    private Announcement broadcast;
    private BukkitTask ticker;

    public AnnouncementService(QEventPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.skins = new SkinResolver(plugin, settings.skin());
    }

    /**
     * Applies new configuration. A running announcement is stopped because it was built from
     * the previous snapshot; stopping it is what keeps behaviour predictable after a reload.
     */
    public void applySettings(PluginSettings updated) {
        this.settings = updated;
        skins.applySettings(updated.skin());
        cancelAll();
    }

    public SkinResolver skins() {
        return skins;
    }

    /** Handles an announcement request, applying cooldown and queue rules. */
    public Outcome request(CommandSender sender, AnnouncementRequest request) {
        if (request.preview()) {
            startPreview(sender, request);
            return new Outcome(Status.STARTED, 1);
        }

        int remaining = cooldownRemaining(sender);
        if (remaining > 0) {
            return new Outcome(Status.COOLDOWN, remaining);
        }

        if (broadcast != null) {
            if (!settings.announcement().queueEnabled()) {
                return new Outcome(Status.IN_PROGRESS, 0);
            }
            if (queue.size() >= settings.announcement().queueMaxSize()) {
                return new Outcome(Status.QUEUE_FULL, queue.size());
            }
            queue.addLast(new QueuedRequest(request, sender));
            return new Outcome(Status.QUEUED, queue.size());
        }

        return new Outcome(Status.STARTED, start(sender, request));
    }

    /** Remaining cooldown in seconds for this sender, 0 when they may announce. */
    public int cooldownRemaining(CommandSender sender) {
        PluginSettings current = settings;
        if (current.announcement().cooldownSeconds() <= 0) {
            return 0;
        }
        if (current.admin().bypassCooldown() && sender.hasPermission("qevent.admin")) {
            return 0;
        }
        Long last = cooldowns.get(senderKey(sender));
        if (last == null) {
            return 0;
        }
        long elapsedMillis = System.currentTimeMillis() - last;
        long cooldownMillis = current.announcement().cooldownSeconds() * 1000L;
        if (elapsedMillis >= cooldownMillis) {
            return 0;
        }
        return (int) Math.ceil((cooldownMillis - elapsedMillis) / 1000.0);
    }

    private String senderKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console";
    }

    private int start(CommandSender sender, AnnouncementRequest request) {
        Announcement announcement = new Announcement(plugin, settings, request, sender);
        announcement.begin();
        broadcast = announcement;
        cooldowns.put(senderKey(sender), System.currentTimeMillis());
        if (settings.admin().logAnnouncements()) {
            plugin.getLogger().info("Announcement by " + request.announcerName() + " for "
                    + announcement.viewerCount() + " player(s): " + request.message());
        }
        resolveSkin(announcement, request);
        ensureTicker();
        return announcement.viewerCount();
    }

    private void startPreview(CommandSender sender, AnnouncementRequest request) {
        if (!(sender instanceof Player player)) {
            return;
        }
        Announcement previous = previews.remove(player.getUniqueId());
        if (previous != null) {
            previous.abort();
        }
        Announcement announcement = new Announcement(plugin, settings, request, sender);
        announcement.begin();
        previews.put(player.getUniqueId(), announcement);
        resolveSkin(announcement, request);
        ensureTicker();
    }

    private void resolveSkin(Announcement announcement, AnnouncementRequest request) {
        if (!settings.rendersHead()) {
            return;
        }
        String name = request.skinLookupName();
        if (name == null || name.isBlank()) {
            return;
        }
        skins.resolve(name, request.announcerId(), request.onlineAnnouncer())
                .thenAccept(lookup -> runOnMainThread(() -> {
                    if (!announcement.isFinished()) {
                        announcement.attachSkin(lookup);
                    }
                    logLookup(name, lookup);
                }));
    }

    private void logLookup(String name, SkinLookup lookup) {
        if (lookup.succeeded()) {
            plugin.debugLog("Skin for " + name + " resolved from " + lookup.reason() + ".");
            return;
        }
        String detail = lookup.detail() == null ? "" : " (" + lookup.detail() + ")";
        switch (lookup.reason()) {
            case RATE_LIMITED -> plugin.getLogger().warning(
                    "Mojang rate limited the skin lookup for " + name + "; no head was rendered." + detail);
            case FAILED -> plugin.getLogger().warning(
                    "The skin lookup for " + name + " failed; the announcement was shown without a head."
                            + detail);
            case NOT_FOUND -> plugin.getLogger().info(
                    "No Mojang profile exists for " + name + "; the announcement was shown without a head.");
            case DISABLED -> plugin.debugLog("Skin lookups are disabled; " + name + " has no head.");
            default -> {
            }
        }
    }

    private void runOnMainThread(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, action);
        } catch (IllegalStateException exception) {
            // The plugin is shutting down; nothing to update any more.
        }
    }

    private void ensureTicker() {
        if (ticker != null) {
            return;
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** Advances every active announcement. Called once per server tick. */
    void tick() {
        if (broadcast != null && !broadcast.tick()) {
            broadcast = null;
            startNextQueued();
        }

        Iterator<Map.Entry<UUID, Announcement>> iterator = previews.entrySet().iterator();
        while (iterator.hasNext()) {
            Announcement preview = iterator.next().getValue();
            if (!preview.tick()) {
                iterator.remove();
            }
        }

        if (broadcast == null && previews.isEmpty() && queue.isEmpty()) {
            stopTicker();
        }
    }

    private void startNextQueued() {
        QueuedRequest next = queue.pollFirst();
        if (next == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            queue.clear();
            return;
        }
        start(next.sender(), next.request());
        plugin.messages().send(next.sender(), "command.announce-sent",
                java.util.Map.of("viewers", Integer.toString(broadcast == null ? 0 : broadcast.viewerCount())));
    }

    /** Hides running announcement heads from a player who just joined. */
    public void onPlayerJoin(Player player) {
        if (broadcast != null) {
            broadcast.hideDisplaysFrom(player);
        }
    }

    /** Cleans up when a viewer or an announcer disconnects. */
    public void onPlayerQuit(Player player) {
        if (broadcast != null) {
            broadcast.onViewerQuit(player);
        }
        Announcement preview = previews.remove(player.getUniqueId());
        if (preview != null) {
            preview.abort();
        }
    }

    /** Stops everything that is running and clears the queue. */
    public void cancelAll() {
        if (broadcast != null) {
            broadcast.abort();
            broadcast = null;
        }
        for (Announcement preview : previews.values()) {
            preview.abort();
        }
        previews.clear();
        queue.clear();
        stopTicker();
    }

    /** Releases every resource owned by the service. */
    public void shutdown() {
        cancelAll();
        skins.close();
    }

    /** True when a broadcast announcement is currently on screen. */
    public boolean isBroadcasting() {
        return broadcast != null;
    }

    /** Number of requests waiting behind the running announcement. */
    public int queuedCount() {
        return queue.size();
    }
}
