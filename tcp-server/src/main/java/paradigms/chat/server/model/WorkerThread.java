package paradigms.chat.server.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * One accepted client. Same shape as the course {@code WorkerThread}:
 * {@code readUTF} / {@code writeUTF} on {@code socketForClient}.
 * {@code ChatServer} starts each task with {@code Thread.ofVirtual().start(task)}.
 */
public final class WorkerThread implements Runnable {

    private final Socket socketForClient;
    private final ChatServer server;
    private final Object writeLock = new Object();
    private DataOutputStream dout;
    private volatile boolean open = true;

    WorkerThread(Socket socketForClient, ChatServer server) {
        this.socketForClient = socketForClient;
        this.server = server;
    }

    @Override
    public void run() {
        try (socketForClient) {
            DataInputStream dis = new DataInputStream(socketForClient.getInputStream());
            synchronized (writeLock) {
                dout = new DataOutputStream(socketForClient.getOutputStream());
            }
            while (open && server.isRunning()) {
                String msg = dis.readUTF();
                server.handleLine(this, msg);
            }
        } catch (IOException ignored) {
            // The peer hung up, or the server closed the socket.
        } finally {
            open = false;
            server.onSessionClosed(this);
        }
    }

    void send(String msg) {
        synchronized (writeLock) {
            if (!open || dout == null) {
                return;
            }
            try {
                dout.writeUTF(msg);
                dout.flush();
            } catch (IOException ex) {
                close();
            }
        }
    }

    void close() {
        open = false;
        try {
            socketForClient.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    String remoteLabel() {
        return socketForClient.getInetAddress().getHostAddress() + ":" + socketForClient.getPort();
    }
}
