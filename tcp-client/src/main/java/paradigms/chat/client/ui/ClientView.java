package paradigms.chat.client.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import paradigms.chat.client.config.ClientConfig;
import paradigms.chat.client.model.ChatClient;
import paradigms.chat.client.model.ClientListener;
import paradigms.chat.protocol.Protocol;

import java.util.List;

/**
 * Login and chat screens. Both screens are {@link GridPane}s. The class
 * observes {@link ChatClient} and does not open sockets itself.
 */
public final class ClientView implements ClientListener {

    private final ClientConfig config;
    private final GridPane login = new GridPane();
    private final GridPane chat = new GridPane();
    private Parent showing;

    private final TextField username = new TextField();
    private final Label loginMessage = new Label();
    private final Button join = new Button("Join chat");

    private final Circle statusDot = new Circle(7);
    private final Label statusLabel = new Label("Online");
    private final Label identity = new Label();
    private final Label modeBadge = new Label("READ-ONLY MODE");
    private final VBox transcript = new VBox(8);
    private final ScrollPane transcriptScroll = new ScrollPane(transcript);
    private final TextField composer = new TextField();
    private final Button send = new Button("SEND");
    private final Button leave = new Button("LEAVE");
    private final ObservableList<String> knownUsers = FXCollections.observableArrayList();
    private final ListView<String> userList = new ListView<>(knownUsers);

    private ChatClient client;
    private boolean inChat;
    private boolean shutDown;
    private String myName = "";

    public ClientView(ClientConfig config) {
        this.config = config;
        buildLogin();
        buildChat();
        showing = login;
        if (!config.valid()) {
            loginMessage.setText(config.error());
            join.setDisable(true);
        }
    }

    public Parent root() {
        return showing;
    }

    public void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        if (client != null) {
            client.disconnect();
        }
    }

    @Override
    public void onWelcome(String displayName, boolean readOnly) {
        Platform.runLater(() -> showChat(displayName, readOnly));
    }

    @Override
    public void onChat(String time, String user, String text) {
        Platform.runLater(() -> {
            if (inChat) {
                appendChat(time, user, text);
            }
        });
    }

    @Override
    public void onSystem(String text) {
        Platform.runLater(() -> {
            if (inChat) {
                appendSystem(text);
            }
        });
    }

    @Override
    public void onUsers(List<String> users) {
        Platform.runLater(() -> {
            knownUsers.setAll(users);
            if (inChat) {
                appendSystem(users.isEmpty()
                        ? "Active users: (none)"
                        : "Active users: " + String.join(", ", users));
            }
        });
    }

    @Override
    public void onError(String text) {
        Platform.runLater(() -> {
            if (inChat) {
                appendSystem(text);
            } else {
                loginMessage.setText(text);
                join.setDisable(false);
            }
        });
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            if (!inChat) {
                join.setDisable(false);
                if (reason != null && !reason.isBlank() && loginMessage.getText().startsWith("Connecting")) {
                    loginMessage.setText(reason);
                }
                return;
            }
            showLogin(reason);
        });
    }

    private void buildLogin() {
        login.getStyleClass().add("client-root");
        login.setPadding(new Insets(24));
        ColumnConstraints side = growingColumn();
        ColumnConstraints middle = new ColumnConstraints(460);
        login.getColumnConstraints().addAll(side, middle, side);
        login.getRowConstraints().addAll(growingRow(), new RowConstraints(), growingRow());

        Label eyebrow = new Label("MULTI-CLIENT CHAT");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("TCP Client");
        title.getStyleClass().add("title");
        Label target = new Label(targetText());
        target.getStyleClass().add("hint");
        target.setWrapText(true);

        Label nameLabel = new Label("Username");
        nameLabel.getStyleClass().add("section-title");
        username.setPromptText("Leave empty for read-only mode");

        Label hint = new Label("Empty username joins in READ-ONLY MODE. You can still list users and leave.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        join.getStyleClass().add("primary");
        join.setMaxWidth(Double.MAX_VALUE);
        join.setDefaultButton(true);
        join.setOnAction(event -> onJoin());

        loginMessage.getStyleClass().add("error");
        loginMessage.setWrapText(true);
        loginMessage.setMinHeight(36);

        GridPane card = new GridPane();
        card.getStyleClass().add("card");
        card.setVgap(10);
        card.add(eyebrow, 0, 0);
        card.add(title, 0, 1);
        card.add(target, 0, 2);
        card.add(nameLabel, 0, 3);
        card.add(username, 0, 4);
        card.add(hint, 0, 5);
        card.add(join, 0, 6);
        card.add(loginMessage, 0, 7);
        login.add(card, 1, 1);
    }

    private void buildChat() {
        chat.getStyleClass().add("client-root");
        chat.setHgap(16);
        chat.setVgap(16);
        chat.setPadding(new Insets(18));

        ColumnConstraints conversation = new ColumnConstraints();
        conversation.setPercentWidth(72);
        conversation.setHgrow(Priority.ALWAYS);
        ColumnConstraints side = new ColumnConstraints();
        side.setPercentWidth(28);
        side.setMinWidth(220);
        side.setHgrow(Priority.ALWAYS);
        chat.getColumnConstraints().addAll(conversation, side);

        RowConstraints header = new RowConstraints();
        RowConstraints body = growingRow();
        RowConstraints composerRow = new RowConstraints();
        chat.getRowConstraints().addAll(header, body, composerRow);

        chat.add(buildChatHeader(), 0, 0, 2, 1);
        chat.add(buildTranscript(), 0, 1);
        chat.add(buildSidePanel(), 1, 1);
        chat.add(buildComposer(), 0, 2, 2, 1);

        transcript.heightProperty().addListener((obs, previous, height) -> transcriptScroll.setVvalue(1));
    }

    private Parent buildChatHeader() {
        statusDot.getStyleClass().addAll("status-dot", "online");
        statusDot.setAccessibleText("Online status indicator");
        statusLabel.getStyleClass().add("status-label");
        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("status-pill");

        identity.getStyleClass().add("section-title");
        modeBadge.getStyleClass().add("badge");
        modeBadge.setVisible(false);
        modeBadge.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, status, identity, modeBadge, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Parent buildTranscript() {
        transcript.setFillWidth(true);
        transcript.setPadding(new Insets(4, 4, 12, 4));
        transcriptScroll.setFitToWidth(true);
        transcriptScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        transcriptScroll.getStyleClass().add("transcript-scroll");
        VBox panel = new VBox(transcriptScroll);
        VBox.setVgrow(transcriptScroll, Priority.ALWAYS);
        panel.getStyleClass().add("panel");
        GridPane.setHgrow(panel, Priority.ALWAYS);
        GridPane.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Parent buildSidePanel() {
        Label title = new Label("Active users");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Type allUsers and press SEND.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        Button query = new Button("allUsers");
        query.getStyleClass().add("ghost");
        query.setMaxWidth(Double.MAX_VALUE);
        query.setOnAction(event -> submitCommand("allUsers"));
        userList.getStyleClass().add("known-users");
        userList.setPlaceholder(new Label("No list yet"));
        VBox panel = new VBox(8, title, hint, query, userList);
        VBox.setVgrow(userList, Priority.ALWAYS);
        panel.getStyleClass().add("panel");
        GridPane.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Parent buildComposer() {
        composer.setPromptText("Write a message");
        composer.setOnAction(event -> onSend());
        send.getStyleClass().add("primary");
        send.setOnAction(event -> onSend());
        leave.getStyleClass().add("danger");
        leave.setOnAction(event -> {
            if (client != null) {
                client.disconnect();
            }
        });
        GridPane row = new GridPane();
        row.setHgap(10);
        ColumnConstraints field = new ColumnConstraints();
        field.setHgrow(Priority.ALWAYS);
        row.getColumnConstraints().addAll(field, new ColumnConstraints(), new ColumnConstraints());
        row.add(composer, 0, 0);
        row.add(send, 1, 0);
        row.add(leave, 2, 0);
        GridPane.setHgrow(composer, Priority.ALWAYS);
        return row;
    }

    private void onJoin() {
        if (!config.valid() || shutDown) {
            return;
        }
        String name = username.getText() == null ? "" : username.getText().trim();
        if (!name.isEmpty() && (!Protocol.validName(name) || Protocol.reservedName(name))) {
            loginMessage.setText("Use 1-20 letters, digits, spaces, '_' or '-'.");
            return;
        }
        join.setDisable(true);
        loginMessage.setText("Connecting to " + config.host() + ":" + config.port() + " ...");
        ChatClient next = new ChatClient();
        next.addListener(this);
        client = next;
        Thread.ofVirtual().name("tcp-connect").start(() -> {
            try {
                next.connect(config.host(), config.port(), name);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!inChat) {
                        join.setDisable(false);
                        loginMessage.setText(ex.getMessage());
                    }
                });
            }
        });
    }

    private void onSend() {
        if (client == null || !inChat) {
            return;
        }
        String text = composer.getText();
        composer.clear();
        client.submit(text);
    }

    private void submitCommand(String command) {
        if (client != null && inChat) {
            client.submit(command);
        }
    }

    private void showChat(String displayName, boolean readOnly) {
        myName = displayName;
        inChat = true;
        identity.setText(displayName);
        modeBadge.setVisible(readOnly);
        modeBadge.setManaged(readOnly);
        statusLabel.setText("Online");
        statusDot.getStyleClass().add("online");
        composer.clear();
        composer.setPromptText(readOnly
                ? "READ-ONLY MODE — allUsers, end, or bye"
                : "Write a message, or type allUsers");
        send.setDisable(false);
        leave.setDisable(false);
        knownUsers.clear();
        transcript.getChildren().clear();
        appendSystem(readOnly
                ? "READ-ONLY MODE. You can receive messages, list users, and leave."
                : "You joined the chat.");
        show(chat);
        retitle("TCP Client — " + displayName);
        composer.requestFocus();
    }

    private void showLogin(String notice) {
        inChat = false;
        statusLabel.setText("Offline");
        statusDot.getStyleClass().remove("online");
        join.setDisable(!config.valid());
        loginMessage.setText(notice == null ? "" : notice);
        show(login);
        retitle("TCP Client");
        username.requestFocus();
    }

    private void show(Parent next) {
        javafx.scene.Scene scene = showing.getScene();
        showing = next;
        if (scene != null) {
            scene.setRoot(next);
        }
    }

    private void retitle(String title) {
        if (showing.getScene() != null && showing.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle(title);
        }
    }

    private void appendChat(String time, String user, String text) {
        boolean mine = user.equals(myName);
        Label meta = new Label("[" + time + "]  " + user);
        meta.getStyleClass().add("bubble-meta");
        Label body = new Label(text);
        body.setWrapText(true);
        body.setMnemonicParsing(false);
        body.getStyleClass().add("bubble-text");
        VBox bubble = new VBox(2, meta, body);
        bubble.getStyleClass().add("bubble");
        bubble.getStyleClass().add(mine ? "bubble-self" : "bubble-other");
        bubble.setMaxWidth(520);
        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        transcript.getChildren().add(row);
        trimTranscript();
    }

    private void appendSystem(String text) {
        Label line = new Label(text);
        line.setWrapText(true);
        line.setMnemonicParsing(false);
        line.getStyleClass().add("system-line");
        HBox row = new HBox(line);
        row.setAlignment(Pos.CENTER);
        transcript.getChildren().add(row);
        trimTranscript();
    }

    private void trimTranscript() {
        while (transcript.getChildren().size() > 500) {
            transcript.getChildren().removeFirst();
        }
    }

    private String targetText() {
        if (!config.valid()) {
            return "Fix the command line, then start the client again.";
        }
        String source = config.fromCommandLine() ? "command line" : "client.properties";
        return "Server " + config.host() + ":" + config.port() + " (" + source + ")";
    }

    private static ColumnConstraints growingColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private static RowConstraints growingRow() {
        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        return row;
    }
}
