package paradigms.chat.client.model;

import java.util.List;

/**
 * Observer for one chat connection. The JavaFX view implements this;
 * {@link ChatClient} does not depend on JavaFX.
 */
public interface ClientListener {

    default void onWelcome(String displayName, boolean readOnly) {
    }

    default void onChat(String time, String username, String text) {
    }

    default void onSystem(String text) {
    }

    default void onUsers(List<String> users) {
    }

    default void onError(String text) {
    }

    default void onDisconnected(String reason) {
    }
}
