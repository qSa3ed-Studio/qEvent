package studio.q.qevent.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import studio.q.qevent.QEventPlugin;

/**
 * Keeps running announcements and resource pack state consistent with who is online.
 */
public final class PlayerConnectionListener implements Listener {

    private final QEventPlugin plugin;

    public PlayerConnectionListener(QEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // A player who joins in the middle of an announcement must not see the private heads.
        plugin.announcements().onPlayerJoin(event.getPlayer());
        plugin.resourcePacks().offer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.announcements().onPlayerQuit(event.getPlayer());
        plugin.resourcePacks().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        plugin.resourcePacks().handleStatus(event.getPlayer(), event.getID(), event.getStatus().name());
    }
}
