package paradigms.chat.client.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import paradigms.chat.client.config.ClientConfig;

/**
 * Opens the client window. The host and port come from the command line
 * {@code java TCPClient <ServerIPAddress> <PortNumber>} or from {@code client.properties}.
 */
public final class ClientApplication extends Application {

    private ClientView view;

    @Override
    public void start(Stage stage) {
        ClientConfig config = ClientConfig.load(getParameters().getRaw());
        if (!config.valid()) {
            System.err.println(config.error());
        }
        view = new ClientView(config);
        Scene scene = new Scene(view.root(), 980, 680);
        var css = ClientApplication.class.getResource("/css/chat.css");
        if (css == null) {
            throw new IllegalStateException("Missing /css/chat.css on the classpath.");
        }
        scene.getStylesheets().add(css.toExternalForm());
        stage.setTitle("TCP Client");
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setOnCloseRequest(event -> view.shutdown());
        stage.show();
    }

    @Override
    public void stop() {
        if (view != null) {
            view.shutdown();
        }
    }
}
