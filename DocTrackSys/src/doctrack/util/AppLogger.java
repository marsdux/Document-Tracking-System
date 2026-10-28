package doctrack.util;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal file logger. Writes to logs/app.log next to the application
 * (see AppPaths). This exists so that, in a live deployment, unexpected
 * errors leave a trace an admin can send back to support instead of only
 * a one-line dialog the user has to transcribe by hand.
 */
public final class AppLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppLogger() { }

    public static synchronized void logError(String context, Throwable t) {
        File logFile = new File(AppPaths.getLogsDir(), "app.log");
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println("[" + LocalDateTime.now().format(TS) + "] ERROR - " + context);
            if (t != null) {
                t.printStackTrace(pw);
            }
        } catch (IOException ignored) {
            // logging must never crash the app; if we can't write the log, drop it
        }
    }

    public static synchronized void logInfo(String message) {
        File logFile = new File(AppPaths.getLogsDir(), "app.log");
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println("[" + LocalDateTime.now().format(TS) + "] INFO  - " + message);
        } catch (IOException ignored) {
        }
    }
}
