package studio.q.qevent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionTest {

    @Test
    @DisplayName("classic Paper version strings parse")
    void parsesClassicVersions() {
        assertEquals("1.20.6", ServerVersion.parse("1.20.6-R0.1-SNAPSHOT").toString());
        assertEquals("1.21.8", ServerVersion.parse("1.21.8-R0.1-SNAPSHOT").toString());
        assertEquals("1.19.4", ServerVersion.parse("1.19.4-R0.1-SNAPSHOT").toString());
    }

    @Test
    @DisplayName("date based version strings of the 26.x line parse")
    void parsesDateBasedVersions() {
        ServerVersion twentySix = ServerVersion.parse("26.3-R0.1-SNAPSHOT");
        assertEquals(26, twentySix.major());
        assertEquals(3, twentySix.minor());
        assertEquals("26.3", twentySix.toString());
    }

    @Test
    @DisplayName("comparison and support floor behave as documented")
    void comparesVersions() {
        assertTrue(ServerVersion.parse("1.20.5").isSupported());
        assertTrue(ServerVersion.parse("1.20.6").isSupported());
        assertTrue(ServerVersion.parse("26.3").isSupported());
        assertFalse(ServerVersion.parse("1.20.4").isSupported(),
                "1.20.4 runs on Java 17 and is outside the supported range");
        assertFalse(ServerVersion.parse("1.16.5").isSupported());
        assertTrue(ServerVersion.parse("1.21").isAtLeast(1, 20, 5));
        assertTrue(ServerVersion.parse("26.3").compareTo(ServerVersion.parse("1.21.11")) > 0);
    }

    @Test
    @DisplayName("nonsense input degrades instead of throwing")
    void toleratesGarbage() {
        assertEquals("0", ServerVersion.parse(null).toString());
        assertEquals("0", ServerVersion.parse("").toString());
        assertEquals("0", ServerVersion.parse("abc-def").toString());
    }
}