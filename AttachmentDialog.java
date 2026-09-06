package doctrack.ui;

import doctrack.dao.AttachmentDAO;
import doctrack.model.Attachment;
import doctrack.model.Document;
import doctrack.model.User;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.List;

/**
 * Lets the user attach scanned hardcopies (compliance proof, signed
 * responses, etc.) to a document. Files are copied into ./attachments/<docId>/
 * so the originals the user picks stay untouched, and the app keeps its own
 * managed copy for the audit trail.
 */
public class AttachmentDialog extends JDialog {

    private final User currentUser;
    private final Document document;
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();
    private final doctrack.dao.AuditLogDAO auditLogDAO = new doctrack.dao.AuditLogDAO();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"File Name", "Description", "Date Uploaded"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Attachment> currentAttachments;

    public AttachmentDialog(Frame owner, User currentUser, Document document) {
        super(owner, "Attachments - " + document.getTrackingNo(), true);
        this.currentUser = currentUser;
        this.document = document;
        buildUI();
        loadAttachments();
        setSize(650, 450);
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("Scanned copies / proof of compliance for " + document.getTrackingNo());
        header.setFont(UITheme.FONT_LABEL);
        root.add(header, BorderLayout.NORTH);

        table.setFont(UITheme.FONT_TABLE);
        table.getTableHeader().setFont(UITheme.FONT_LABEL);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("Attach File...");
        addBtn.addActionListener(e -> addAttachment());
        JButton openBtn = new JButton("Open Selected");
        openBtn.addActionListener(e -> openSelected());
        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> removeSelected());
        buttons.add(addBtn);
        buttons.add(openBtn);
        buttons.add(removeBtn);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadAttachments() {
        try {
            currentAttachments = attachmentDAO.findByDocument(document.getDocumentId());
            model.setRowCount(0);
            for (Attachment a : currentAttachments) {
                model.addRow(new Object[]{a.getFileName(), a.getDescription(), a.getDateUploaded()});
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load attachments: " + ex.getMessage());
        }
    }

    private void addAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select scanned file to attach (image or PDF)");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File source = chooser.getSelectedFile();
        String description = JOptionPane.showInputDialog(this,
                "Description (e.g. 'Signed compliance letter'):");

        try {
            File destDir = new File(doctrack.util.AppPaths.getAttachmentsDir(),
                    String.valueOf(document.getDocumentId()));
            if (!destDir.exists()) destDir.mkdirs();
            String safeName = sanitizeFileName(source.getName());
            File dest = new File(destDir, System.currentTimeMillis() + "_" + safeName);
            // Guard against a crafted file name escaping the intended attachment
            // folder via ".." path segments (path traversal / zip-slip style attack).
            if (!dest.getCanonicalFile().toPath().startsWith(destDir.getCanonicalFile().toPath())) {
                JOptionPane.showMessageDialog(this, "Invalid file name.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Attachment a = new Attachment();
            a.setDocumentId(document.getDocumentId());
            a.setFileName(safeName);
            a.setFilePath(dest.getPath());
            a.setDescription(description);
            a.setUploadedBy(currentUser.getUserId());
            attachmentDAO.insert(a);
            auditLogDAO.log(document.getDocumentId(), currentUser.getUserId(), "ATTACH",
                    "Attached file: " + safeName);

            loadAttachments();
        } catch (IOException | SQLException ex) {
            doctrack.util.AppLogger.logError("Failed to attach file", ex);
            JOptionPane.showMessageDialog(this, "Failed to attach file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Strips path separators and control characters so a crafted file name can't escape the attachments folder. */
    private String sanitizeFileName(String name) {
        String base = new File(name).getName(); // drops any directory components
        String cleaned = base.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        return cleaned.isEmpty() ? "attachment" : cleaned;
    }

    private void openSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an attachment first.");
            return;
        }
        Attachment a = currentAttachments.get(row);
        try {
            Desktop.getDesktop().open(new File(a.getFilePath()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not open file: " + ex.getMessage());
        }
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        Attachment a = currentAttachments.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove attachment \"" + a.getFileName() + "\"? This cannot be undone.",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            attachmentDAO.delete(a.getAttachmentId());
            new File(a.getFilePath()).delete();
            auditLogDAO.log(document.getDocumentId(), currentUser.getUserId(), "DETACH",
                    "Removed attachment: " + a.getFileName());
            loadAttachments();
        } catch (SQLException ex) {
            doctrack.util.AppLogger.logError("Failed to remove attachment", ex);
            JOptionPane.showMessageDialog(this, "Failed to remove attachment: " + ex.getMessage());
        }
    }
}
