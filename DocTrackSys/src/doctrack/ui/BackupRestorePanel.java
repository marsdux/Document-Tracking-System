package doctrack.ui;

import doctrack.dao.AuditLogDAO;
import doctrack.model.User;
import doctrack.util.AppLogger;
import doctrack.util.AppPaths;
import doctrack.util.BackupService;
import doctrack.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupRestorePanel extends JPanel {

    private final User currentUser;
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    public BackupRestorePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        buildUI();
    }

    private void buildUI() {
        JLabel title = new JLabel("Backup & Restore");
        title.setFont(UITheme.FONT_HEADER);
        add(title, BorderLayout.NORTH);

        JTextArea info = new JTextArea(
                "Export creates a single file containing your entire database and every " +
                "attached scanned document, together with a checksum for each file. That " +
                "checksum is verified before anything is restored, so you can trust an " +
                "exported file even after copying it to a different computer or reinstalling " +
                "the app.\n\n" +
                "Importing REPLACES all current data. The app automatically saves a safety " +
                "backup of what you currently have before making any changes, so an import " +
                "can always be undone by importing that safety backup again.\n\n" +
                "Data folder: " + AppPaths.getAppBaseDir().getPath());
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setFont(UITheme.FONT_TABLE);
        info.setBackground(getBackground());
        add(new JScrollPane(info), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        JButton exportBtn = new JButton("Export Full Backup...");
        exportBtn.setFont(UITheme.FONT_LABEL);
        exportBtn.addActionListener(e -> doExport());
        JButton importBtn = new JButton("Import / Restore Backup...");
        importBtn.setFont(UITheme.FONT_LABEL);
        importBtn.addActionListener(e -> doImport());
        buttons.add(exportBtn);
        buttons.add(importBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private void doExport() {
        JFileChooser chooser = new JFileChooser(AppPaths.getBackupsDir());
        String suggested = "doctracksys_backup_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        chooser.setSelectedFile(new File(AppPaths.getBackupsDir(), suggested));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File dest = chooser.getSelectedFile();
        if (!dest.getName().toLowerCase().endsWith(".zip")) {
            dest = new File(dest.getParentFile(), dest.getName() + ".zip");
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            BackupService.exportBackup(dest);
            auditLogDAO.log(null, currentUser.getUserId(), "EXPORT", "Exported full backup to " + dest.getPath());
            JOptionPane.showMessageDialog(this, "Backup exported successfully to:\n" + dest.getPath());
        } catch (Exception ex) {
            AppLogger.logError("Backup export failed", ex);
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Backup Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void doImport() {
        JFileChooser chooser = new JFileChooser(AppPaths.getBackupsDir());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File src = chooser.getSelectedFile();

        int confirm = JOptionPane.showConfirmDialog(this,
                "This will REPLACE all current documents, routing history, and attachments "
                        + "with the contents of:\n" + src.getPath()
                        + "\n\nA safety backup of your current data will be made automatically first.\n"
                        + "Continue?",
                "Confirm Restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            File safetyBackup = BackupService.importBackup(src);
            // Logged AFTER the swap, into the now-active restored database --
            // logging before the swap would be pointless, since the whole
            // action_log table (along with everything else) is about to be
            // replaced by the backup's own. This entry marks the restore in
            // the log going forward.
            auditLogDAO.log(null, currentUser.getUserId(), "IMPORT",
                    "Restored full backup from " + src.getPath()
                            + " (pre-restore safety backup saved to " + safetyBackup.getPath() + ")");
            JOptionPane.showMessageDialog(this,
                    "Restore complete. Your previous data was saved to:\n" + safetyBackup.getPath()
                            + "\n\nPlease restart the application now so every screen reloads the restored data.");
        } catch (BackupService.VerificationException ex) {
            JOptionPane.showMessageDialog(this, "Backup could not be verified -- import cancelled.\n\n"
                    + ex.getMessage(), "Integrity Check Failed", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            AppLogger.logError("Backup import failed", ex);
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                    "Backup Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }
}
