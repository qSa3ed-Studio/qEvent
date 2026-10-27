package studio.q.qevent.announce;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import studio.q.qevent.QEventPlugin;
import studio.q.qevent.config.PluginSettings;
import studio.q.qevent.render.HeadDisplay;
import studio.q.qevent.render.HeadItems;
import studio.q.qevent.render.ItemDisplayHead;
import studio.q.qevent.render.Viewport;
import studio.q.qevent.skin.SkinLookup;
import studio.q.qevent.util.TextFormatter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One running announcement.
 *
 * <p>All viewers share the text content, but each viewer gets a private head entity, so the
 * head is always rendered close to its own camera and nobody else can see it.</p>
 *
 * <p>Instances are only ever touched from the server thread.</p>
 */
public final class Announcement {

    private final QEventPlugin plugin;
    private final PluginSettings settings;
    private final AnnouncementRequest request;
    private final CommandSender feedback;
    private final Map<UUID, ViewerSession> sessions = new LinkedHashMap<>();
    private final List<PluginSettings.SoundCue> cues;
    private final BossBar bossBar;

    private final int lifetime;
    private int elapsed;
    private boolean exiting;
    private boolean finished;
    private boolean headSpawnPossible;
    private boolean headSpawned;
    private boolean entranceApplied;
    private int headSpawnTick = -1;
    private SkinLookup skinLookup;

    public Announcement(QEventPlugin plugin, PluginSettings settings, AnnouncementRequest request,
                        CommandSender feedback) {
        this.plugin = plugin;
        this.settings = settings;
        this.request = request;
        this.feedback = feedback;
        this.lifetime = settings.lifetimeTicks();
        this.cues = new ArrayList<>(settings.sounds().cues());
        this.cues.sort(java.util.Comparator.comparingInt(PluginSettings.SoundCue::delayTicks));
        this.headSpawnPossible = settings.rendersHead() && ItemDisplayHead.isSupported();
        if (settings.rendersHead() && !ItemDisplayHead.isSupported()) {
            plugin.getLogger().warning("qEvent cannot render the announcer head on this server "
                    + "(display entities are unavailable); showing the text announcement only.");
        }
        this.bossBar = settings.bossBar().enabled()
                ? BossBar.bossBar(TextFormatter.parse(baseTemplate(settings.bossBar().template())), 1.0f,
                settings.bossBar().color(), settings.bossBar().overlay())
                : null;
    }

    /** Starts the announcement: text, sound cues and boss bar are delivered immediately. */
    public void begin() {
        Set<UUID> targets = resolveTargets();
        for (UUID viewerId : targets) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            ViewerSession session = new ViewerSession(viewerId);
            sessions.put(viewerId, session);
            showText(viewer);
            if (bossBar != null) {
                viewer.showBossBar(bossBar);
                session.bossBarShown = true;
            }
        }
        plugin.debugLog("Announcement started for " + sessions.size() + " viewer(s) by "
                + request.announcerName() + ".");
    }

    private Set<UUID> resolveTargets() {
        Set<UUID> targets = new LinkedHashSet<>();
        if (request.viewers() != null) {
            targets.addAll(request.viewers());
            return targets;
        }
        UUID announcer = request.onlineAnnouncer() == null ? null : request.onlineAnnouncer().getUniqueId();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!settings.announcement().broadcastToAnnouncer() && player.getUniqueId().equals(announcer)) {
                continue;
            }
            targets.add(player.getUniqueId());
        }
        return targets;
    }

    /**
     * Picks between the vanilla-safe template and the artwork pack template.
     *
     * <p>Only clients that reported the qEvent pack as fully loaded get the branded font;
     * everybody else keeps the fallback, so a declined or failed download never produces
     * missing-glyph boxes.</p>
     */
    private String templateFor(Player viewer, PluginSettings.TextSection section) {
        String packTemplate = section.packTemplate();
        if (packTemplate != null && !packTemplate.isBlank() && plugin.resourcePacks().hasArtwork(viewer)) {
            return packTemplate;
        }
        return section.template();
    }

    private void showText(Player viewer) {
        Map<String, String> placeholders = placeholdersFor(viewer);

        if (settings.title().enabled()) {
            Component title = TextFormatter.parse(templateFor(viewer, settings.title()), placeholders);
            Component subtitle = settings.subtitle().enabled()
                    ? TextFormatter.parse(templateFor(viewer, settings.subtitle()), placeholders)
                    : Component.empty();
            int fadeIn = settings.title().fadeInTicks();
            int fadeOut = Math.min(settings.title().fadeOutTicks(), Math.max(0, lifetime - fadeIn - 1));
            int stay = Math.max(1, lifetime - fadeIn - fadeOut);
            viewer.showTitle(Title.title(title, subtitle, Title.Times.times(
                    Duration.ofMillis(fadeIn * 50L),
                    Duration.ofMillis(stay * 50L),
                    Duration.ofMillis(fadeOut * 50L))));
        } else if (settings.subtitle().enabled()) {
            // Without the title, deliver the subtitle through the action bar so it is still visible.
            viewer.sendActionBar(TextFormatter.parse(templateFor(viewer, settings.subtitle()), placeholders));
        }

        if (settings.actionBar().enabled()) {
            viewer.sendActionBar(TextFormatter.parse(settings.actionBar().template(), placeholders));
        }
    }

    private Map<String, String> placeholdersFor(Player viewer) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("announcer", TextFormatter.escape(request.announcerName()));
        placeholders.put("message", request.message() == null ? "" : request.message());
        placeholders.put("headline", TextFormatter.escape(headline()));
        placeholders.put("duration", Integer.toString(lifetime / 20));
        placeholders.put("version", plugin.getPluginMeta().getVersion());
        placeholders.put("viewer", TextFormatter.escape(viewer == null ? "" : viewer.getName()));
        return placeholders;
    }

    private String baseTemplate(String template) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("announcer", TextFormatter.escape(request.announcerName()));
        placeholders.put("message", request.message() == null ? "" : request.message());
        placeholders.put("headline", TextFormatter.escape(headline()));
        placeholders.put("duration", Integer.toString(lifetime / 20));
        placeholders.put("version", plugin.getPluginMeta().getVersion());
        placeholders.put("viewer", "");
        return TextFormatter.substitute(template, placeholders);
    }

    private String headline() {
        String message = request.message() == null ? "" : request.message();
        int newline = message.indexOf('\n');
        String firstLine = newline >= 0 ? message.substring(0, newline) : message;
        return firstLine.length() > 48 ? firstLine.substring(0, 48) + "…" : firstLine;
    }

    /**
     * Advances the announcement by one tick.
     *
     * @return true while the announcement is still running
     */
    public boolean tick() {
        if (finished) {
            return false;
        }
        elapsed++;
        playDueSounds();
        updateBossBar();

        if (!exiting && elapsed >= lifetime - settings.animation().exitTicks()) {
            beginExit();
        }

        if (headSpawned) {
            if (!entranceApplied && elapsed > headSpawnTick) {
                applyEntrance();
            }
            if (!exiting && settings.head().followView()) {
                followViewers();
            }
        } else if (skinLookup != null && skinLookup.succeeded() && headSpawnPossible && !exiting) {
            spawnHeads();
        }

        if (elapsed >= lifetime) {
            cleanup();
            return false;
        }
        return true;
    }

    private void playDueSounds() {
        if (!settings.sounds().enabled() || cues.isEmpty()) {
            return;
        }
        for (PluginSettings.SoundCue cue : cues) {
            if (cue.delayTicks() == elapsed) {
                for (ViewerSession session : sessions.values()) {
                    Player viewer = Bukkit.getPlayer(session.viewerId);
                    if (viewer != null && viewer.isOnline()) {
                        viewer.playSound(soundOf(cue));
                    }
                }
            }
        }
    }

    private Sound soundOf(PluginSettings.SoundCue cue) {
        Key key = Key.key(cue.sound());
        return Sound.sound(key, Sound.Source.MASTER, cue.volume(), cue.pitch());
    }

    private void updateBossBar() {
        if (bossBar == null || !settings.bossBar().showCountdown()) {
            return;
        }
        float remaining = 1.0f - (elapsed / (float) lifetime);
        bossBar.progress(Math.max(0f, Math.min(1f, remaining)));
    }

    private void spawnHeads() {
        headSpawned = true;
        headSpawnTick = elapsed;
        Optional<ItemStack> item = HeadItems.create(skinLookup.profile().orElse(null));
        if (item.isEmpty()) {
            plugin.getLogger().warning("qEvent: the resolved skin of " + request.announcerName()
                    + " had no usable texture; continuing without a head.");
            return;
        }

        PluginSettings.AnimationSection animation = settings.animation();
        boolean usesScale = animation.style().usesScale();
        boolean usesSlide = animation.style().usesSlide();
        float initialScale = usesScale ? 0.0f : settings.head().scale();
        float initialOffset = usesSlide ? (float) -animation.slideDistance() : 0.0f;

        int rendered = 0;
        for (ViewerSession session : sessions.values()) {
            Player viewer = Bukkit.getPlayer(session.viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            Location anchor = Viewport.anchor(viewer, settings.head().distance(),
                    settings.head().offsetX(), settings.head().offsetY() + initialOffset);
            session.head = ItemDisplayHead.spawn(plugin, viewer, anchor, settings.head(), item.get(),
                    initialScale, initialOffset).orElse(HeadDisplay.NONE);
            if (session.head.isActive()) {
                rendered++;
            }
        }
        plugin.debugLog("Rendered the real head of " + skinLookup.profile().map(p -> p.name()).orElse("?")
                + " for " + rendered + " viewer(s) (source: " + skinLookup.reason() + ").");
    }

    private void applyEntrance() {
        entranceApplied = true;
        int ticks = settings.animation().entranceTicks();
        boolean usesScale = settings.animation().style().usesScale();
        boolean usesSlide = settings.animation().style().usesSlide();
        for (ViewerSession session : sessions.values()) {
            if (!session.head.isActive()) {
                continue;
            }
            if (usesSlide) {
                session.head.setVerticalOffset(0.0f, ticks);
            }
            session.head.setScale(settings.head().scale(), usesScale ? ticks : 0);
        }
    }

    private void beginExit() {
        exiting = true;
        int ticks = settings.animation().exitTicks();
        boolean usesScale = settings.animation().style().usesScale();
        boolean usesSlide = settings.animation().style().usesSlide();
        for (ViewerSession session : sessions.values()) {
            if (!session.head.isActive()) {
                continue;
            }
            if (usesScale) {
                session.head.setScale(0.0f, ticks);
            }
            if (usesSlide) {
                session.head.setVerticalOffset((float) -settings.animation().slideDistance(), ticks);
            }
            if (!usesScale && !usesSlide) {
                session.head.setScale(0.0f, 0);
            }
        }
    }

    private void followViewers() {
        for (ViewerSession session : sessions.values()) {
            if (!session.head.isActive()) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(session.viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            session.head.teleport(Viewport.anchor(viewer, settings.head().distance(),
                    settings.head().offsetX(), settings.head().offsetY()));
        }
    }

    /**
     * Supplies the resolved skin. Called once the lookup completes, which may be after the
     * announcement has already started; the head then joins in late rather than delaying the
     * whole announcement.
     */
    public void attachSkin(SkinLookup lookup) {
        this.skinLookup = lookup;
        if (!lookup.succeeded()) {
            notifySkinProblem(lookup.reason());
            return;
        }
        if (!headSpawnPossible) {
            this.skinLookup = null;
            plugin.debugLog("Head rendering is disabled or unsupported; showing text only.");
        }
    }

    private void notifySkinProblem(SkinLookup.Reason reason) {
        String name = request.skinLookupName();
        plugin.getLogger().warning("qEvent skipped the announcer head: skin lookup for '"
                + (name == null ? "?" : name) + "' did not succeed (" + reason + ").");
        Map<String, String> placeholders = Map.of("name", TextFormatter.escape(name == null ? "" : name));
        switch (reason) {
            case DISABLED -> sendFeedback("skin.network-disabled", placeholders);
            case RATE_LIMITED -> sendFeedback("skin.lookup-rate-limited", placeholders);
            case NOT_FOUND, FAILED -> sendFeedback("skin.missing", placeholders);
            default -> {
                // Nothing to report for the success reasons.
            }
        }
    }

    private void sendFeedback(String key, Map<String, String> placeholders) {
        if (feedback == null) {
            return;
        }
        if (!(feedback instanceof Player player) || player.isOnline()) {
            plugin.messages().sendRaw(feedback, key, placeholders);
        }
    }

    /** Removes a viewer who disconnected while the announcement was running. */
    public void onViewerQuit(Player player) {
        ViewerSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.head.remove();
            if (session.bossBarShown && bossBar != null) {
                player.hideBossBar(bossBar);
            }
        }
    }

    /** Hides this announcement's heads from a player who joined after it started. */
    public void hideDisplaysFrom(Player joiner) {
        for (ViewerSession session : sessions.values()) {
            if (!session.viewerId.equals(joiner.getUniqueId()) && session.head instanceof ItemDisplayHead head) {
                head.hideFrom(joiner);
            }
        }
    }

    /** Stops the announcement immediately and cleans up every entity. */
    public void abort() {
        if (finished) {
            return;
        }
        cleanup();
    }

    private void cleanup() {
        if (finished) {
            return;
        }
        finished = true;
        for (ViewerSession session : sessions.values()) {
            session.head.remove();
            if (session.bossBarShown && bossBar != null) {
                Player viewer = Bukkit.getPlayer(session.viewerId);
                if (viewer != null && viewer.isOnline()) {
                    viewer.hideBossBar(bossBar);
                }
            }
        }
        sessions.clear();
    }

    public boolean isFinished() {
        return finished;
    }

    public int viewerCount() {
        return sessions.size();
    }

    public AnnouncementRequest request() {
        return request;
    }

    /** Per-viewer state: the private head and whether the shared boss bar is shown. */
    private static final class ViewerSession {
        private final UUID viewerId;
        private HeadDisplay head = HeadDisplay.NONE;
        private boolean bossBarShown;

        private ViewerSession(UUID viewerId) {
            this.viewerId = viewerId;
        }
    }
}
