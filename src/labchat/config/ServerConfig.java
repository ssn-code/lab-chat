package labchat.config;

public final class ServerConfig {
    private int port = 5000;
    private String host = "0.0.0.0";
    private int maxUsers = 200;
    private int maxUsernameLength = 20;
    private int maxMessageLength = 2000;
    private long maxFileSizeBytes = 25 * 1024 * 1024; // 25 MB
    private int historySizePerRoom = 1000;
    private String adminToken = "admin123";
    private int userInactiveTimeoutSeconds = 90;
    private int uploadRateLimitPerMinute = 15;
    private int messageRateLimitPerSecond = 5;

    public static ServerConfig parse(String[] args) {
        ServerConfig cfg = new ServerConfig();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--port") && i + 1 < args.length) {
                try { cfg.port = Integer.parseInt(args[++i]); } catch (Exception ignored) {}
            } else if (arg.equals("--host") && i + 1 < args.length) {
                cfg.host = args[++i];
            } else if (arg.equals("--max-users") && i + 1 < args.length) {
                try { cfg.maxUsers = Integer.parseInt(args[++i]); } catch (Exception ignored) {}
            } else if (arg.equals("--max-message-length") && i + 1 < args.length) {
                try { cfg.maxMessageLength = Integer.parseInt(args[++i]); } catch (Exception ignored) {}
            } else if (arg.equals("--max-file-size") && i + 1 < args.length) {
                try { cfg.maxFileSizeBytes = Long.parseLong(args[++i]); } catch (Exception ignored) {}
            } else if (arg.equals("--history-size") && i + 1 < args.length) {
                try { cfg.historySizePerRoom = Integer.parseInt(args[++i]); } catch (Exception ignored) {}
            } else if (arg.equals("--admin-token") && i + 1 < args.length) {
                cfg.adminToken = args[++i];
            } else if (!arg.startsWith("--") && i == 0) {
                try { cfg.port = Integer.parseInt(arg); } catch (Exception ignored) {}
            }
        }
        return cfg;
    }

    public int getPort() { return port; }
    public String getHost() { return host; }
    public int getMaxUsers() { return maxUsers; }
    public int getMaxUsernameLength() { return maxUsernameLength; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public int getHistorySizePerRoom() { return historySizePerRoom; }
    public String getAdminToken() { return adminToken; }
    public int getUserInactiveTimeoutSeconds() { return userInactiveTimeoutSeconds; }
    public int getUploadRateLimitPerMinute() { return uploadRateLimitPerMinute; }
    public int getMessageRateLimitPerSecond() { return messageRateLimitPerSecond; }
}
