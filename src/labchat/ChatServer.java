package labchat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Starts the dependency-free LAB CHAT HTTP server. */
public final class ChatServer {
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? DEFAULT_PORT : parsePort(args[0]);
        ChatState state = new ChatState();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        ExecutorService workers = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors() * 2));
        server.setExecutor(workers);
        server.createContext("/api", new ApiHandler(state));
        server.createContext("/", new StaticFileHandler(Path.of("web")));
        server.start();

        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() ->
                state.removeInactiveUsers().forEach(name -> System.out.println("[LEAVE] " + name)), 15, 15, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); workers.shutdown(); cleaner.shutdown(); }));

        System.out.println("========================================");
        System.out.println("LAB CHAT SERVER");
        System.out.println("========================================");
        System.out.println("Server started");
        System.out.println("Address: " + lanAddress());
        System.out.println("Port: " + port);
        System.out.println("Open: http://" + lanAddress() + ":" + port);
    }

    private static int parsePort(String input) {
        try { int port = Integer.parseInt(input); if (port >= 1 && port <= 65535) return port; } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Port must be a number from 1 to 65535.");
    }

    private static String lanAddress() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(network -> {
                        try { return network.isUp() && !network.isLoopback() && !network.isVirtual(); }
                        catch (Exception ignored) { return false; }
                    })
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(address -> address.isSiteLocalAddress() && !address.isLoopbackAddress())
                    .map(InetAddress::getHostAddress)
                    .findFirst().orElse("SERVER_IP");
        } catch (Exception ignored) { return "SERVER_IP"; }
    }
}
