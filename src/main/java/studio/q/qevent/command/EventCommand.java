package studio.q.qevent.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import studio.q.qevent.QEventPlugin;
import studio.q.qevent.announce.AnnouncementRequest;
import studio.q.qevent.announce.AnnouncementService;
import studio.q.qevent.config.Messages;
import studio.q.qevent.util.TextFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /event} — announce, title, preview, reload and help.
 *
 * <p>Every subcommand checks its own permission, every parse error produces a readable
 * message, and the console is supported everywhere it makes sense (it uses the configured
 * console name and, optionally, one player's head as its identity).</p>
 */
public final class EventCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_ANNOUNCE = "qevent.announce";
    private static final String PERMISSION_PREVIEW = "qevent.preview";
    private static final String PERMISSION_RELOAD = "qevent.reload";
    private static final String PERMISSION_ADMIN = "qevent.admin";

    /** Official Minecraft username shape: 3-16 characters of {@code a-z A-Z 0-9 _}. */
    private static final java.util.regex.Pattern PLAYER_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final QEventPlugin plugin;

    public EventCommand(QEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        ArgumentReader reader = ArgumentReader.of(args);
        switch (subCommand) {
            case "announce" -> handleAnnounce(sender, reader);
            case "title" -> handleTitle(sender, reader);
            case "preview" -> handlePreview(sender);
            case "reload" -> handleReload(sender);
            case "help", "?" -> sendHelp(sender);
            default -> message(sender, "command.unknown-subcommand", Map.of("input", subCommand));
        }
        return true;
    }

    // ------------------------------------------------------------------ announce

    private void handleAnnounce(CommandSender sender, ArgumentReader reader) {
        if (!requirePermission(sender, PERMISSION_ANNOUNCE)) {
            return;
        }
        if (reader.size() < 2) {
            message(sender, "command.message-empty", Map.of());
            return;
        }

        String message = null;
        AnnouncementRequest request = null;
        boolean quotedTarget = reader.size() >= 3 && reader.quoteStartsAt(1);

        if (reader.size() >= 3) {
            String candidate = reader.get(1);
            PlayerTarget target = quotedTarget ? quotedTarget(candidate) : resolveTarget(candidate);
            if (target != null) {
                message = reader.join(2);
                request = AnnouncementRequest.ofTarget(target.name(), target.id(), target.online(), message);
            }
        }

        if (request == null && quotedTarget) {
            // The administrator explicitly quoted a player name: report it instead of
            // silently announcing the whole line as a message.
            message(sender, "command.player-not-found", Map.of("name", TextFormatter.escape(reader.get(1))));
            return;
        }

        if (request == null) {
            message = reader.join(1);
            request = sender instanceof Player player
                    ? AnnouncementRequest.ofPlayer(player, message)
                    : AnnouncementRequest.ofConsole(plugin.settings().announcement().consoleName(),
                    plugin.settings().skin().consoleSkin(), message);
        }

        if (message.isBlank()) {
            message(sender, "command.message-empty", Map.of());
            return;
        }
        if (!withinLengthLimit(sender, message)) {
            return;
        }
        dispatch(sender, request);
    }

    // --------------------------------------------------------------------- title

    private void handleTitle(CommandSender sender, ArgumentReader reader) {
        if (!requirePermission(sender, PERMISSION_ANNOUNCE)) {
            return;
        }
        String rest = reader.join(1);
        if (rest.isBlank()) {
            message(sender, "command.title-usage", Map.of());
            return;
        }

        String title;
        String subtitle;
        int pipe = rest.indexOf('|');
        if (pipe >= 0) {
            title = rest.substring(0, pipe).trim();
            subtitle = rest.substring(pipe + 1).trim();
        } else if (reader.size() >= 3) {
            title = reader.get(1);
            subtitle = reader.join(2);
        } else {
            message(sender, "command.title-usage", Map.of());
            return;
        }

        if (title.isBlank() || subtitle.isBlank()) {
            message(sender, "command.title-usage", Map.of());
            return;
        }
        if (!withinLengthLimit(sender, title) || !withinLengthLimit(sender, subtitle)) {
            return;
        }

        AnnouncementRequest request = sender instanceof Player player
                ? AnnouncementRequest.ofPlayer(player, subtitle)
                : AnnouncementRequest.ofConsole(plugin.settings().announcement().consoleName(),
                plugin.settings().skin().consoleSkin(), subtitle);
        dispatch(sender, request.withTemplates(title, subtitle, null));
    }

    // ------------------------------------------------------------------- preview

    private void handlePreview(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_PREVIEW)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "command.players-only", Map.of());
            return;
        }
        String previewMessage = plugin.messages().raw("preview.message");
        AnnouncementRequest request = AnnouncementRequest.ofPlayer(player, previewMessage)
                .forViewerOnly(player.getUniqueId());
        plugin.announcements().request(sender, request);
        message(sender, "command.preview-sent", Map.of());
    }

    // -------------------------------------------------------------------- reload

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_RELOAD)) {
            return;
        }
        try {
            plugin.reloadEverything();
            message(sender, "command.reloaded", Map.of());
        } catch (Exception exception) {
            plugin.getLogger().warning("Reload failed: " + exception.getMessage());
            message(sender, "command.reload-failed",
                    Map.of("error", TextFormatter.escape(String.valueOf(exception.getMessage()))));
        }
    }

    // --------------------------------------------------------------------- help

    private void sendHelp(CommandSender sender) {
        Messages messages = plugin.messages();
        Map<String, String> placeholders = Map.of("version", plugin.getPluginMeta().getVersion());
        for (var line : messages.getList("command.help", placeholders)) {
            sender.sendMessage(line);
        }
    }

    // ------------------------------------------------------------------ plumbing

    private void dispatch(CommandSender sender, AnnouncementRequest request) {
        AnnouncementService.Outcome outcome = plugin.announcements().request(sender, request);
        switch (outcome.status()) {
            case STARTED -> message(sender, "command.announce-sent",
                    Map.of("viewers", Integer.toString(outcome.value())));
            case QUEUED -> message(sender, "command.announce-queued",
                    Map.of("position", Integer.toString(outcome.value())));
            case COOLDOWN -> message(sender, "command.cooldown",
                    Map.of("seconds", Integer.toString(outcome.value())));
            case IN_PROGRESS -> message(sender, "command.announcement-in-progress", Map.of());
            case QUEUE_FULL -> message(sender, "command.queue-full", Map.of(
                    "max", Integer.toString(plugin.settings().announcement().queueMaxSize())));
        }
    }

    private boolean withinLengthLimit(CommandSender sender, String text) {
        int length = TextFormatter.visibleLength(text);
        int max = plugin.settings().announcement().maxMessageLength();
        if (length > max) {
            message(sender, "command.message-too-long",
                    Map.of("length", Integer.toString(length), "max", Integer.toString(max)));
            return false;
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String node) {
        if (sender.hasPermission(node) || sender.hasPermission(PERMISSION_ADMIN)) {
            return true;
        }
        message(sender, "command.no-permission", Map.of());
        return false;
    }

    private void message(CommandSender sender, String key, Map<String, String> placeholders) {
        plugin.messages().send(sender, key, placeholders);
    }

    /**
     * Resolves the {@code <player>} form of {@code /event announce}.
     *
     * <p>Only exact online names and players the server has already cached are accepted, so a
     * word such as {@code HALLOWEEN} is never mistaken for a player name and no blocking web
     * lookup is triggered.</p>
     */
    private PlayerTarget resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new PlayerTarget(online.getName(), online.getUniqueId(), online);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getUniqueId() != null) {
            String resolved = cached.getName() != null ? cached.getName() : name;
            return new PlayerTarget(resolved, cached.getUniqueId(), null);
        }
        return null;
    }

    /**
     * Resolves an explicitly quoted player name.
     *
     * <p>The UUID may stay null: {@code SkinResolver} performs the name lookup asynchronously
     * and the announcement still renders as text when the name turns out not to exist.</p>
     */
    private PlayerTarget quotedTarget(String name) {
        if (!PLAYER_NAME.matcher(name).matches()) {
            return null;
        }
        PlayerTarget local = resolveTarget(name);
        return local != null ? local : new PlayerTarget(name, null, null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (has(sender, PERMISSION_ANNOUNCE)) {
                options.add("announce");
                options.add("title");
            }
            if (has(sender, PERMISSION_PREVIEW)) {
                options.add("preview");
            }
            if (has(sender, PERMISSION_RELOAD)) {
                options.add("reload");
            }
            options.add("help");
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("announce") && has(sender, PERMISSION_ANNOUNCE)) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return List.of();
    }

    private boolean has(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission(PERMISSION_ADMIN);
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    /** Resolved {@code <player>} argument of the announce subcommand. */
    private record PlayerTarget(String name, UUID id, Player online) {
    }
}
