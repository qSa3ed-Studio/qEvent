package studio.q.qevent;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import studio.q.qevent.announce.AnnouncementService;
import studio.q.qevent.command.EventCommand;
import studio.q.qevent.config.ConfigMigrator;
import studio.q.qevent.config.Messages;
import studio.q.qevent.config.PluginSettings;
import studio.q.qevent.listener.PlayerConnectionListener;
import studio.q.qevent.render.ItemDisplayHead;
import studio.q.qevent.resourcepack.ResourcePackService;
import studio.q.qevent.util.ServerVersion;

import java.util.logging.Level;

/**
 * qEvent — cinematic event announcements for Paper servers.
 *
 * <p>Developed and published by qSa3ed Studio.
 * <a href="https://github.com/qSa3ed-Studio/qEvent">github.com/qSa3ed-Studio/qEvent</a></p>
 */
public final class QEventPlugin extends JavaPlugin {

    private PluginSettings settings;
    private Messages messages;
    private AnnouncementService announcements;
    private ResourcePackService resourcePacks;
    private ServerVersion serverVersion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.Result migration = ConfigMigrator.migrate(getConfig());
        if (migration.migrated()) {
            saveConfig();
            if (migration.defaultsUpdated()) {
                getLogger().info("Updated qEvent defaults: moved the head into the visible upper screen area "
                        + "and enabled the official artwork pack. Custom config values were preserved.");
            }
        }
        serverVersion = ServerVersion.current();

        try {
            settings = PluginSettings.load(getConfig(), warning -> getLogger().warning(warning));
            messages = Messages.load(this, settings.language());
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "qEvent could not read its configuration and will be disabled. "
                    + "Fix the reported error and restart the server.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        resourcePacks = new ResourcePackService(this, settings);
        announcements = new AnnouncementService(this, settings);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        EventCommand executor = new EventCommand(this);
        PluginCommand command = getCommand("event");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("The /event command is missing from plugin.yml; "
                    + "the plugin cannot be used. Reinstall qEvent.");
        }

        logStartupSummary();
    }

    @Override
    public void onDisable() {
        if (announcements != null) {
            announcements.shutdown();
        }
        getLogger().info("qEvent disabled.");
    }

    private void logStartupSummary() {
        getLogger().info("qEvent v" + getPluginMeta().getVersion() + " by qSa3ed Studio enabled.");
        getLogger().info("Server: " + Bukkit.getName() + " " + serverVersion
                + " | Java " + System.getProperty("java.version")
                + " | language " + messages.language());
        getLogger().info("Head renderer: " + (ItemDisplayHead.isSupported()
                ? "display entities available (per-viewer skin heads)"
                : "unavailable - announcements will use the text-only fallback"));
        getLogger().info("Resource pack: " + resourcePacks.describe());
        if (!serverVersion.isSupported()) {
            getLogger().warning("This server version (" + serverVersion + ") is outside the supported range. "
                    + "qEvent is built for Paper 1.20.5 and newer; see COMPATIBILITY.md.");
        }
    }

    /** Reloads {@code config.yml} and the language file, and restarts the running state. */
    public void reloadEverything() {
        reloadConfig();

        PluginSettings updatedSettings = PluginSettings.load(getConfig(), warning -> getLogger().warning(warning));
        Messages updatedMessages = Messages.load(this, updatedSettings.language());

        this.settings = updatedSettings;
        this.messages = updatedMessages;

        announcements.applySettings(updatedSettings);
        resourcePacks.applySettings(updatedSettings);
        debugLog("Configuration reloaded.");
    }

    /** Writes a diagnostic line when {@code general.debug} is enabled. */
    public void debugLog(String message) {
        if (settings != null && settings.debug()) {
            getLogger().info("[debug] " + message);
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public AnnouncementService announcements() {
        return announcements;
    }

    public ResourcePackService resourcePacks() {
        return resourcePacks;
    }

    public ServerVersion serverVersion() {
        return serverVersion;
    }
}
