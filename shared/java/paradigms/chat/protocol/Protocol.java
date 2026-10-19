package paradigms.chat.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Line-oriented chat protocol shared by TCPServer and TCPClient.
 * One UTF-8 line is one message. Fields are separated by {@code |}.
 * Chat text may itself contain {@code |}; it is always the last field.
 */
public final class Protocol {

    public static final String JOIN = "JOIN";
    public static final String MSG = "MSG";
    public static final String CMD = "CMD";
    public static final String WELCOME = "WELCOME";
    public static final String SYS = "SYS";
    public static final String CHAT = "CHAT";
    public static final String USERS = "USERS";
    public static final String ERR = "ERR";
    public static final String BYE = "BYE";

    public static final String MODE_ACTIVE = "ACTIVE";
    public static final String MODE_READ_ONLY = "READONLY";

    /** Typed by a user. The client sends {@link #CMD_ALL_USERS} for this word. */
    public static final String CMD_ALL_USERS = "allUsers";
    /** Wire command used for the LEAVE button and for the words {@code end} and {@code bye}. */
    public static final String CMD_LEAVE = "leave";

    public static final int MAX_NAME = 20;
    public static final int MAX_TEXT = 2000;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _\\-]{0,19}");

    private Protocol() {
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static boolean reservedName(String name) {
        if (name == null) {
            return false;
        }
        return CMD_ALL_USERS.equalsIgnoreCase(name)
                || "end".equalsIgnoreCase(name)
                || "bye".equalsIgnoreCase(name)
                || name.regionMatches(true, 0, "Guest-", 0, "Guest-".length());
    }

    public static String join(String username) {
        return JOIN + "|" + (username == null ? "" : username);
    }

    public static String message(String text) {
        return MSG + "|" + text;
    }

    public static String command(String name) {
        return CMD + "|" + name;
    }

    public static String welcome(String displayName, boolean readOnly) {
        return WELCOME + "|" + displayName + "|" + (readOnly ? MODE_READ_ONLY : MODE_ACTIVE);
    }

    public static String system(String text) {
        return SYS + "|" + text;
    }

    public static String chat(String time, String username, String text) {
        return CHAT + "|" + time + "|" + username + "|" + text;
    }

    public static String users(List<String> names) {
        return USERS + "|" + String.join("|", names);
    }

    public static String error(String text) {
        return ERR + "|" + text;
    }

    public static String bye() {
        return BYE + "|Goodbye";
    }

    public static String typeOf(String line) {
        int bar = line.indexOf('|');
        return bar < 0 ? line : line.substring(0, bar);
    }

    public static String payload(String line) {
        int bar = line.indexOf('|');
        return bar < 0 ? "" : line.substring(bar + 1);
    }

    public static Welcome parseWelcome(String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3 || !WELCOME.equals(parts[0])) {
            throw new IllegalArgumentException("Bad welcome line: " + line);
        }
        return new Welcome(parts[1], MODE_READ_ONLY.equals(parts[2]));
    }

    public static Chat parseChat(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4 || !CHAT.equals(parts[0])) {
            throw new IllegalArgumentException("Bad chat line: " + line);
        }
        return new Chat(parts[1], parts[2], parts[3]);
    }

    public static List<String> parseUsers(String line) {
        String[] parts = line.split("\\|", -1);
        List<String> names = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                names.add(parts[i]);
            }
        }
        return List.copyOf(names);
    }

    public record Welcome(String displayName, boolean readOnly) {
    }

    public record Chat(String time, String username, String text) {
    }
}
