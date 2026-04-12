package utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight logger with optional debug output.
 */
public final class Logger {
    private enum Level {
        INFO,
        WARN,
        ERROR,
        DEBUG
    }

    private static boolean debugEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("VAULTFS_DEBUG", "false"));
    private static final boolean timestampEnabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("VAULTFS_LOG_TIMESTAMPS", "false")
    );
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Logger() {
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void info(String message) {
        log(Level.INFO, message, null, false);
    }

    public static void warn(String message) {
        log(Level.WARN, message, Colors.YELLOW, false);
    }

    public static void error(String message) {
        log(Level.ERROR, message, Colors.RED, false);
    }

    public static void debug(String message) {
        log(Level.DEBUG, "[debug] " + message, Colors.GRAY, true);
    }

    private static void log(Level level, String message, String color, boolean debugOnly) {
        if (debugOnly && !debugEnabled) {
            return;
        }

        String rendered = message;
        if (timestampEnabled) {
            rendered = "[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "] " + rendered;
        }

        if (color != null) {
            System.out.println(Colors.c(color, rendered));
        } else {
            System.out.println(rendered);
        }
    }
}
