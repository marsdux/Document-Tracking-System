package doctrack.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Copies a user-selected file into this app's managed attachments folder,
 * safely. Centralized here so every place that lets someone attach a
 * scanned file (document-level attachments, per-action evidence on a
 * routing entry, the initial receipt scan) goes through the exact same
 * filename-sanitization and path-containment check rather than three
 * copies of similar logic that could drift out of sync.
 */
public final class AttachmentFileUtil {

    private AttachmentFileUtil() { }

    public static final class Result {
        public final String storedFileName; // sanitized original name, for display
        public final String storedPath;     // actual path on disk
        Result(String storedFileName, String storedPath) {
            this.storedFileName = storedFileName;
            this.storedPath = storedPath;
        }
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /**
     * Copies sourceFile into AppPaths.getAttachmentsDir()/&lt;documentId&gt;/,
     * under a sanitized, collision-proof name. Each call produces its own
     * independent physical copy (even for the same source file attached to
     * several routing steps in one batch) so each attachment row can be
     * removed independently later without risk of deleting a file another
     * row still references.
     *
     * @throws IOException if the copy fails or the resolved destination
     *                      would escape the intended per-document folder
     *                      (a defensive check against a crafted file name
     *                      trying to traverse out via ".." segments).
     */
    public static Result copyIntoDocumentFolder(int documentId, File sourceFile) throws IOException {
        File destDir = new File(AppPaths.getAttachmentsDir(), String.valueOf(documentId));
        if (!destDir.exists()) destDir.mkdirs();

        String safeName = sanitizeFileName(sourceFile.getName());
        // timestamp + random suffix: two attachments created in the same
        // millisecond (e.g. attaching one evidence file across several
        // recipients in a single batch send) must never collide on name.
        String uniquePrefix = System.currentTimeMillis() + "_" + Integer.toHexString(RANDOM.nextInt());
        File dest = new File(destDir, uniquePrefix + "_" + safeName);

        if (!dest.getCanonicalFile().toPath().startsWith(destDir.getCanonicalFile().toPath())) {
            throw new IOException("Invalid file name -- resolved outside the attachments folder.");
        }

        Files.copy(sourceFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new Result(safeName, dest.getPath());
    }

    /** Strips path separators and control characters so a crafted file name can't escape the attachments folder. */
    private static String sanitizeFileName(String name) {
        String base = new File(name).getName(); // drops any directory components
        String cleaned = base.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        return cleaned.isEmpty() ? "attachment" : cleaned;
    }
}
