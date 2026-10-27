package studio.q.qevent.config;

/**
 * What qEvent does when the announcer's real skin cannot be resolved.
 *
 * <p>A generic Steve or Alex head is deliberately never substituted: showing the wrong
 * skin is worse than showing no head at all.</p>
 */
public enum MissingSkinBehaviour {
    /** Keep the cinematic text announcement and skip the head entity. */
    SKIP_HEAD,
    /** Same behaviour, but the reason is always logged so administrators can diagnose it. */
    TEXT_ONLY
}
