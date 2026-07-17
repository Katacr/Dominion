package cn.lunadeer.dominion.utils;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static cn.lunadeer.dominion.utils.Misc.formatString;

public class XLogger {
    public static XLogger instance;

    public XLogger(@NotNull JavaPlugin plugin) {
        instance = this;
        this.sender = plugin.getServer().getConsoleSender();
    }

    public static XLogger setDebug(boolean debug) {
        instance.debug = debug;
        return instance;
    }

    public static boolean isDebug() {
        return instance.debug;
    }

    private final ConsoleCommandSender sender;
    private boolean debug = false;

    public static void info(String message) {
        Notification.info(instance.sender, "&a I | " + message);
    }

    public static void warn(String message) {
        Notification.warn(instance.sender, "&e W | " + message);
    }

    public static void error(String message) {
        Notification.error(instance.sender, "&c E | " + message);
    }

    public static void debug(String message) {
        if (!instance.debug) return;
        Notification.info(instance.sender, "&9 D | " + message);
    }

    public static void info(String message, Object... args) {
        info(formatString(message, args));
    }

    public static void warn(String message, Object... args) {
        warn(formatString(message, args));
    }

    public static void error(String message, Object... args) {
        error(formatString(message, args));
    }

    public static void error(Throwable throwable) {
        if (throwable == null) {
            error("Unknown error (no exception details available)");
            return;
        }
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        logThrowable(throwable, "Exception", visited);
    }

    private static void logThrowable(Throwable throwable, String relation, Set<Throwable> visited) {
        if (!visited.add(throwable)) {
            error(relation + " | [CIRCULAR REFERENCE: " + throwable.getClass().getName() + "]");
            return;
        }

        String message = throwable.getMessage();
        error(relation + " | " + throwable.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message));
        if (throwable instanceof SQLException sqlException) {
            error("SQL Details | SQLState: " + valueOrUnknown(sqlException.getSQLState())
                    + ", Error Code: " + sqlException.getErrorCode());
        }

        if (isDebug()) {
            for (StackTraceElement element : throwable.getStackTrace()) {
                error("StackTrace | " + element);
            }
            for (Throwable suppressed : throwable.getSuppressed()) {
                logThrowable(suppressed, "Suppressed", visited);
            }
        }

        if (throwable instanceof SQLException sqlException && sqlException.getNextException() != null) {
            logThrowable(sqlException.getNextException(), "Next SQL exception", visited);
        }
        Throwable cause = throwable.getCause();
        if (cause != null) {
            logThrowable(cause, "Caused by", visited);
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public static void debug(String message, Object... args) {
        debug(formatString(message, args));
    }
}
