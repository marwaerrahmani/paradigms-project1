package paradigms.chat.server.model;

/**
 * Snapshot of one connected client. {@code colorHex} is a display hint for the
 * server list; the network model never talks to JavaFX.
 */
public record ConnectedClient(String displayName, boolean readOnly, String colorHex) {

    public String listLabel() {
        return readOnly ? displayName + " (read-only)" : displayName;
    }
}
