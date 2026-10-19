package paradigms.chat.client.model;

/**
 * What the text field means. {@code allUsers}, {@code end}, and {@code bye}
 * are commands; every other line is a chat message.
 */
public enum InputIntent {
    CHAT,
    LIST_USERS,
    LEAVE;

    public static InputIntent from(String trimmed) {
        if ("allUsers".equals(trimmed)) {
            return LIST_USERS;
        }
        if ("end".equals(trimmed) || "bye".equals(trimmed)) {
            return LEAVE;
        }
        return CHAT;
    }
}
