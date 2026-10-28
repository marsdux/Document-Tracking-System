package doctrack.ui;

import doctrack.dao.AttachmentDAO;
import doctrack.dao.AuditLogDAO;
import doctrack.model.Attachment;
import doctrack.model.Document;
import doctrack.model.RoutingStep;
import doctrack.model.User;
import doctrack.util.AppLogger;
import doctrack.util.AttachmentFileUtil;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Lets the user attach scanned hardcopies (proof of receipt, forwarding,
 * compliance, etc.) either to a document in general, or as evidence for
 * one specific tracking action, depending on which constructor is used.
 * Files are copied into the app's managed attachments folder so the
 * originals the user picks stay untouched.
 */
public class AttachmentDialog extends JDialog {

    private final User currentUser;
    private final Document document;
    private final RoutingStep routingStep; // null = document-level mode; non-null = evidence for this action
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"File Name", "Description", "Date Uploaded"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Attachment> currentAttachments;

    /** Document-level attachments (general reference files, not tied to one action). */
    public AttachmentDialog(Frame owner, User currentUser, Document document) {
        this(owner, currentUser, document, null);
    }

    /** Evidence attached to one specific tracking/routing action. */
    public AttachmentDialog(Frame owner, User currentUser, Document document, RoutingStep routingStep) {
        super(owner, routingStep == null
                        ? "Attachments - " + document.getTrackingNo()
                        : "Evidence for " + routingStep.getAction() + " on " + routingStep.getActionDate(),
                true);
        this.currentUser = currentUser;
        this.document = document;
        this.routingStep = routingStep;
        buildUI();
        loadAttachments();
        setSize(650, 450);
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String headerText = routingStep == null
                ? "Scanned copies / general attachments for " + document.getTrackingNo()
                : "Proof of \"" + routingStep.getAction() + "\" logged " + routingStep.getActionDate()
                        + " -- " + document.getTrackingNo();
        JLabel header = new JLabel(headerText);
        header.setFont(UITheme.FONT_LABEL);
        root.add(header, BorderLayout.NORTH);

        table.setFont(UITheme.FONT_TABLE);
        table.getTableHeader().setFont(UITheme.FONT_LABEL);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("Attach File...");
        addBtn.addActionListener(e -> addAttachment());
        JButton openBtn = new JButton("View Selected");
        openBtn.addActionListener(e -> openSelected());
        JButton printBtn = new JButton("Print Selected");
        printBtn.addActionListener(e -> printSelected());
        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> removeSelected());
        buttons.add(addBtn);
        buttons.add(openBtn);
        buttons.add(printBtn);
        buttons.add(removeBtn);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadAttachments() {
        try {
            currentAttachments = (routingStep == null)
                    ? attachmentDAO.findByDocument(document.getDocumentId())
                    : attachmentDAO.findByRoutingStep(routingStep.getStepId());
            model.setRowCount(0);
            for (Attachment a : currentAttachments) {
                model.addRow(new Object[]{a.getFileName(), a.getDescription(), a.getDateUploaded()});
            }
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load attachments", ex);
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
            AttachmentFileUtil.Result copied = AttachmentFileUtil.copyIntoDocumentFolder(
                    document.getDocumentId(), source);

            Attachment a = new Attachment();
            a.setDocumentId(document.getDocumentId());
            a.setRoutingStepId(routingStep == null ? null : routingStep.getStepId());
            a.setFileName(copied.storedFileName);
            a.setFilePath(copied.storedPath);
            a.setDescription(description);
            a.setUploadedBy(currentUser.getUserId());
            attachmentDAO.insert(a);

            String logDetail = routingStep == null
                    ? "Attached file: " + copied.storedFileName
                    : "Attached evidence for " + routingStep.getAction() + " (step #" + routingStep.getStepId()
                            + "): " + copied.storedFileName;
            auditLogDAO.log(document.getDocumentId(), currentUser.getUserId(), "ATTACH", logDetail);

            loadAttachments();
        } catch (IOException | SQLException ex) {
            AppLogger.logError("Failed to attach file", ex);
            JOptionPane.showMessageDialog(this, "Failed to attach file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelected() {
        Attachment a = getSelected();
        if (a == null) return;
        try {
            Desktop.getDesktop().open(new File(a.getFilePath()));
        } catch (IOException ex) {
            AppLogger.logError("Failed to open attachment", ex);
            JOptionPane.showMessageDialog(this, "Could not open file: " + ex.getMessage());
        }
    }

    private void printSelected() {
        Attachment a = getSelected();
        if (a == null) return;
        File file = new File(a.getFilePath());
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
                Desktop.getDesktop().print(file);
                auditLogDAO.log(document.getDocumentId(), currentUser.getUserId(), "PRINT",
                        "Printed attachment: " + a.getFileName());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Direct printing isn't supported on this system. Opening the file instead -- "
                                + "you can print from whatever application opens it.",
                        "Print Not Supported", JOptionPane.INFORMATION_MESSAGE);
                Desktop.getDesktop().open(file);
            }
        } catch (IOException ex) {
            AppLogger.logError("Failed to print attachment", ex);
            JOptionPane.showMessageDialog(this, "Could not print file: " + ex.getMessage()
                    + "\n\nYou can try opening it and printing from there instead.");
        }
    }

    private void removeSelected() {
        Attachment a = getSelected();
        if (a == null) return;
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
            AppLogger.logError("Failed to remove attachment", ex);
            JOptionPane.showMessageDialog(this, "Failed to remove attachment: " + ex.getMessage());
        }
    }

    private Attachment getSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || currentAttachments == null || row >= currentAttachments.size()) {
            JOptionPane.showMessageDialog(this, "Select an attachment first.");
            return null;
        }
        return currentAttachments.get(row);
    }
}
