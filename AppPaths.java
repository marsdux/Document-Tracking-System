package doctrack.util;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Resolves every folder the app writes to (database, attachments,
 * backups, logs) relative to the location of the running application
 * itself -- the folder containing DocTrackSys.jar, or the build/classes
 * folder when run unpacked from an IDE -- rather than the process's
 * current working directory.
 *
 * Why this matters: a plain relative path like new File("data") depends
 * entirely on what directory the JVM was launched FROM, which varies by
 * OS, launcher, shortcut, or double-click behavior. Two people launching
 * the exact same install could end up pointed at two different "data"
 * folders, silently creating a second empty database. Anchoring to the
 * app's own location means the app always finds (or creates) the same
 * data next to itself, wherever it's copied or run from.
 */
public final class AppPaths {

    private AppPaths() { }

    /** Folder that contains the running jar (or the classes root, if run unpacked). */
    public static File getAppBaseDir() {
        try {
            File src = new File(AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            // Running from a packaged jar: src is the jar file -> use its parent folder.
            // Running unpacked (e.g. straight from an IDE's build/classes): src is
            // already a directory -> use it directly.
            return src.isFile() ? src.getParentFile() : src;
        } catch (URISyntaxException | NullPointerException e) {
            // Fallback: current working directory. Only hit if the code source can't
            // be determined at all (e.g. some non-standard classloader setups).
            return new File(".").getAbsoluteFile();
        }
    }

    public static File getDataDir() {
        return ensure(new File(getAppBaseDir(), "data"));
    }

    public static File getAttachmentsDir() {
        return ensure(new File(getAppBaseDir(), "attachments"));
    }

    public static File getBackupsDir() {
        return ensure(new File(getAppBaseDir(), "backups"));
    }

    public static File getLogsDir() {
        return ensure(new File(getAppBaseDir(), "logs"));
    }

    private static File ensure(File dir) {
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
