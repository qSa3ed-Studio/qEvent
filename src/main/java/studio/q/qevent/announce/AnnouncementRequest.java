package studio.q.qevent.announce;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Everything needed to display one announcement.
 *
 * @param announcerName   name shown in {@code %announcer%}
 * @param announcerId     UUID used for the skin lookup, may be null
 * @param onlineAnnouncer the connected announcer, may be null for console or offline senders
 * @param skinName        player name to resolve the head from, may be null to use the announcer
 * @param titleTemplate   overrides {@code title.template}, may be null
 * @param subtitleTemplate overrides {@code subtitle.template}, may be null
 * @param actionBarTemplate overrides {@code action-bar.template}, may be null
 * @param message         the exact message supplied by the administrator
 * @param viewers         explicit viewer set, null broadcasts to every online player
 * @param preview         true when only the requester should see it
 */
public record AnnouncementRequest(
        String announcerName,
        UUID announcerId,
        Player onlineAnnouncer,
        String skinName,
        String titleTemplate,
        String subtitleTemplate,
        String actionBarTemplate,
        String message,
        Set<UUID> viewers,
        boolean preview
) {

    /** Broadcast request announced by a player, using that player's own head. */
    public static AnnouncementRequest ofPlayer(Player announcer, String message) {
        return new AnnouncementRequest(announcer.getName(), announcer.getUniqueId(), announcer, announcer.getName(),
                null, null, null, message, null, false);
    }

    /** Broadcast request announced with another player's head. */
    public static AnnouncementRequest ofTarget(
            String announcerName,
            UUID announcerId,
            Player onlineAnnouncer,
            String message
    ) {
        return new AnnouncementRequest(announcerName, announcerId, onlineAnnouncer, announcerName,
                null, null, null, message, null, false);
    }

    /** Broadcast request from the server console. */
    public static AnnouncementRequest ofConsole(String consoleName, String skinName, String message) {
        return new AnnouncementRequest(consoleName, null, null, skinName, null, null, null, message, null, false);
    }

    /** Custom title and subtitle instead of the configured announcement layout. */
    public AnnouncementRequest withTemplates(String title, String subtitle, String actionBar) {
        return new AnnouncementRequest(announcerName, announcerId, onlineAnnouncer, skinName,
                title, subtitle, actionBar, message, viewers, preview);
    }

    /** Restricts the announcement to a single viewer, used by {@code /event preview}. */
    public AnnouncementRequest forViewerOnly(UUID viewerId) {
        return new AnnouncementRequest(announcerName, announcerId, onlineAnnouncer, skinName,
                titleTemplate, subtitleTemplate, actionBarTemplate, message, Set.of(viewerId), true);
    }

    /**
     * The player name that should be used for the skin lookup.
     *
     * <p>Empty means "do not look anything up": that is the case for announcements from the
     * console without a configured {@code skin.console-skin}, which have no head at all.</p>
     */
    public String skinLookupName() {
        if (skinName != null && !skinName.isBlank()) {
            return skinName;
        }
        return onlineAnnouncer != null ? announcerName : "";
    }
}
