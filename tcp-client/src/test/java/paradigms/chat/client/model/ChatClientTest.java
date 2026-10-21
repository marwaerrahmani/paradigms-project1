package paradigms.chat.client.model;

import org.junit.jupiter.api.Test;
import paradigms.chat.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatClientTest {

    @Test
    void commandsAndChatRoundTrip() throws Exception {
        List<String> inbox = new CopyOnWriteArrayList<>();
        List<String> chats = new CopyOnWriteArrayList<>();
        List<List<String>> userLists = new CopyOnWriteArrayList<>();
        List<String> goodbyes = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(3);

        try (ServerSocket server = new ServerSocket(0)) {
            Thread peer = Thread.ofVirtual().start(() -> serveActive(server, inbox));
            ChatClient client = new ChatClient();
            client.addListener(new ClientListener() {
                @Override
                public void onChat(String time, String username, String text) {
                    chats.add(time + "|" + username + "|" + text);
                    done.countDown();
                }

                @Override
                public void onUsers(List<String> users) {
                    userLists.add(List.copyOf(users));
                    done.countDown();
                }

                @Override
                public void onDisconnected(String reason) {
                    goodbyes.add(reason);
                    done.countDown();
                }
            });

            client.connect("127.0.0.1", server.getLocalPort(), "Alice");
            assertEquals("Alice", client.displayName());
            assertFalse(client.isReadOnly());
            client.submit("hello");
            client.submit("allUsers");
            client.submit("bye");

            assertTrue(done.await(3, TimeUnit.SECONDS), "inbox=" + inbox + " chats=" + chats + " users=" + userLists);
            peer.join(TimeUnit.SECONDS.toMillis(3));
            assertFalse(peer.isAlive(), inbox.toString());
            assertTrue(inbox.contains(Protocol.join("Alice")));
            assertTrue(inbox.contains(Protocol.message("hello")));
            assertTrue(inbox.contains(Protocol.command(Protocol.CMD_ALL_USERS)));
            assertTrue(inbox.contains(Protocol.command(Protocol.CMD_LEAVE)));
            assertEquals(List.of("12:00:00|Alice|hello"), List.copyOf(chats));
            assertEquals(List.of("Alice", "Bob"), userLists.getFirst());
            assertEquals(List.of("Goodbye"), List.copyOf(goodbyes));
        }
    }

    @Test
    void readOnlyModeDoesNotSendChat() throws Exception {
        List<String> inbox = new CopyOnWriteArrayList<>();
        List<String> notices = new CopyOnWriteArrayList<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread peer = Thread.ofVirtual().start(() -> serveReadOnly(server, inbox));
            ChatClient client = new ChatClient();
            client.addListener(new ClientListener() {
                @Override
                public void onSystem(String text) {
                    notices.add(text);
                }
            });
            client.connect("127.0.0.1", server.getLocalPort(), "  ");
            assertTrue(client.isReadOnly());
            assertEquals("Guest-1", client.displayName());
            client.submit("hello");
            client.submit("end");
            peer.join(3000);
            assertFalse(peer.isAlive(), inbox.toString());
        }
        assertTrue(notices.stream().anyMatch(text -> text.contains("READ-ONLY MODE")));
        assertTrue(inbox.stream().noneMatch(line -> line.startsWith(Protocol.MSG)));
        assertTrue(inbox.stream().anyMatch(line -> line.equals(Protocol.command(Protocol.CMD_LEAVE))));
    }

    @Test
    void interpretsAssignmentCommands() {
        assertEquals(InputIntent.LIST_USERS, InputIntent.from("allUsers"));
        assertEquals(InputIntent.LEAVE, InputIntent.from("end"));
        assertEquals(InputIntent.LEAVE, InputIntent.from("bye"));
        assertEquals(InputIntent.CHAT, InputIntent.from("AllUsers"));
        assertEquals(InputIntent.CHAT, InputIntent.from("hello"));
    }

    @Test
    void modelSourcesDoNotReferenceJavaFx() throws IOException {
        assertNoJavaFx(Path.of("src/main/java/paradigms/chat/client/model"));
        assertNoJavaFx(Path.of("src/main/java/paradigms/chat/client/config"));
        assertNoJavaFx(Path.of("../shared/java"));
    }

    private static void serveActive(ServerSocket server, List<String> inbox) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(3000);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            inbox.add(dis.readUTF());
            write(dout, Protocol.welcome("Alice", false));
            inbox.add(dis.readUTF());
            write(dout, Protocol.chat("12:00:00", "Alice", "hello"));
            inbox.add(dis.readUTF());
            write(dout, Protocol.users(List.of("Alice", "Bob")));
            inbox.add(dis.readUTF());
            write(dout, Protocol.bye());
        } catch (IOException ex) {
            inbox.add("ERROR " + ex.getMessage());
        }
    }

    private static void serveReadOnly(ServerSocket server, List<String> inbox) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(3000);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
            inbox.add(dis.readUTF());
            write(dout, Protocol.welcome("Guest-1", true));
            try {
                while (true) {
                    String msg = dis.readUTF();
                    inbox.add(msg);
                    if (Protocol.command(Protocol.CMD_LEAVE).equals(msg)) {
                        write(dout, Protocol.bye());
                        break;
                    }
                }
            } catch (EOFException ignored) {
                // The client closed the socket.
            }
        } catch (IOException ex) {
            inbox.add("ERROR " + ex.getMessage());
        }
    }

    private static void write(DataOutputStream dout, String msg) throws IOException {
        dout.writeUTF(msg);
        dout.flush();
    }

    private static void assertNoJavaFx(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), root.toString());
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(file).contains("javafx"), file.toString());
            }
        }
    }
}
