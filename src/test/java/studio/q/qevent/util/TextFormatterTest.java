package studio.q.qevent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormatterTest {

    @Test
    @DisplayName("legacy colour codes become MiniMessage tags")
    void convertsLegacyColourCodes() {
        assertEquals("<green>Hello", TextFormatter.toMiniMessage("&aHello"));
        assertEquals("<gold><bold>Event", TextFormatter.toMiniMessage("&6&lEvent"));
        assertEquals("<reset>done", TextFormatter.toMiniMessage("&rdone"));
    }

    @Test
    @DisplayName("classic hex codes are converted")
    void convertsLegacyHexCodes() {
        assertEquals("<#FF6B6B>Go", TextFormatter.toMiniMessage("&#FF6B6BGo"));
    }

    @Test
    @DisplayName("plain text survives untouched")
    void leavesPlainTextAlone() {
        assertEquals("HALLOWEEN EVENT STARTED!", TextFormatter.toMiniMessage("HALLOWEEN EVENT STARTED!"));
        assertEquals("a & b", TextFormatter.toMiniMessage("a & b"), "an & that is not a code must stay");
        assertEquals("&z", TextFormatter.toMiniMessage("&z"), "unknown codes are not converted");
    }

    @Test
    @DisplayName("player supplied text cannot inject tags")
    void escapesPlayerSuppliedTags() {
        assertEquals("\\<red>sneaky", TextFormatter.escape("<red>sneaky"));
        assertEquals("plain", TextFormatter.escape("plain"));
        ComponentAssert.assertPlainText("<red>sneaky", TextFormatter.escape("<red>sneaky"));
    }

    @Test
    @DisplayName("placeholders are substituted before parsing")
    void substitutesPlaceholders() {
        String result = TextFormatter.substitute("%announcer% announced %message%",
                Map.of("announcer", "Notch", "message", "HALLOWEEN"));
        assertEquals("Notch announced HALLOWEEN", result);
    }

    @Test
    @DisplayName("placeholders the caller did not supply stay visible so typos are obvious")
    void leavesUnknownPlaceholdersVisible() {
        assertEquals(" %missing%", TextFormatter.substitute(" %missing%", Map.of()));
        assertEquals("done ", TextFormatter.substitute("done %empty%", Map.of("empty", "")));
    }

    @Test
    @DisplayName("visible length ignores formatting codes")
    void visibleLengthIgnoresFormatting() {
        assertEquals(TextFormatter.visibleLength("plain"), TextFormatter.visibleLength("&6&lplain"));
        assertTrue(TextFormatter.visibleLength("<#FF6B6B>x") == 1);
    }

    @Test
    @DisplayName("MiniMessage tags pass through and still parse")
    void passesMiniMessageThrough() {
        String converted = TextFormatter.toMiniMessage("<gradient:#FFD166:#FF6B6B>EVENT</gradient>");
        assertEquals("<gradient:#FFD166:#FF6B6B>EVENT</gradient>", converted);
        assertFalse(TextFormatter.plainText("<red>Hi").isBlank());
        assertEquals("Hi", TextFormatter.plainText("<red>Hi"));
    }

    @Test
    @DisplayName("null input never throws")
    void toleratesNullInput() {
        assertEquals("", TextFormatter.toMiniMessage(null));
        assertEquals("", TextFormatter.escape(null));
        assertEquals("", TextFormatter.plainText(""));
        assertEquals("", TextFormatter.substitute(null, Map.of("a", "b")));
    }
}