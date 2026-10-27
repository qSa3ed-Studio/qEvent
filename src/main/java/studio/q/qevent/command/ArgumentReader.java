package studio.q.qevent.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads command arguments safely.
 *
 * <p>Bukkit splits commands on spaces before the executor sees them, so
 * {@code /event title "Halloween Event" "Join now!"} arrives as five fragments. This reader
 * re-joins them and splits again while honouring double quotes, which lets administrators
 * pass multi-word titles without guessing.</p>
 */
public final class ArgumentReader {

    private final List<String> tokens;
    private final String[] rawArguments;

    private ArgumentReader(List<String> tokens, String[] rawArguments) {
        this.tokens = tokens;
        this.rawArguments = rawArguments == null ? new String[0] : rawArguments;
    }

    public static ArgumentReader of(String[] args) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean started = false;

        for (String argument : args) {
            for (int i = 0; i < argument.length(); i++) {
                char c = argument.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                    started = true;
                    continue;
                }
                current.append(c);
                started = true;
            }
            // A space between Bukkit arguments is a separator unless we are inside quotes.
            if (quoted) {
                current.append(' ');
            } else if (started) {
                tokens.add(current.toString());
                current.setLength(0);
                started = false;
            }
        }
        if (started) {
            tokens.add(current.toString());
        }
        return new ArgumentReader(tokens, args);
    }

    public int size() {
        return tokens.size();
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    public String get(int index) {
        return index >= 0 && index < tokens.size() ? tokens.get(index) : "";
    }

    /** Joins every token from {@code from} onwards with single spaces. */
    public String join(int from) {
        if (from >= tokens.size()) {
            return "";
        }
        return String.join(" ", tokens.subList(from, tokens.size())).trim();
    }

    public List<String> tokens() {
        return List.copyOf(tokens);
    }

    /**
     * True when the original argument at {@code index} was wrapped in double quotes.
     *
     * <p>{@code /event announce "Notch" message} means "resolve Notch as a player"; the quotes
     * are the administrator's way of disambiguating a name from the start of a message.</p>
     */
    public boolean quoteStartsAt(int index) {
        return index >= 0 && index < rawArguments.length && rawArguments[index].startsWith("\"");
    }
}
