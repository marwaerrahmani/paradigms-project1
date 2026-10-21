package paradigms.chat.client.model;

import paradigms.chat.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One TCP connection to the chat server. Call {@link #connect} off the JavaFX
 * thread; listener methods may run on the reader thread or the caller thread.
 */
public final class ChatClient {

    private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();
    private final CountDownLatch welcomeLatch = new CountDownLatch(1);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dout;
    private volatile String failure;
    private volatile boolean welcomed;
    private volatile boolean readOnly;
    private volatile String displayName = "";

    public void addListener(ClientListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void connect(String host, int port, String username) throws IOException {
        if (finished.get() || socket != null) {
            throw new IOException("This client was already used.");
        }
        Socket opening = new Socket();
        try {
            opening.connect(new InetSocketAddress(host, port), 4000);
        } catch (UnknownHostException ex) {
            throw new IOException("Unknown server host: " + host, ex);
        } catch (IOException ex) {
            throw new IOException(
                    "Could not connect to " + host + ":" + port + ". Start TCPServer and check the address.", ex);
        }
        opening.setTcpNoDelay(true);
        socket = opening;
        dis = new DataInputStream(opening.getInputStream());
        synchronized (writeLock) {
            dout = new DataOutputStream(opening.getOutputStream());
        }
        Thread.ofVirtual().name("tcp-client-reader").start(this::readLoop);
        send(Protocol.join(username == null ? "" : username.trim()));
        awaitWelcome();
    }

    public void submit(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || !connected.get()) {
            return;
        }
        try {
            switch (InputIntent.from(text)) {
                case LEAVE -> disconnect();
                case LIST_USERS -> send(Protocol.command(Protocol.CMD_ALL_USERS));
                case CHAT -> sendChat(text);
            }
        } catch (IOException ex) {
            finish(ex.getMessage());
        }
    }

    public void disconnect() {
        if (finished.get()) {
            return;
        }
        try {
            if (dout != null) {
                send(Protocol.command(Protocol.CMD_LEAVE));
            } else {
                finish("Disconnected.");
            }
        } catch (IOException ex) {
            finish(ex.getMessage());
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public String displayName() {
        return displayName;
    }

    private void sendChat(String text) throws IOException {
        if (readOnly) {
            fire(listener -> listener.onSystem("READ-ONLY MODE: you cannot send messages."));
            return;
        }
        if (text.length() > Protocol.MAX_TEXT) {
            fire(listener -> listener.onError("Message is too long."));
            return;
        }
        send(Protocol.message(text));
    }

    private void awaitWelcome() throws IOException {
        try {
            if (!welcomeLatch.await(5, TimeUnit.SECONDS)) {
                failure = "Connected, but the server sent no welcome.";
                finish(failure);
                throw new IOException(failure);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            finish("Interrupted.");
            throw new IOException("Interrupted while waiting for the server.", ex);
        }
        if (failure != null) {
            String message = failure;
            finish(message);
            throw new IOException(message);
        }
    }

    private void readLoop() {
        try {
            while (true) {
                dispatch(dis.readUTF());
            }
        } catch (IOException ex) {
            finish(failure != null ? failure : "Connection closed.");
        }
    }

    private void dispatch(String line) {
        switch (Protocol.typeOf(line)) {
            case Protocol.WELCOME -> onWelcome(line);
            case Protocol.CHAT -> onChat(line);
            case Protocol.SYS -> fire(listener -> listener.onSystem(Protocol.payload(line)));
            case Protocol.USERS -> fire(listener -> listener.onUsers(Protocol.parseUsers(line)));
            case Protocol.ERR -> onError(line);
            case Protocol.BYE -> finish(blankTo(Protocol.payload(line), "Goodbye"));
            default -> {
                // Ignore unexpected lines so one bad frame cannot kill the client.
            }
        }
    }

    private void onWelcome(String line) {
        Protocol.Welcome welcome = Protocol.parseWelcome(line);
        displayName = welcome.displayName();
        readOnly = welcome.readOnly();
        welcomed = true;
        connected.set(true);
        fire(listener -> listener.onWelcome(displayName, readOnly));
        welcomeLatch.countDown();
    }

    private void onChat(String line) {
        Protocol.Chat chat = Protocol.parseChat(line);
        fire(listener -> listener.onChat(chat.time(), chat.username(), chat.text()));
    }

    private void onError(String line) {
        String message = Protocol.payload(line);
        if (!welcomed) {
            failure = message;
            welcomeLatch.countDown();
        }
        fire(listener -> listener.onError(message));
    }

    private void send(String msg) throws IOException {
        synchronized (writeLock) {
            if (dout == null) {
                throw new IOException("Not connected.");
            }
            dout.writeUTF(msg);
            dout.flush();
        }
    }

    private void finish(String reason) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        connected.set(false);
        closeSocket();
        String text = reason == null || reason.isBlank() ? "Disconnected." : reason;
        fire(listener -> listener.onDisconnected(text));
    }

    private void closeSocket() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    private void fire(Consumer<ClientListener> call) {
        for (ClientListener listener : listeners) {
            try {
                call.accept(listener);
            } catch (RuntimeException ignored) {
                // The view must not break the socket loop.
            }
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
