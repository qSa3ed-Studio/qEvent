package studio.q.qevent.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

/**
 * Turns administrator supplied text into Adventure components.
 *
 * <p>Both text dialects are supported everywhere a string is configurable:
 * MiniMessage tags ({@code <gold>}, {@code <gradient:#a:#b>}) and classic legacy
 * codes ({@code &6}, {@code &l}, {@code &#RRGGBB}). Legacy codes are converted to
 * their MiniMessage equivalent first, so a single parser handles both.</p>
 *
 * <p>Values that come from players (names, chat messages) must be passed through
 * {@link #escape(String)} before they are inserted into a template, otherwise a
 * message could inject MiniMessage tags.</p>
 */
public final class TextFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Map<Character, String> LEGACY_CODES = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private TextFormatter() {
    }

    /** Parses a complete template. Never returns null. */
    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(toMiniMessage(input));
    }

    /** Parses a template after substituting the given placeholders. */
    public static Component parse(String input, Map<String, String> placeholders) {
        return parse(substitute(input, placeholders));
    }

    /**
     * Replaces {@code %key%} placeholders with values.
     *
     * <p>Values are expected to be already escaped when they originate from a player.</p>
     */
    public static String substitute(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    /**
     * Escapes text so MiniMessage treats it as literal content.
     *
     * <p>Used for anything a player controls: announcement messages, player names and
     * viewer names. Without this, typing {@code <red>} into an announcement would change
     * the formatting of everybody's screen.</p>
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** The visible text of a template, with every formatting code removed. */
    public static String plainText(String input) {
        return PlainTextComponentSerializer.plainText().serialize(parse(input));
    }

    /** Visible length of a template, used for length validation. */
    public static int visibleLength(String input) {
        return plainText(input).length();
    }

    /** Converts legacy {@code &} codes into MiniMessage tags, leaving tags untouched. */
    public static String toMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(i + 1));
                if (next == '#' && i + 8 <= input.length()) {
                    out.append('<').append('#').append(input, i + 2, i + 8).append('>');
                    i += 8;
                    continue;
                }
                String tag = LEGACY_CODES.get(next);
                if (tag != null) {
                    out.append(tag);
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
