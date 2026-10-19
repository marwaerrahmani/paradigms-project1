package paradigms.chat.server.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import paradigms.chat.server.config.ServerConfig;
import paradigms.chat.server.model.ChatServer;

/**
 * Opens the server window and starts {@link ChatServer} on a virtual thread
 * so the UI appears while the socket binds.
 */
public final class ServerApplication extends Application {

    private ChatServer server;

    @Override
    public void start(Stage stage) {
        ServerConfig config;
        try {
            config = ServerConfig.load();
        } catch (RuntimeException ex) {
            config = new ServerConfig("0.0.0.0", 3000);
            System.err.println(ex.getMessage());
        }
        server = new ChatServer(config.bindAddress(), config.port());
        ServerView view = new ServerView();
        server.addListener(view);

        Scene scene = new Scene(view.root(), 980, 640);
        var css = ServerApplication.class.getResource("/css/chat.css");
        if (css == null) {
            throw new IllegalStateException("Missing /css/chat.css on the classpath.");
        }
        scene.getStylesheets().add(css.toExternalForm());
        stage.setTitle("TCP Server");
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(event -> stopServer());
        stage.show();

        Thread.ofVirtual().name("server-start").start(() -> {
            try {
                server.start();
            } catch (Exception ex) {
                Platform.runLater(() -> view.showFatal(ex.getMessage()));
            }
        });
    }

    @Override
    public void stop() {
        stopServer();
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
        }
    }
}
