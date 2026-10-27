package studio.q.qevent.render;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Places the floating head at a fixed position in the viewer's field of view.
 *
 * <p>The anchor is rebuilt from the viewer's eyes and look direction every tick, which is
 * what keeps the head pinned to the same spot on screen while the player looks around.</p>
 */
public final class Viewport {

    private static final Vector WORLD_UP = new Vector(0, 1, 0);
    private static final double MIN_LENGTH_SQUARED = 1.0e-6;

    private Viewport() {
    }

    /**
     * @param viewer   the player who will see the head
     * @param distance blocks in front of the eyes
     * @param offsetX  blocks to the viewer's right
     * @param offsetY  blocks up in the viewer's view space (negative moves the head down)
     */
    public static Location anchor(Player viewer, double distance, double offsetX, double offsetY) {
        Location eyes = viewer.getEyeLocation();
        Vector forward = eyes.getDirection();
        if (forward.lengthSquared() < MIN_LENGTH_SQUARED) {
            forward = new Vector(0, 0, 1);
        }
        forward = forward.clone().normalize();

        // right = forward x worldUp; degenerate when looking straight up or down.
        Vector right = forward.clone().crossProduct(WORLD_UP);
        if (right.lengthSquared() < MIN_LENGTH_SQUARED) {
            right = new Vector(1, 0, 0);
        }
        right = right.clone().normalize();

        Vector up = right.clone().crossProduct(forward);
        if (up.lengthSquared() < MIN_LENGTH_SQUARED) {
            up = new Vector(0, 1, 0);
        }
        up = up.clone().normalize();

        return eyes.clone()
                .add(forward.multiply(distance))
                .add(right.multiply(offsetX))
                .add(up.multiply(offsetY));
    }
}
