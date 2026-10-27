package studio.q.qevent.render;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Per-viewer entity visibility.
 *
 * <p>Every supported Paper release has {@link Player#hideEntity(Plugin, Entity)}, but the
 * capability is still probed once at runtime. If it is ever missing, qEvent degrades to the
 * text-only presentation instead of spawning a head that everybody in the area would see.</p>
 */
final class ViewVisibility {

    private static final Method HIDE_METHOD = findHideMethod();

    private static boolean warningLogged;

    private ViewVisibility() {
    }

    static boolean supportsPerViewerVisibility() {
        return HIDE_METHOD != null;
    }

    private static Method findHideMethod() {
        try {
            return Player.class.getMethod("hideEntity", Plugin.class, Entity.class);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    /** Hides an entity from one player, reporting a failure at most once per plugin lifetime. */
    static void hideFrom(Plugin plugin, Player player, Entity entity, Runnable onWarning) {
        if (HIDE_METHOD == null || !entity.isValid() || !player.isOnline()) {
            return;
        }
        try {
            HIDE_METHOD.invoke(player, plugin, entity);
        } catch (Throwable throwable) {
            if (!warningLogged) {
                warningLogged = true;
                plugin.getLogger().log(Level.WARNING,
                        "Could not hide an announcement entity from other players; "
                                + "there may be brief visual overlap for nearby players.", throwable);
                onWarning.run();
            }
        }
    }
}
