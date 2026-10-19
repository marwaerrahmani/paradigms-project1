package paradigms.chat.server.model;

import java.util.List;

/**
 * Observer for server activity. Implementations may be a JavaFX view;
 * {@link ChatServer} only depends on this interface.
 */
public interface ServerListener {

    default void onLog(String message) {
    }

    default void onClientsChanged(List<ConnectedClient> clients) {
    }

    default void onStateChanged(boolean running, String bindAddress, int port) {
    }
}
