# Lab Chat

Dependency-free LAN chat using Java 21's built-in `HttpServer` and browser-native HTML, CSS, and JavaScript. Chat state is held in memory; uploads are stored in `data/uploads`.

## Requirements

- Java 21 or newer (virtual threads are used by the server)
- A modern browser

## Run

From this directory (`lab-chat`), compile every Java source file and start the server:

```bash
rm -rf out
mkdir -p out
javac --add-modules jdk.httpserver -d out $(find src -name '*.java')
java --add-modules jdk.httpserver -cp out labchat.ChatServer
```

Open <http://localhost:5000>. The server prints LAN addresses for other computers. If a LAN client cannot connect, ask the network administrator to allow inbound TCP traffic on the selected port.

Use another port with:

```bash
java --add-modules jdk.httpserver -cp out labchat.ChatServer --port 8080
```

Other options include `--host`, `--max-users`, `--max-message-length`, `--max-file-size`, `--history-size`, and `--admin-token`.

## Features

- Public rooms, direct messages, presence, and inactive-user cleanup
- Message history, replies, editing, deletion, reactions, pins, and search
- File uploads/downloads (25 MB per file by default)
- Polls, announcements, and administrator tools
- Themes, bookmarks, notifications, and keyboard shortcuts

## API overview

The browser uses JSON endpoints under `/api`: users (`/api/join`, `/api/guest`, `/api/leave`, `/api/presence`, `/api/users`), messages (`/api/messages`, `/api/send`, `/api/private/*`, `/api/message/*`, `/api/pinned`), rooms and polls (`/api/rooms/*`, `/api/poll/*`), files (`/api/upload`, `/api/download`), and administration (`/api/admin/*`, `/api/server/status`).

Names are limited to 20 characters and messages to 2,000 characters by default. Browser rendering uses `textContent`, so user content is not interpreted as HTML.

## Development check

```bash
rm -rf /tmp/labchat-out
mkdir -p /tmp/labchat-out
javac --add-modules jdk.httpserver -d /tmp/labchat-out $(find src -name '*.java')
```

Run from the project directory so `web/` and `data/uploads/` resolve correctly.
