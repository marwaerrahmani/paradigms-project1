import javafx.application.Application;
import paradigms.chat.server.ui.ServerApplication;

/**
 * Entry point: {@code java TCPServer}.
 * On JDK 11 and later, JavaFX also has to be placed on the module path.
 * The scripts in this project add that flag.
 */
public final class TCPServer {

    private TCPServer() {
    }

    public static void main(String[] args) {
        Application.launch(ServerApplication.class, args);
    }
}
