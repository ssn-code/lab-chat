# Lab Chat

Dependency-free LAN chat with Java 21's built-in `HttpServer` and browser-native HTML, CSS, and JavaScript. Messages and active users are held only in memory.

From this directory, compile and run:

```bash
javac --add-modules jdk.httpserver -d out src/labchat/*.java
java --add-modules jdk.httpserver -cp out labchat.ChatServer
```

The default port is `5000`; supply another as the first argument, for example `... ChatServer 8080`. Open the displayed LAN URL from lab computers. If another machine cannot connect, ask the administrator to allow inbound TCP traffic on that port—do not change firewall settings yourself.

API: `POST /api/join`, `POST /api/send`, `POST /api/ping`, `GET /api/messages`, and `GET /api/users`. Names are limited to 20 characters and messages to 500. Browser rendering uses `textContent`, so user content is not interpreted as HTML.
