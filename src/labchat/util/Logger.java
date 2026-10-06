package labchat.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Logger {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Logger() {}

    public static synchronized void info(String tag, String message) {
        String time = LocalDateTime.now().format(FMT);
        System.out.printf("[%s] [%-4s] %s%n", time, tag, message);
    }

    public static synchronized void warn(String tag, String message) {
        String time = LocalDateTime.now().format(FMT);
        System.err.printf("[%s] [%-4s] WARN: %s%n", time, tag, message);
    }

    public static synchronized void error(String tag, String message, Throwable t) {
        String time = LocalDateTime.now().format(FMT);
        System.err.printf("[%s] [%-4s] ERROR: %s%n", time, tag, message);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }
}
