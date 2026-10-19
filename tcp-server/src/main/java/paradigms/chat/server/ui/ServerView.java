package paradigms.chat.server.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import paradigms.chat.server.model.ChatServer;
import paradigms.chat.server.model.ConnectedClient;
import paradigms.chat.server.model.ServerListener;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * JavaFX presentation for the server. Layout is a {@link GridPane}; colors and
 * spacing come from {@code chat.css}. Socket work stays in {@link ChatServer}.
 */
public final class ServerView implements ServerListener {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GridPane root = new GridPane();
    private final Circle statusDot = new Circle(7);
    private final Label statusLabel = new Label("Starting");
    private final Label endpointLabel = new Label("Opening the server socket");
    private final Label usersTitle = new Label("Connected  ·  0");
    private final ObservableList<ConnectedClient> users = FXCollections.observableArrayList();
    private final ListView<ConnectedClient> userList = new ListView<>(users);
    private final TextArea logArea = new TextArea();

    public ServerView() {
        root.getStyleClass().add("server-root");
        root.setHgap(16);
        root.setVgap(16);
        root.setPadding(new Insets(22));

        ColumnConstraints usersColumn = new ColumnConstraints();
        usersColumn.setPercentWidth(34);
        usersColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints logColumn = new ColumnConstraints();
        logColumn.setPercentWidth(66);
        logColumn.setHgrow(Priority.ALWAYS);
        root.getColumnConstraints().addAll(usersColumn, logColumn);

        RowConstraints headerRow = new RowConstraints();
        headerRow.setVgrow(Priority.NEVER);
        RowConstraints bodyRow = new RowConstraints();
        bodyRow.setVgrow(Priority.ALWAYS);
        bodyRow.setValignment(VPos.TOP);
        root.getRowConstraints().addAll(headerRow, bodyRow);

        root.add(buildHeader(), 0, 0, 2, 1);
        root.add(buildUsers(), 0, 1);
        root.add(buildLog(), 1, 1);

        statusDot.getStyleClass().add("status-dot");
        statusLabel.getStyleClass().add("status-label");
        userList.setCellFactory(list -> new UserCell());
        userList.setPlaceholder(new Label("Waiting for Client"));
        userList.getStyleClass().add("user-list");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        logArea.setPromptText("Activity log");
    }

    public Parent root() {
        return root;
    }

    @Override
    public void onLog(String message) {
        System.out.println(message);
        Platform.runLater(() -> appendLog(message));
    }

    @Override
    public void onClientsChanged(java.util.List<ConnectedClient> clients) {
        Platform.runLater(() -> {
            users.setAll(clients);
            usersTitle.setText("Connected  ·  " + clients.size());
        });
    }

    @Override
    public void onStateChanged(boolean running, String bindAddress, int port) {
        Platform.runLater(() -> applyState(running, bindAddress, port));
    }

    public void showFatal(String message) {
        appendLog(message);
        applyState(false, "", 0);
        endpointLabel.setText("The server socket is closed");
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("TCP Server");
        alert.setHeaderText("The server could not start");
        alert.setContentText(message);
        if (root.getScene() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        alert.show();
    }

    private Parent buildHeader() {
        Label eyebrow = new Label("MULTI-CLIENT CHAT");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("TCP Server");
        title.getStyleClass().add("title");
        VBox titles = new VBox(2, eyebrow, title);

        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("status-pill");

        endpointLabel.getStyleClass().add("hint");
        VBox text = new VBox(6, titles, endpointLabel);

        HBox header = new HBox(16, text, status);
        HBox.setHgrow(text, Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Parent buildUsers() {
        usersTitle.getStyleClass().add("section-title");
        Label caption = new Label("Each user gets a background color.");
        caption.getStyleClass().add("hint");
        VBox box = new VBox(8, usersTitle, caption, userList);
        VBox.setVgrow(userList, Priority.ALWAYS);
        box.getStyleClass().add("panel");
        GridPane.setVgrow(box, Priority.ALWAYS);
        GridPane.setHgrow(box, Priority.ALWAYS);
        GridPane.setHalignment(box, HPos.LEFT);
        return box;
    }

    private Parent buildLog() {
        Label title = new Label("Activity log");
        title.getStyleClass().add("section-title");
        VBox box = new VBox(8, title, logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        box.getStyleClass().add("panel");
        GridPane.setVgrow(box, Priority.ALWAYS);
        GridPane.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void appendLog(String message) {
        String line = LocalTime.now().format(CLOCK) + "  " + message;
        if (logArea.getText() == null || logArea.getText().isEmpty()) {
            logArea.appendText(line);
        } else {
            logArea.appendText(System.lineSeparator() + line);
        }
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void applyState(boolean running, String bindAddress, int port) {
        statusDot.getStyleClass().remove("online");
        if (running) {
            statusDot.getStyleClass().add("online");
            statusLabel.setText("Online");
            String where = "0.0.0.0".equals(bindAddress) ? "all interfaces" : bindAddress;
            endpointLabel.setText("Online on " + where + ", port " + port
                    + "   ·   clients run: java TCPClient <server-ip> " + port);
        } else {
            statusLabel.setText("Offline");
        }
    }

    private static final class UserCell extends ListCell<ConnectedClient> {
        @Override
        protected void updateItem(ConnectedClient item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            Label name = new Label(item.listLabel());
            name.setMnemonicParsing(false);
            name.setMaxWidth(Double.MAX_VALUE);
            name.setStyle("-fx-background-color: " + item.colorHex()
                    + "; -fx-text-fill: #14202b; -fx-font-weight: 700;"
                    + " -fx-background-radius: 10; -fx-padding: 10 12 10 12;");
            setGraphic(name);
        }
    }
}
