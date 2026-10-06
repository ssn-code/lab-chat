package labchat;

import com.sun.net.httpserver.HttpServer;
import labchat.config.ServerConfig;
import labchat.handler.*;
import labchat.service.*;
import labchat.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ChatServer {

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.parse(args);

        // Print Startup Banner
        System.out.println("========================================");
        System.out.println("             LAB CHAT SERVER            ");
        System.out.println("========================================");
        System.out.println("Host    : " + config.getHost());
        System.out.println("Port    : " + config.getPort());

        // Initialize Services
        UserService userService = new UserService(config);
        RoomService roomService = new RoomService();
        ChatService chatService = new ChatService(config, userService);
        FileService fileService = new FileService(config);
        PollService pollService = new PollService();
        AdminService adminService = new AdminService(config, userService, roomService, chatService, fileService);

        // Initialize Handlers
        UserHandler userHandler = new UserHandler(config, userService, chatService);
        MessageHandler messageHandler = new MessageHandler(config, chatService, userService, fileService);
        RoomHandler roomHandler = new RoomHandler(roomService, userService, chatService);
        FileHandler fileHandler = new FileHandler(config, fileService, userService);
        PollHandler pollHandler = new PollHandler(pollService, userService, chatService);
        AdminHandler adminHandler = new AdminHandler(adminService, userService, roomService, chatService);

        ApiHandler apiHandler = new ApiHandler(userHandler, messageHandler, roomHandler, fileHandler, pollHandler, adminHandler);
        StaticFileHandler staticHandler = new StaticFileHandler();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(config.getHost(), config.getPort()), 0);
            server.createContext("/api", apiHandler);
            server.createContext("/", staticHandler);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            server.start();

            printLanAddresses(config.getPort());

            // Scheduled background tasks: remove inactive users & cleanup typing
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    List<String> removed = userService.removeInactiveUsers();
                    for (String name : removed) {
                        chatService.addSystemMessage("#general", "🔴 " + name + " timed out (inactive)");
                        Logger.info("LEAVE", name + " (timed out)");
                    }
                } catch (Exception e) {
                    Logger.error("CRON", "Error during inactive user cleanup", e);
                }
            }, 10, 10, TimeUnit.SECONDS);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Stopping Lab Chat Server...");
                server.stop(1);
                scheduler.shutdown();
                System.out.println("[SHUTDOWN] Server stopped safely.");
            }));

        } catch (IOException e) {
            System.err.println("Fatal: Could not start HTTP server on port " + config.getPort());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printLanAddresses(int port) {
        System.out.println("\nReady for connections!");
        System.out.println("Local access : http://localhost:" + port);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') == -1) {
                        System.out.println("LAN access   : http://" + addr.getHostAddress() + ":" + port + " (" + ni.getDisplayName() + ")");
                    }
                }
            }
        } catch (Exception ignored) {}
        System.out.println("========================================\n");
    }
}
