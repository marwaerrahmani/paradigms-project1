package paradigms.chat.server.model;

import paradigms.chat.protocol.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * One accepted TCP connection. The server runs each session on its own virtual thread.
 */
public final class ClientSession implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private final Object writeLock = new Object();
    private PrintWriter out;
    private volatile boolean open = true;

    ClientSession(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (socket) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            synchronized (writeLock) {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            }
            String line;
            while (open && server.isRunning() && (line = in.readLine()) != null) {
                server.handleLine(this, line);
            }
        } catch (IOException ignored) {
            // The peer hung up, or the server closed the socket.
        } finally {
            open = false;
            server.onSessionClosed(this);
        }
    }

    void send(String line) {
        synchronized (writeLock) {
            if (!open || out == null) {
                return;
            }
            out.println(line);
            if (out.checkError()) {
                close();
            }
        }
    }

    void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    String remoteLabel() {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }
}
