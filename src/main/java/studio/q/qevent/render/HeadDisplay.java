package studio.q.qevent.render;

import org.bukkit.Location;

/**
 * A head that is visible to exactly one viewer.
 *
 * <p>The name intentionally avoids "hologram": this is a real player-head item rendered by
 * a display entity, not animated text.</p>
 */
public interface HeadDisplay {

    /** Used when the head cannot be rendered; every operation is a safe no-op. */
    HeadDisplay NONE = new HeadDisplay() {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void teleport(Location location) {
            // Nothing to move.
        }

        @Override
        public void setScale(float scale, int interpolationTicks) {
            // Nothing to resize.
        }

        @Override
        public void setVerticalOffset(float offset, int interpolationTicks) {
            // Nothing to move.
        }

        @Override
        public void remove() {
            // Nothing to remove.
        }
    };

    boolean isActive();

    /** Moves the head to a camera-relative anchor. */
    void teleport(Location location);

    /**
     * Sets the rendered size.
     *
     * @param interpolationTicks ticks the client should spend interpolating, 0 for an
     *                           immediate change
     */
    void setScale(float scale, int interpolationTicks);

    /** Moves the head within the viewer's view space, used by the slide animation. */
    void setVerticalOffset(float offset, int interpolationTicks);

    void remove();
}
