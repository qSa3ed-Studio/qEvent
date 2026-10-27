package studio.q.qevent.render;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import studio.q.qevent.config.PluginSettings;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Renders the announcer's head as a genuine player-head item on a display entity.
 *
 * <p>Paper and Bukkit have no API that draws an arbitrary image inside a title or HUD
 * element, and a resource pack can never contain another player's skin. The only vanilla
 * mechanism that shows a real, per-player skin is the actual item model, so qEvent spawns
 * one item display per viewer:</p>
 *
 * <ul>
 *   <li>the item is a {@code player_head} carrying the announcer's signed textures;</li>
 *   <li>{@code Billboard.CENTER} keeps it facing the viewer;</li>
 *   <li>the entity is hidden from everybody except its viewer with
 *       {@link Player#hideEntity(Plugin, Entity)}, so each player gets a private head;</li>
 *   <li>it is teleported to a camera-relative anchor every tick, which pins it to a fixed
 *       position on screen.</li>
 * </ul>
 */
public final class ItemDisplayHead implements HeadDisplay {

    /** Ticks the client spends interpolating a teleport; 1 keeps the head glued to the camera. */
    private static final int TELEPORT_INTERPOLATION_TICKS = 1;

    private final Plugin plugin;
    private final ItemDisplay entity;
    private final UUID viewerId;
    private final Vector3f translation = new Vector3f(0f, 0f, 0f);
    private float scale;
    private boolean active = true;
    private boolean warnedAboutVisibility;

    private ItemDisplayHead(Plugin plugin, ItemDisplay entity, UUID viewerId, float scale) {
        this.plugin = plugin;
        this.entity = entity;
        this.viewerId = viewerId;
        this.scale = scale;
    }

    /** True when this runtime can render per-viewer heads. */
    public static boolean isSupported() {
        if (!ViewVisibility.supportsPerViewerVisibility()) {
            return false;
        }
        try {
            Class.forName("org.bukkit.entity.ItemDisplay");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    /**
     * Spawns a head for one viewer.
     *
     * @param item          the player head item, already carrying the real skin
     * @param initialScale  starting scale, normally 0 for the entrance animation
     * @param initialOffset starting offset in view space, used by the slide animation
     */
    public static Optional<HeadDisplay> spawn(
            Plugin plugin,
            Player viewer,
            Location location,
            PluginSettings.HeadSection settings,
            ItemStack item,
            float initialScale,
            float initialOffset
    ) {
        if (!isSupported()) {
            return Optional.empty();
        }
        ItemDisplay display;
        try {
            display = viewer.getWorld().spawn(location, ItemDisplay.class);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Could not spawn the announcement head entity.", throwable);
            return Optional.empty();
        }
        if (display == null) {
            return Optional.empty();
        }

        try {
            display.setItemStack(item);
            display.setItemDisplayTransform(settings.itemTransform());
            display.setBillboard(settings.billboard());
            display.setTeleportDuration(TELEPORT_INTERPOLATION_TICKS);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setSilent(true);
            if (settings.fullBrightness()) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
            if (settings.glow()) {
                display.setGlowing(true);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Could not configure the announcement head entity.", throwable);
            display.remove();
            return Optional.empty();
        }

        ItemDisplayHead head = new ItemDisplayHead(plugin, display, viewer.getUniqueId(), initialScale);
        head.translation.set(0f, initialOffset, 0f);
        head.applyTransformation(0);
        head.hideFromEverybodyElse(viewer);
        return Optional.of(head);
    }

    private void hideFromEverybodyElse(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                ViewVisibility.hideFrom(plugin, other, entity, () -> warnedAboutVisibility = true);
            }
        }
    }

    /** Hides this head from a player who joined after the announcement started. */
    public void hideFrom(Player player) {
        if (!player.getUniqueId().equals(viewerId)) {
            ViewVisibility.hideFrom(plugin, player, entity, () -> warnedAboutVisibility = true);
        }
    }

    /** The only player who can see this head. */
    public UUID viewerId() {
        return viewerId;
    }

    @Override
    public boolean isActive() {
        return active && entity.isValid();
    }

    @Override
    public void teleport(Location location) {
        if (!isActive()) {
            return;
        }
        try {
            entity.teleport(location);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Could not follow the viewer with the head entity.", throwable);
        }
    }

    @Override
    public void setScale(float scale, int interpolationTicks) {
        if (!isActive()) {
            return;
        }
        this.scale = scale;
        applyTransformation(interpolationTicks);
    }

    @Override
    public void setVerticalOffset(float offset, int interpolationTicks) {
        if (!isActive()) {
            return;
        }
        this.translation.y = offset;
        applyTransformation(interpolationTicks);
    }

    private void applyTransformation(int interpolationTicks) {
        entity.setInterpolationDelay(0);
        entity.setInterpolationDuration(Math.max(0, interpolationTicks));
        entity.setTransformation(new Transformation(
                new Vector3f(translation),
                new Quaternionf(),
                new Vector3f(scale),
                new Quaternionf()
        ));
    }

    @Override
    public void remove() {
        active = false;
        try {
            if (entity.isValid()) {
                entity.remove();
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Could not remove the head entity.", throwable);
        }
    }
}
