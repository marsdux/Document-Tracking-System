package doctrack.util;

import doctrack.db.DBConnection;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

/**
 * Full backup/restore of the application's data: the database and every
 * attachment file, bundled into one zip with a checksummed manifest so
 * the archive's integrity can be verified on import -- including after
 * being copied to a different computer with a fresh install of the app.
 *
 * Backup contents:
 *   manifest.txt        -- export metadata + SHA-256 + size for every file
 *   data/doctrack.db    -- a consistent snapshot of the database (via SQLite's VACUUM INTO,
 *                           not a raw file copy, so it can't capture a mid-write torn file)
 *   attachments/...      -- every attached scanned file, same relative layout as on disk
 */
public final class BackupService {

    private static final String MANIFEST_ENTRY = "manifest.txt";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private BackupService() { }

    public static final class VerificationException extends Exception {
        public VerificationException(String message) { super(message); }
    }

    // ---------------------------------------------------------------- export

    public static void exportBackup(File destZip) throws IOException, SQLException {
        File snapshot = File.createTempFile("doctrack_snapshot_", ".db", AppPaths.getBackupsDir());
        snapshot.delete(); // VACUUM INTO requires the target not already exist
        try {
            vacuumInto(snapshot);

            List<ManifestEntry> entries = new ArrayList<>();
            entries.add(new ManifestEntry("data/doctrack.db", snapshot));

            File attachmentsDir = AppPaths.getAttachmentsDir();
            if (attachmentsDir.exists()) {
                try (Stream<Path> walk = Files.walk(attachmentsDir.toPath())) {
                    for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        String rel = "attachments/" + attachmentsDir.toPath().relativize(p).toString().replace('\\', '/');
                        entries.add(new ManifestEntry(rel, p.toFile()));
                    }
                }
            }

            writeZip(destZip, entries);
            AppLogger.logInfo("Exported full backup to " + destZip.getPath() + " (" + entries.size() + " file(s))");
        } finally {
            snapshot.delete();
        }
    }

    private static void vacuumInto(File snapshotFile) throws SQLException {
        Connection conn = DBConnection.getConnection();
        String escapedPath = snapshotFile.getAbsolutePath().replace("'", "''");
        try (Statement st = conn.createStatement()) {
            st.execute("VACUUM INTO '" + escapedPath + "'");
        }
    }

    private static void writeZip(File destZip, List<ManifestEntry> entries) throws IOException {
        StringBuilder manifest = new StringBuilder();
        manifest.append("DocTrackSys Full Backup Manifest\n");
        manifest.append("export_timestamp=").append(LocalDateTime.now().format(TS)).append("\n");
        manifest.append("file_count=").append(entries.size()).append("\n");
        manifest.append("---\n");
        for (ManifestEntry e : entries) {
            String hash = FileHashUtil.sha256(e.sourceFile);
            manifest.append(hash).append("  ").append(e.sourceFile.length())
                    .append("  ").append(e.zipPath).append("\n");
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destZip))) {
            zos.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zos.write(manifest.toString().getBytes("UTF-8"));
            zos.closeEntry();

            byte[] buffer = new byte[8192];
            for (ManifestEntry e : entries) {
                zos.putNextEntry(new ZipEntry(e.zipPath));
                try (InputStream is = new BufferedInputStream(new FileInputStream(e.sourceFile))) {
                    int read;
                    while ((read = is.read(buffer)) != -1) zos.write(buffer, 0, read);
                }
                zos.closeEntry();
            }
        }
    }

    private static final class ManifestEntry {
        final String zipPath;
        final File sourceFile;
        ManifestEntry(String zipPath, File sourceFile) { this.zipPath = zipPath; this.sourceFile = sourceFile; }
    }

    // ---------------------------------------------------------------- import

    /**
     * Verifies and restores a backup archive. On success, replaces the
     * current data/doctrack.db and attachments/ folder. Before doing so,
     * it automatically snapshots the CURRENT data to backups/ as a safety
     * net, so an import that turns out to be wrong can be undone by
     * importing that auto-backup again.
     *
     * @return the path of the automatic pre-restore safety backup that was created
     */
    public static File importBackup(File srcZip) throws IOException, SQLException, VerificationException {
        File stagingDir = Files.createTempDirectory("doctrack_restore_").toFile();
        try {
            Map<String, String> manifestHashes = extractAndVerify(srcZip, stagingDir);

            File restoredDb = new File(stagingDir, "data/doctrack.db");
            if (!restoredDb.exists()) {
                throw new VerificationException("Archive does not contain data/doctrack.db -- not a valid backup.");
            }

            // Safety net: back up what's currently installed before overwriting it.
            File preRestoreBackup = new File(AppPaths.getBackupsDir(),
                    "pre_restore_" + LocalDateTime.now().format(TS) + ".zip");
            exportBackup(preRestoreBackup);

            // Swap in the restored data.
            DBConnection.reset();

            File currentDb = DBConnection.getDbFile();
            Files.copy(restoredDb.toPath(), currentDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

            File restoredAttachments = new File(stagingDir, "attachments");
            File currentAttachments = AppPaths.getAttachmentsDir();
            if (restoredAttachments.exists()) {
                deleteRecursively(currentAttachments);
                currentAttachments.mkdirs();
                copyRecursively(restoredAttachments.toPath(), currentAttachments.toPath());
            }

            AppLogger.logInfo("Restored backup from " + srcZip.getPath()
                    + " (" + manifestHashes.size() + " file(s) verified). "
                    + "Pre-restore safety backup: " + preRestoreBackup.getPath());
            return preRestoreBackup;
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    /**
     * Extracts the archive into stagingDir with zip-slip protection (every
     * entry's resolved path is checked to stay inside stagingDir before
     * being written), then verifies every file listed in the manifest
     * against its recorded SHA-256 checksum. Throws VerificationException
     * on the first mismatch or missing file rather than silently
     * accepting a corrupted or tampered archive.
     */
    private static Map<String, String> extractAndVerify(File srcZip, File stagingDir)
            throws IOException, VerificationException {
        Path stagingPath = stagingDir.toPath().normalize();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(srcZip))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path target = stagingPath.resolve(entry.getName()).normalize();
                if (!target.startsWith(stagingPath)) {
                    throw new VerificationException("Archive contains an unsafe file path ("
                            + entry.getName() + ") -- refusing to extract. This archive may be corrupted or tampered with.");
                }
                Files.createDirectories(target.getParent());
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                    int read;
                    while ((read = zis.read(buffer)) != -1) os.write(buffer, 0, read);
                }
            }
        } catch (ZipException ex) {
            // The zip format's own CRC check caught corrupted/tampered entry data
            // before our SHA-256 manifest check even got a chance to run.
            throw new VerificationException("This backup file is corrupted or has been altered "
                    + "(zip integrity check failed while extracting: " + ex.getMessage() + "). Import cancelled.");
        }

        File manifestFile = new File(stagingDir, MANIFEST_ENTRY);
        if (!manifestFile.exists()) {
            throw new VerificationException("Archive has no manifest.txt -- not a valid DocTrackSys backup, "
                    + "or it has been altered.");
        }

        Map<String, String> expectedHashes = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(manifestFile.toPath());
        boolean pastHeader = false;
        for (String line : lines) {
            if (line.equals("---")) { pastHeader = true; continue; }
            if (!pastHeader || line.trim().isEmpty()) continue;
            String[] parts = line.split("\\s+", 3);
            if (parts.length == 3) expectedHashes.put(parts[2], parts[0]);
        }

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> e : expectedHashes.entrySet()) {
            File f = new File(stagingDir, e.getKey());
            if (!f.exists()) {
                failures.add(e.getKey() + " (missing from archive)");
                continue;
            }
            String actualHash = FileHashUtil.sha256(f);
            if (!actualHash.equalsIgnoreCase(e.getValue())) {
                failures.add(e.getKey() + " (checksum mismatch -- file may be corrupted or altered)");
            }
        }
        if (!failures.isEmpty()) {
            throw new VerificationException("Backup integrity check failed for " + failures.size()
                    + " file(s):\n" + String.join("\n", failures)
                    + "\n\nImport cancelled -- no data was changed.");
        }

        return expectedHashes;
    }

    private static void deleteRecursively(File dir) throws IOException {
        if (!dir.exists()) return;
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
