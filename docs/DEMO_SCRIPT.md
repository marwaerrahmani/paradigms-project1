# 3-minute demo script

Record the screen with the IDE and the running apps. Speak while you show the code, then switch to the live windows. Aim for just under 3 minutes.

## 0:00 – 0:35 Architecture

1. Open `README.md` and the class diagram in `docs/uml/class-diagram.puml`.
2. Say that `ChatServer` and `ChatClient` own the sockets and do not import JavaFX.
3. Point at `ServerView` and `ClientView`: they implement the listener interfaces and only update the `GridPane` screens.
4. Open `tcp-server/src/main/resources/server.properties` and say the port can change without recompiling.

## 0:35 – 1:10 Server

1. Open `ChatServer` and `WorkerThread`. Show `Thread.ofVirtual().start(task)` and `readUTF` / `writeUTF`.
2. Show `join`: empty username becomes `Guest-n` and read-only; a real name must be unique.
3. Show `onMessage`: the server adds the time and username, then broadcasts `CHAT|...` to every connection.
4. Show the log lines `Server Started`, `Waiting for Client`, and `Welcome Alice`.

## 1:10 – 1:35 Client

1. Open `InputIntent`. `allUsers` lists users. `end` and `bye` leave. Anything else is chat.
2. Open `ClientView` and show the `GridPane` login, the Online circle, SEND, and LEAVE.
3. Mention that a blank username keeps the field usable for commands but blocks normal messages.

## 1:35 – 3:00 Live demo

1. Start `artifacts\TCPServer\TCPServer.bat`. Show **Online**, the log, and the empty user list.
2. Start two clients:
   - `artifacts\TCPClient\TCPClient.bat localhost 3000` as **Alice**
   - another one as **Bob**
3. Send a message from Alice with the SEND button. Send one from Bob with Enter.
4. On the server, show both names with different background colors and the activity log.
5. Start a third client with an empty username. Show the **READ-ONLY MODE** badge. Try to send `hello` and show the refusal. Type `allUsers` and show the list.
6. On Alice, type `bye`. On Bob, click **LEAVE**. Show the server log return to `Waiting for Client` and the other client’s “left the chat” line.

Stop the recording after Bob has left.
