package studio.q.qevent.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import studio.q.qevent.announce.AnnouncementRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSettingsTest {

    private PluginSettings load(String yaml, List<String> warnings) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new java.io.StringReader(
                yaml));
        return PluginSettings.load(configuration, warnings::add);
    }

    @Test
    @DisplayName("an empty configuration falls back to the documented defaults")
    void defaultsAreApplied() {
        List<String> warnings = new ArrayList<>();
        PluginSettings settings = load("", warnings);

        assertEquals(120, settings.announcement().durationTicks());
        assertEquals(10, settings.announcement().cooldownSeconds());
        assertEquals(220, settings.announcement().maxMessageLength());
        assertTrue(settings.announcement().queueEnabled());
        assertEquals(HeadMode.AUTO, settings.head().mode());
        assertEquals(MissingSkinBehaviour.SKIP_HEAD, settings.head().missingSkin());
        assertEquals(AnimationStyle.SCALE_SLIDE, settings.animation().style());
        assertEquals(0.85f, settings.head().scale(), 0.0001f);
        assertFalse(settings.resourcePack().enabled(), "the resource pack is opt-in");
        assertNotNull(settings.title().packTemplate());
        assertTrue(warnings.isEmpty(), "defaults must not produce warnings: " + warnings);
    }

    @Test
    @DisplayName("values outside their valid range are clamped and reported")
    void invalidValuesAreClamped() {
        List<String> warnings = new ArrayList<>();
        PluginSettings settings = load("""
                announcement:
                  duration-ticks: 5
                  cooldown-seconds: 999999
                head:
                  scale: 900
                  mode: NOT_A_REAL_MODE
                animation:
                  entrance-ticks: -40
                """, warnings);

        assertEquals(20, settings.announcement().durationTicks());
        assertEquals(3600, settings.announcement().cooldownSeconds());
        assertEquals(8.0f, settings.head().scale(), 0.0001f);
        assertEquals(HeadMode.AUTO, settings.head().mode());
        assertEquals(0, settings.animation().entranceTicks());
        assertFalse(warnings.isEmpty(), "clamped values must be reported");
    }

    @Test
    @DisplayName("custom settings are read back exactly")
    void customValuesAreRead() {
        PluginSettings settings = load("""
                general:
                  language: fr
                  debug: true
                announcement:
                  duration-ticks: 200
                  max-message-length: 50
                head:
                  enabled: false
                  distance: 4.5
                  offset-y: -1.25
                  billboard: VERTICAL
                boss-bar:
                  enabled: true
                  color: GREEN
                  overlay: NOTCHED_10
                sounds:
                  enabled: false
                """, new ArrayList<>());

        assertEquals("fr", settings.language());
        assertTrue(settings.debug());
        assertEquals(200, settings.announcement().durationTicks());
        assertEquals(50, settings.announcement().maxMessageLength());
        assertFalse(settings.head().enabled());
        assertFalse(settings.rendersHead());
        assertEquals(4.5, settings.head().distance(), 0.0001);
        assertEquals(-1.25, settings.head().offsetY(), 0.0001);
        assertTrue(settings.sounds().cues().isEmpty());
        assertTrue(settings.bossBar().enabled());
        assertEquals("GREEN", settings.bossBar().color().name());
    }

    @Test
    @DisplayName("the lifetime always leaves room for both animations")
    void lifetimeCoversAnimations() {
        PluginSettings settings = load("""
                announcement:
                  duration-ticks: 20
                animation:
                  entrance-ticks: 30
                  exit-ticks: 40
                """, new ArrayList<>());

        assertTrue(settings.lifetimeTicks() >= 70,
                "a 20 tick duration cannot contain a 70 tick animation: " + settings.lifetimeTicks());
    }

    @Test
    @DisplayName("the skin lookup is skipped for console announcements without a skin")
    void consoleWithoutSkinDoesNotLookUp() {
        AnnouncementRequest console = AnnouncementRequest.ofConsole("Console", "", "hello");
        assertEquals("", console.skinLookupName());

        AnnouncementRequest consoleWithSkin = AnnouncementRequest.ofConsole("Console", "Notch", "hello");
        assertEquals("Notch", consoleWithSkin.skinLookupName());

        AnnouncementRequest explicit = AnnouncementRequest.ofTarget("Sara", UUID.randomUUID(), null, "hello");
        assertEquals("Sara", explicit.skinLookupName());
    }

    @Test
    @DisplayName("sound cues are parsed from the configuration list")
    void soundCuesAreParsed() {
        PluginSettings settings = load("""
                sounds:
                  enabled: true
                  play:
                    - sound: minecraft:ui.toast.challenge_complete
                      volume: 0.9
                      pitch: 1.2
                      delay-ticks: 6
                    - sound: minecraft:block.beacon.activate
                """, new ArrayList<>());

        assertEquals(2, settings.sounds().cues().size());
        assertEquals("minecraft:ui.toast.challenge_complete", settings.sounds().cues().get(0).sound());
        assertEquals(6, settings.sounds().cues().get(0).delayTicks());
        assertEquals(0.9f, settings.sounds().cues().get(0).volume(), 0.0001f);
        assertEquals("minecraft:block.beacon.activate", settings.sounds().cues().get(1).sound());
    }
}