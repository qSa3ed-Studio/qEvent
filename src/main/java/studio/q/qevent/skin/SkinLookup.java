package studio.q.qevent.skin;

import java.util.Optional;

/**
 * Outcome of a skin resolution, including why it failed so the plugin can tell an
 * administrator something more useful than "no head".
 */
public record SkinLookup(Optional<SkinProfile> profile, Reason reason, String detail) {

    public enum Reason {
        /** The player is online and the server already knows the skin. */
        ONLINE_PROFILE,
        /** Served from the local skin cache. */
        CACHE,
        /** Fetched from the configured skin API. */
        NETWORK,
        /** The API does not know this player name. */
        NOT_FOUND,
        /** The API asked us to slow down. */
        RATE_LIMITED,
        /** Network error, timeout or malformed response. */
        FAILED,
        /** Skin lookups are disabled in the configuration. */
        DISABLED
    }

    public boolean succeeded() {
        return profile.isPresent();
    }

    public static SkinLookup found(SkinProfile profile, Reason reason) {
        return new SkinLookup(Optional.of(profile), reason, null);
    }

    public static SkinLookup missing(Reason reason) {
        return new SkinLookup(Optional.empty(), reason, null);
    }

    public static SkinLookup missing(Reason reason, String detail) {
        return new SkinLookup(Optional.empty(), reason, detail);
    }
}
