package studio.q.qevent.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    @Test
    @DisplayName("migrates shipped old defaults and enables the official resource pack")
    void migratesOldDefaults() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                head:
                  offset-y: 1.2
                  scale: 0.55
                resource-pack:
                  enabled: false
                  url: ''
                """));

        ConfigMigrator.Result result = ConfigMigrator.migrate(config);

        assertTrue(result.migrated());
        assertTrue(result.defaultsUpdated());
        assertEquals(ConfigMigrator.HEAD_OFFSET_Y, config.getDouble("head.offset-y"), 0.0001);
        assertEquals(ConfigMigrator.HEAD_SCALE, config.getDouble("head.scale"), 0.0001);
        assertTrue(config.getBoolean("resource-pack.enabled"));
        assertEquals(ConfigMigrator.ARTWORK_PACK_URL, config.getString("resource-pack.url"));
        assertEquals(ConfigMigrator.ARTWORK_PACK_SHA1, config.getString("resource-pack.sha1"));
        assertEquals(ConfigMigrator.CURRENT_VERSION, config.getInt("qevent-config-version"));
    }

    @Test
    @DisplayName("preserves custom head values while updating the shipped disabled-pack default")
    void preservesCustomValues() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                head:
                  offset-y: -0.8
                  scale: 1.4
                resource-pack:
                  enabled: false
                  url: ''
                """));

        ConfigMigrator.Result result = ConfigMigrator.migrate(config);

        assertTrue(result.migrated());
        assertTrue(result.defaultsUpdated());
        assertEquals(-0.8, config.getDouble("head.offset-y"), 0.0001);
        assertEquals(1.4, config.getDouble("head.scale"), 0.0001);
        assertTrue(config.getBoolean("resource-pack.enabled"));
        assertEquals(ConfigMigrator.ARTWORK_PACK_URL, config.getString("resource-pack.url"));
        assertEquals(ConfigMigrator.CURRENT_VERSION, config.getInt("qevent-config-version"));
    }

    @Test
    @DisplayName("does not repeat a completed migration")
    void migrationIsIdempotent() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                qevent-config-version: 2
                head:
                  offset-y: 0.1
                  scale: 1.2
                """));

        ConfigMigrator.Result result = ConfigMigrator.migrate(config);

        assertFalse(result.migrated());
        assertEquals(0.1, config.getDouble("head.offset-y"), 0.0001);
        assertEquals(1.2, config.getDouble("head.scale"), 0.0001);
    }
}
