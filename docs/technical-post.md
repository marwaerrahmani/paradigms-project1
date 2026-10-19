# Building a multi-client chat with Java sockets and JavaFX

This is a draft you can publish on Dev.to or Medium. Replace the byline with your name before posting.

## The problem

The assignment was a group chat: many desktop clients, one server, plain TCP. The server had to show who is connected, color each person differently, and write a log a grader can recognize (`Server Started`, `Waiting for Client`, `Welcome Alice`). Clients had to ask for a username, fall back to read-only mode when the name was left blank, and understand three typed commands: `allUsers`, `end`, and `bye`.

JavaFX was required for the UI, and the network code had to keep working if the screens changed.

## Separating the model from the view

The first design choice was an observer, not a framework. `ChatServer` accepts sockets, reads lines, and calls `ServerListener`. It never imports JavaFX. `ServerView` implements that listener and hops to the JavaFX thread with `Platform.runLater` before touching a control. The client is the same shape: `ChatClient` and `ClientListener`, with `ClientView` as the only class that builds the `GridPane`.

A unit test walks the model sources and fails the build if the word `javafx` appears. That keeps the rule honest.

## Concurrency

Each accepted socket runs on a virtual thread (`Thread.ofVirtual()`). The accept loop is another virtual thread, so the server window stays responsive while it waits. Broadcasts copy the session list under a lock and write to each socket outside that lock, so a slow client cannot stall the bookkeeping.

## A small text protocol

Messages are single UTF-8 lines. `JOIN|Alice` enters. `MSG|hello` is chat. `CMD|allUsers` and `CMD|leave` are commands. The server answers with `WELCOME`, `CHAT|time|user|text`, `SYS`, `USERS`, `ERR`, or `BYE`. Chat text may contain `|` because it is always the last field.

The words `end` and `bye` never go on the wire as chat. The client turns them into `CMD|leave` before sending. `allUsers` is answered only to the socket that asked.

## Read-only mode

An empty username is not an error. The server assigns `Guest-1`, `Guest-2`, and so on, marks the session read-only, and still delivers everyone else's messages. The client disables nothing in the text field, because the user must still be able to type `allUsers` or `bye`. A normal sentence is rejected locally and again on the server.

## Configuration

`server.properties` and `client.properties` hold the bind address, port, and default host. A file next to the jar overrides the copy inside the jar. The client command line overrides the file:

```
java TCPClient localhost 3000
```

JavaFX is not part of the JDK anymore, so the real launch command also puts the JavaFX jars on the module path. The `.bat` files in `artifacts` do that.

## What I would do next

The chat is intentionally open: no passwords and no TLS. A course project that added accounts would keep the same model/view split and only change `ChatServer.join`. History is not replayed to late joiners; a short ring buffer in the model would fix that without touching the view.
