package studio.q.qevent.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import studio.q.qevent.util.TextFormatter;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads {@code lang/<language>.yml} and resolves messages.
 *
 * <p>The bundled English file is always used as a fallback, so a partially translated file
 * or an older one that is missing a key still produces sensible output instead of
 * {@code null}.</p>
 */
public final class Messages {

    private static final String FALLBACK_LANGUAGE = "en";

    private final YamlConfiguration file;
    private final String prefix;
    private final String language;

    private Messages(YamlConfiguration file, String language) {
        this.file = file;
        this.language = language;
        this.prefix = file.getString("prefix", "");
    }

    /**
     * Loads the requested language, creating the bundled file in the plugin data folder on
     * first use and falling back to English when the requested language does not exist.
     */
    public static Messages load(JavaPlugin plugin, String language) {
        String requested = language == null || language.isBlank() ? FALLBACK_LANGUAGE : language.trim();

        // Always make sure the shipped English file is available as a template and fallback.
        File englishFile = new File(plugin.getDataFolder(), "lang/" + FALLBACK_LANGUAGE + ".yml");
        if (!englishFile.isFile()) {
            plugin.saveResource("lang/" + FALLBACK_LANGUAGE + ".yml", false);
        }

        File target = new File(plugin.getDataFolder(), "lang/" + requested + ".yml");
        boolean fallback = false;
        if (!target.isFile()) {
            if (requested.equals(FALLBACK_LANGUAGE)) {
                target = englishFile;
            } else {
                plugin.getLogger().warning("Language file lang/" + requested + ".yml was not found; using English.");
                target = englishFile;
                fallback = true;
            }
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(target);
        try (InputStream bundled = plugin.getResource("lang/" + FALLBACK_LANGUAGE + ".yml")) {
            if (bundled != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled language defaults.", exception);
        }

        String effective = fallback ? FALLBACK_LANGUAGE : requested;
        if (!target.isFile()) {
            plugin.getLogger().warning("No language file could be loaded; built-in defaults are used.");
        }
        return new Messages(configuration, effective);
    }

    public String language() {
        return language;
    }

    /** Resolves a single message with placeholders substituted. */
    public Component get(String key, Map<String, String> placeholders) {
        return TextFormatter.parse(TextFormatter.substitute(raw(key), placeholders));
    }

    /** Resolves a list of message lines, used by the help screen. */
    public List<Component> getList(String key, Map<String, String> placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : file.getStringList(key)) {
            lines.add(TextFormatter.parse(TextFormatter.substitute(line, placeholders)));
        }
        return lines;
    }

    /** Raw text of a key, without parsing. Useful for the console log. */
    public String raw(String key) {
        String value = file.getString(key);
        return value == null ? "" : value;
    }

    /** Plain text of a key with placeholders substituted and formatting removed. */
    public String plain(String key, Map<String, String> placeholders) {
        return TextFormatter.plainText(TextFormatter.substitute(raw(key), placeholders));
    }

    /**
     * Sends a prefixed message to a command sender.
     *
     * @param prefixed when true the configured prefix is prepended
     */
    public void send(CommandSender sender, String key, Map<String, String> placeholders, boolean prefixed) {
        Component message = get(key, placeholders);
        sender.sendMessage(prefixed ? TextFormatter.parse(prefix).append(message) : message);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        send(sender, key, placeholders, true);
    }

    /** Sends a message that carries its own formatting, without the configured prefix. */
    public void sendRaw(CommandSender sender, String key, Map<String, String> placeholders) {
        send(sender, key, placeholders, false);
    }
}
