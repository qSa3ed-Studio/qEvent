package studio.q.qevent.config;

/**
 * How qEvent presents a cinematic announcement on a viewer's screen.
 */
public enum HeadMode {
    /** Render the 3D head whenever the server supports it, otherwise fall back to text only. */
    AUTO,
    /** Always attempt to render the head. */
    ENTITY,
    /** Never spawn an entity; use the text-only presentation. */
    TEXT_ONLY
}
