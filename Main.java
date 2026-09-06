package doctrack;

import doctrack.db.DBConnection;
import doctrack.ui.LoginFrame;
import doctrack.util.AppLogger;
import doctrack.util.UITheme;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Catch anything that would otherwise silently kill the Swing event
        // thread (and thus appear to the user as the app just freezing or
        // vanishing) and write it to the log file instead.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            AppLogger.logError("Uncaught exception on thread " + thread.getName(), ex);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(DBConnection::closeConnection));

        UITheme.apply();
        SwingUtilities.invokeLater(() -> {
            LoginFrame login = new LoginFrame();
            login.setVisible(true);
        });
    }
}
