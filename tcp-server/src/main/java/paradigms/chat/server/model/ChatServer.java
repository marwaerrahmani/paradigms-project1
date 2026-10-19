package paradigms.chat.server.model;

import paradigms.chat.protocol.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts concurrent TCP clients and distributes chat lines.
 * Business logic lives here and does not reference JavaFX. The view observes
 * {@link ServerListener} and can be replaced without changing this class.
 */
public final class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_CLIENTS = 200;
    private static final String[] PALETTE = {
            "#7EB6FF", "#F7C56B", "#8EE0B0", "#F5A3C7", "#C9B6FF",
            "#FFB08A", "#9AD7E8", "#E7E29A", "#F2B6A0", "#B7E4C7"
    };

    private final String bindAddress;
    private final int requestedPort;
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();
    private final Object stateLock = new Object();
    private final Map<ClientSession, ConnectedClient> clients = new LinkedHashMap<>();
    private final AtomicInteger guestSequence = new AtomicInteger();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private int colorIndex;

    public ChatServer(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.colorIndex = (int) (System.nanoTime() % PALETTE.length);
        if (this.colorIndex < 0) {
            this.colorIndex = 0;
        }
    }

    public void addListener(ServerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void start() throws IOException {
        synchronized (stateLock) {
            if (running) {
                throw new IllegalStateException("Server is already running.");
            }
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            try {
                socket.bind(new InetSocketAddress(bindAddress, requestedPort));
            } catch (IOException ex) {
                socket.close();
                throw new IOException(
                        "Could not listen on " + bindAddress + ":" + requestedPort + ". " + ex.getMessage(), ex);
            }
            serverSocket = socket;
            running = true;
        }
        log("Server Started");
        notifyState();
        Thread.ofVirtual().name("tcp-accept").start(this::acceptLoop);
    }

    public void stop() {
        List<ClientSession> sessions;
        synchronized (stateLock) {
            if (!running) {
                return;
            }
            running = false;
            sessions = new ArrayList<>(clients.keySet());
            clients.clear();
        }
        for (ClientSession session : sessions) {
            session.close();
        }
        closeServerSocket();
        publishClients();
        notifyState();
        log("Server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int port() {
        ServerSocket socket = serverSocket;
        if (socket == null) {
            return requestedPort;
        }
        return socket.getLocalPort();
    }

    public String bindAddress() {
        return bindAddress;
    }

    private void acceptLoop() {
        log("Waiting for Client");
        while (running) {
            try {
                Socket incoming = serverSocket.accept();
                incoming.setTcpNoDelay(true);
                ClientSession session = new ClientSession(incoming, this);
                Thread.ofVirtual().name("tcp-client-" + incoming.getPort()).start(session);
            } catch (IOException ex) {
                if (running) {
                    log("Accept failed: " + ex.getMessage());
                }
                break;
            }
        }
    }

    void handleLine(ClientSession session, String raw) {
        if (raw == null) {
            return;
        }
        String line = raw.trim();
        if (line.isEmpty() || !running) {
            return;
        }
        if (!isJoined(session)) {
            if (!Protocol.JOIN.equals(Protocol.typeOf(line))) {
                reject(session, "The first message must be JOIN.");
                return;
            }
            join(session, Protocol.payload(line));
            return;
        }
        switch (Protocol.typeOf(line)) {
            case Protocol.MSG -> onMessage(session, Protocol.payload(line));
            case Protocol.CMD -> onCommand(session, Protocol.payload(line));
            case Protocol.JOIN -> session.send(Protocol.error("Already joined."));
            default -> session.send(Protocol.error("Unknown message type."));
        }
    }

    void onSessionClosed(ClientSession session) {
        ConnectedClient removed;
        synchronized (stateLock) {
            removed = clients.remove(session);
        }
        if (removed == null) {
            return;
        }
        log(removed.displayName() + " disconnected");
        broadcast(Protocol.system(removed.displayName() + " left the chat."), null);
        publishClients();
        if (running) {
            log("Waiting for Client");
        }
    }

    private void join(ClientSession session, String rawName) {
        String requested = rawName == null ? "" : rawName.trim();
        ConnectedClient client;
        String error;
        synchronized (stateLock) {
            error = rejectionReason(requested);
            if (error != null) {
                client = null;
            } else if (clients.size() >= MAX_CLIENTS) {
                error = "The server is full.";
                client = null;
            } else {
                boolean readOnly = requested.isEmpty();
                String displayName = readOnly ? "Guest-" + guestSequence.incrementAndGet() : requested;
                client = new ConnectedClient(displayName, readOnly, nextColor());
                clients.put(session, client);
            }
        }
        if (error != null) {
            log("Rejected " + session.remoteLabel() + ": " + error);
            reject(session, error);
            return;
        }
        log("Welcome " + client.displayName());
        publishClients();
        session.send(Protocol.welcome(client.displayName(), client.readOnly()));
        broadcast(Protocol.system(client.displayName() + " joined the chat."), session);
    }

    private String rejectionReason(String requested) {
        if (requested.isEmpty()) {
            return null;
        }
        if (!Protocol.validName(requested) || Protocol.reservedName(requested)) {
            return "Username must be 1-20 characters: letters, digits, spaces, '_' or '-'.";
        }
        boolean taken = clients.values().stream()
                .anyMatch(existing -> existing.displayName().equalsIgnoreCase(requested));
        if (taken) {
            return "Username is already in use.";
        }
        return null;
    }

    private void onMessage(ClientSession session, String rawText) {
        ConnectedClient client = clientOf(session);
        if (client == null) {
            return;
        }
        if (client.readOnly()) {
            session.send(Protocol.error("READ-ONLY MODE: you cannot send messages."));
            return;
        }
        String text = rawText == null ? "" : rawText.replace("\r", "").trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > Protocol.MAX_TEXT) {
            session.send(Protocol.error("Message is too long."));
            return;
        }
        String time = LocalTime.now().format(TIME);
        broadcast(Protocol.chat(time, client.displayName(), text), null);
        log(client.displayName() + ": " + text);
    }

    private void onCommand(ClientSession session, String command) {
        ConnectedClient client = clientOf(session);
        if (client == null) {
            return;
        }
        if (Protocol.CMD_ALL_USERS.equals(command)) {
            session.send(Protocol.users(labels()));
            log("allUsers requested by " + client.displayName());
            return;
        }
        if (Protocol.CMD_LEAVE.equals(command)) {
            session.send(Protocol.bye());
            session.close();
            return;
        }
        session.send(Protocol.error("Unknown command."));
    }

    private void reject(ClientSession session, String error) {
        session.send(Protocol.error(error));
        session.close();
    }

    private boolean isJoined(ClientSession session) {
        synchronized (stateLock) {
            return clients.containsKey(session);
        }
    }

    private ConnectedClient clientOf(ClientSession session) {
        synchronized (stateLock) {
            return clients.get(session);
        }
    }

    private List<String> labels() {
        synchronized (stateLock) {
            return clients.values().stream().map(ConnectedClient::listLabel).toList();
        }
    }

    private String nextColor() {
        String color = PALETTE[colorIndex % PALETTE.length];
        colorIndex++;
        return color;
    }

    private void broadcast(String line, ClientSession except) {
        List<ClientSession> targets;
        synchronized (stateLock) {
            targets = new ArrayList<>(clients.keySet());
        }
        for (ClientSession session : targets) {
            if (session != except) {
                session.send(line);
            }
        }
    }

    private void publishClients() {
        List<ConnectedClient> snapshot;
        synchronized (stateLock) {
            snapshot = List.copyOf(clients.values());
        }
        for (ServerListener listener : listeners) {
            try {
                listener.onClientsChanged(snapshot);
            } catch (RuntimeException ignored) {
                // A view failure must not drop the connection.
            }
        }
    }

    private void notifyState() {
        boolean up = running;
        int port = port();
        for (ServerListener listener : listeners) {
            try {
                listener.onStateChanged(up, bindAddress, port);
            } catch (RuntimeException ignored) {
                // Keep the accept loop alive.
            }
        }
    }

    private void log(String message) {
        for (ServerListener listener : listeners) {
            try {
                listener.onLog(message);
            } catch (RuntimeException ignored) {
                // Keep the accept loop alive.
            }
        }
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }
}
