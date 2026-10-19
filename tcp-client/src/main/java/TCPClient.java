import javafx.application.Application;
import paradigms.chat.client.ui.ClientApplication;

/**
 * Entry point: {@code java TCPClient localhost 3000}.
 */
public final class TCPClient {

    private TCPClient() {
    }

    public static void main(String[] args) {
        Application.launch(ClientApplication.class, args);
    }
}
