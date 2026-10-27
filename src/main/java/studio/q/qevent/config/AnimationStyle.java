package studio.q.qevent.config;

/**
 * Entrance and exit animation applied to the floating head.
 */
public enum AnimationStyle {
    /** No movement: the head appears at its final size immediately. */
    NONE,
    /** Grow from nothing to the configured scale. */
    SCALE,
    /** Slide in from below and slide out upwards again. */
    SLIDE,
    /** Grow while sliding into place, the default cinematic look. */
    SCALE_SLIDE;

    public boolean usesScale() {
        return this == SCALE || this == SCALE_SLIDE;
    }

    public boolean usesSlide() {
        return this == SLIDE || this == SCALE_SLIDE;
    }
}
