package paradigms.chat.server.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import paradigms.chat.protocol.Protocol;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChatServerTest {

    private ChatServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void logsStartupAndWelcome() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        server = new ChatServer("127.0.0.1", 0);
        server.addListener(new ServerListener() {
            @Override
            public void onLog(String message) {
                logs.add(message);
            }
        });
        server.start();

        waitUntil(() -> logs.contains("Server Started") && logs.contains("Waiting for Client"));
        try (Peer alice = new Peer("127.0.0.1", server.port())) {
            alice.send(Protocol.join("Alice"));
            assertTrue(alice.readMatching(Protocol.WELCOME).contains("Alice"));
        }
        waitUntil(() -> logs.contains("Welcome Alice"));
    }

    @Test
    void broadcastsChatWithTimeAndUsername() throws Exception {
        server = started();
        try (Peer alice = new Peer("127.0.0.1", server.port());
             Peer bob = new Peer("127.0.0.1", server.port())) {
            alice.send(Protocol.join("Alice"));
            bob.send(Protocol.join("Bob"));
            assertTrue(alice.readMatching(Protocol.WELCOME).startsWith("WELCOME|Alice|ACTIVE"));
            assertTrue(bob.readMatching(Protocol.WELCOME).startsWith("WELCOME|Bob|ACTIVE"));

            alice.send(Protocol.message("hello|team"));
            String toAlice = alice.readMatching(Protocol.CHAT);
            String toBob = bob.readMatching(Protocol.CHAT);
            Protocol.Chat chat = Protocol.parseChat(toBob);
            assertEquals(toAlice, toBob);
            assertEquals("Alice", chat.username());
            assertEquals("hello|team", chat.text());
            assertTrue(chat.time().matches("\\d{2}:\\d{2}:\\d{2}"));
        }
    }

    @Test
    void allUsersReachesOnlyTheRequester() throws Exception {
        AtomicReference<List<ConnectedClient>> online = new AtomicReference<>(List.of());
        server = new ChatServer("127.0.0.1", 0);
        server.addListener(new ServerListener() {
            @Override
            public void onClientsChanged(List<ConnectedClient> clients) {
                online.set(clients);
            }
        });
        server.start();

        try (Peer alice = new Peer("127.0.0.1", server.port());
             Peer bob = new Peer("127.0.0.1", server.port())) {
            alice.send(Protocol.join("Alice"));
            bob.send(Protocol.join("Bob"));
            alice.readMatching(Protocol.WELCOME);
            bob.readMatching(Protocol.WELCOME);
            alice.readMatching(Protocol.SYS);

            alice.send(Protocol.command(Protocol.CMD_ALL_USERS));
            List<String> names = Protocol.parseUsers(alice.readMatching(Protocol.USERS));
            assertTrue(names.contains("Alice") && names.contains("Bob"), names.toString());
            assertEquals(2, names.size());
            assertEquals(2, online.get().size());
            assertNotEquals(online.get().get(0).colorHex(), online.get().get(1).colorHex());

            assertFalse(bob.drain().stream().anyMatch(line -> line.startsWith(Protocol.USERS)),
                    "allUsers must be answered only to the client who asked");
        }
    }

    @Test
    void readOnlyClientReceivesButCannotSend() throws Exception {
        server = started();
        try (Peer guest = new Peer("127.0.0.1", server.port());
             Peer alice = new Peer("127.0.0.1", server.port())) {
            guest.send(Protocol.join(""));
            String welcome = guest.readMatching(Protocol.WELCOME);
            assertEquals("WELCOME|Guest-1|READONLY", welcome);

            alice.send(Protocol.join("Alice"));
            alice.readMatching(Protocol.WELCOME);
            alice.send(Protocol.message("hi"));

            Protocol.Chat chat = Protocol.parseChat(guest.readMatching(Protocol.CHAT));
            assertEquals("Alice", chat.username());
            assertEquals("hi", chat.text());

            guest.send(Protocol.message("nope"));
            assertTrue(guest.readMatching(Protocol.ERR).contains("READ-ONLY MODE"));

            alice.send(Protocol.command(Protocol.CMD_ALL_USERS));
            assertTrue(Protocol.parseUsers(alice.readMatching(Protocol.USERS)).contains("Guest-1 (read-only)"));
        }
    }

    @Test
    void leaveNotifiesTheRemainingClient() throws Exception {
        AtomicReference<List<ConnectedClient>> online = new AtomicReference<>(List.of());
        server = new ChatServer("127.0.0.1", 0);
        server.addListener(new ServerListener() {
            @Override
            public void onClientsChanged(List<ConnectedClient> clients) {
                online.set(clients);
            }
        });
        server.start();
        try (Peer alice = new Peer("127.0.0.1", server.port());
             Peer bob = new Peer("127.0.0.1", server.port())) {
            alice.send(Protocol.join("Alice"));
            bob.send(Protocol.join("Bob"));
            alice.readMatching(Protocol.WELCOME);
            bob.readMatching(Protocol.WELCOME);
            alice.readMatching(Protocol.SYS);

            alice.send(Protocol.command(Protocol.CMD_LEAVE));
            assertTrue(alice.readMatching(Protocol.BYE).startsWith("BYE|"));
            assertTrue(bob.readContaining("Alice left the chat.").contains("Alice left the chat."));
            waitUntil(() -> online.get().size() == 1 && "Bob".equals(online.get().get(0).displayName()));
        }
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        server = started();
        try (Peer alice = new Peer("127.0.0.1", server.port());
             Peer copy = new Peer("127.0.0.1", server.port())) {
            alice.send(Protocol.join("Alice"));
            alice.readMatching(Protocol.WELCOME);
            copy.send(Protocol.join("alice"));
            assertTrue(copy.readMatching(Protocol.ERR).contains("already in use"));
        }
    }

    @Test
    void modelSourcesDoNotReferenceJavaFx() throws IOException {
        assertNoJavaFx(Path.of("src/main/java/paradigms/chat/server/model"));
        assertNoJavaFx(Path.of("src/main/java/paradigms/chat/server/config"));
        assertNoJavaFx(Path.of("../shared/java"));
    }

    private ChatServer started() throws IOException {
        server = new ChatServer("127.0.0.1", 0);
        server.start();
        return server;
    }

    private static void assertNoJavaFx(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), root.toString());
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("javafx"), file + " must stay independent of JavaFX");
            }
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for server state");
            }
            Thread.sleep(20);
        }
    }

    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        Peer(String host, int port) throws IOException {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void send(String line) {
            out.println(line);
        }

        String readMatching(String prefix) throws IOException {
            return readMatching(line -> line.startsWith(prefix), prefix);
        }

        String readContaining(String text) throws IOException {
            return readMatching(line -> line.contains(text), text);
        }

        List<String> drain() throws IOException {
            socket.setSoTimeout(400);
            List<String> lines = new ArrayList<>();
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } catch (SocketTimeoutException expected) {
                return lines;
            }
            return lines;
        }

        private String readMatching(java.util.function.Predicate<String> match, String description) throws IOException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                String line = in.readLine();
                if (line == null) {
                    throw new EOFException("Connection closed while waiting for " + description);
                }
                if (match.test(line)) {
                    return line;
                }
            }
            throw new SocketTimeoutException(description);
        }

        Socket socket() {
            return socket;
        }

        BufferedReader reader() {
            return in;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
