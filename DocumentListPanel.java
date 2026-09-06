package doctrack.ui;

import doctrack.dao.AttachmentDAO;
import doctrack.dao.AuditLogDAO;
import doctrack.dao.DocumentDAO;
import doctrack.model.Attachment;
import doctrack.model.Document;
import doctrack.model.User;
import doctrack.util.AppLogger;
import doctrack.util.CsvUtil;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DocumentListPanel extends JPanel {

    private final User currentUser;
    private final DocumentDAO documentDAO = new DocumentDAO();

    private final JTextField searchField = new JTextField(20);
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[]{"ALL", "RECEIVED", "IN_PROGRESS", "FORWARDED", "COMPLIED", "CLOSED"});
    private final JComboBox<String> directionFilter = new JComboBox<>(
            new String[]{"ALL", "INCOMING", "OUTGOING", "INTERNAL"});

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Tracking No.", "Type", "Direction", "Subject", "Status", "Priority",
                    "Current Office", "Current Holder", "Date Received", "Due Date"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private List<Document> currentResults;

    public DocumentListPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUI();
        refresh();
    }

    private void buildUI() {
        // --- filter bar ---
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.add(new JLabel("Search:"));
        filterBar.add(searchField);
        filterBar.add(new JLabel("Status:"));
        filterBar.add(statusFilter);
        filterBar.add(new JLabel("Direction:"));
        filterBar.add(directionFilter);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> refresh());
        filterBar.add(searchBtn);
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            statusFilter.setSelectedIndex(0);
            directionFilter.setSelectedIndex(0);
            refresh();
        });
        filterBar.add(clearBtn);
        add(filterBar, BorderLayout.NORTH);

        // --- table ---
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFont(UITheme.FONT_TABLE);
        table.getTableHeader().setFont(UITheme.FONT_LABEL);
        table.setRowHeight(26);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- action bar ---
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton addBtn = new JButton("Add Document");
        addBtn.addActionListener(e -> openForm(null));
        JButton editBtn = new JButton("View / Edit");
        editBtn.addActionListener(e -> openForm(getSelectedDocument()));
        JButton routeBtn = new JButton("Route / Track History");
        routeBtn.addActionListener(e -> openRouting());
        JButton batchForwardBtn = new JButton("Batch Forward (Multi-Select)");
        batchForwardBtn.addActionListener(e -> openBatchForward());
        JButton attachBtn = new JButton("Attachments");
        attachBtn.addActionListener(e -> openAttachments());
        JButton printBtn = new JButton("Print");
        printBtn.addActionListener(e -> printSelected());
        JButton exportBtn = new JButton("Export to Excel (CSV)");
        exportBtn.addActionListener(e -> exportCsv());
        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(new Color(0xB0, 0x20, 0x20));
        deleteBtn.addActionListener(e -> deleteSelected());

        actionBar.add(addBtn);
        actionBar.add(editBtn);
        actionBar.add(routeBtn);
        actionBar.add(batchForwardBtn);
        actionBar.add(attachBtn);
        actionBar.add(printBtn);
        actionBar.add(exportBtn);
        actionBar.add(deleteBtn);
        add(actionBar, BorderLayout.SOUTH);
    }

    public void refresh() {
        try {
            String keyword = searchField.getText();
            String status = (String) statusFilter.getSelectedItem();
            String direction = (String) directionFilter.getSelectedItem();
            currentResults = documentDAO.search(keyword, status, direction);
            tableModel.setRowCount(0);
            for (Document d : currentResults) {
                tableModel.addRow(new Object[]{
                        d.getTrackingNo(), d.getDocType(), d.getDirection(), d.getSubject(),
                        d.getStatus(), d.getPriority(), d.getCurrentOfficeName(),
                        d.getCurrentHolderName(), d.getDateReceived(), d.getDueDate()
                });
            }
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to load documents: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Document getSelectedDocument() {
        int row = table.getSelectedRow();
        if (row < 0 || currentResults == null || row >= currentResults.size()) return null;
        return currentResults.get(row);
    }

    private List<Document> getSelectedDocuments() {
        List<Document> selected = new java.util.ArrayList<>();
        if (currentResults == null) return selected;
        for (int row : table.getSelectedRows()) {
            if (row >= 0 && row < currentResults.size()) selected.add(currentResults.get(row));
        }
        return selected;
    }

    private void openForm(Document existing) {
        DocumentFormDialog dlg = new DocumentFormDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), currentUser, existing);
        dlg.setVisible(true);
        if (dlg.isSaved()) refresh();
    }

    private void openRouting() {
        Document doc = getSelectedDocument();
        if (doc == null) {
            JOptionPane.showMessageDialog(this, "Select a document first.");
            return;
        }
        RoutingDialog dlg = new RoutingDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), currentUser, doc);
        dlg.setVisible(true);
        refresh();
    }

    private void openBatchForward() {
        List<Document> selected = getSelectedDocuments();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select one or more documents first "
                    + "(Ctrl/Shift+Click to select multiple rows).");
            return;
        }
        BatchForwardDialog dlg = new BatchForwardDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), currentUser, selected);
        dlg.setVisible(true);
        if (dlg.isSent()) refresh();
    }

    private void openAttachments() {
        Document doc = getSelectedDocument();
        if (doc == null) {
            JOptionPane.showMessageDialog(this, "Select a document first.");
            return;
        }
        AttachmentDialog dlg = new AttachmentDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), currentUser, doc);
        dlg.setVisible(true);
    }

    private void printSelected() {
        Document doc = getSelectedDocument();
        if (doc == null) {
            JOptionPane.showMessageDialog(this, "Select a document to print, or print the whole list "
                    + "by pressing Print with no selection.");
        }
        try {
            boolean ok;
            if (doc != null) {
                ok = table.print(JTable.PrintMode.FIT_WIDTH, null, null);
            } else {
                ok = table.print(JTable.PrintMode.FIT_WIDTH, null, null);
            }
            if (!ok) {
                JOptionPane.showMessageDialog(this, "Print cancelled.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage(),
                    "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        List<Document> selected = getSelectedDocuments();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select one or more documents to delete first "
                    + "(Ctrl/Shift+Click to select multiple rows).");
            return;
        }

        StringBuilder names = new StringBuilder();
        for (Document d : selected) {
            if (names.length() > 0) names.append(", ");
            names.append(d.getTrackingNo());
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Permanently delete " + selected.size() + " document(s)?\n\n" + names + "\n\n"
                        + "This removes the document, its full routing history, and its attachments. "
                        + "This cannot be undone -- use it only for entries that are erroneous, "
                        + "redundant, or discarded.\n\n"
                        + "Tip: if you're not certain, export a backup first (Backup & Restore tab).",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        AttachmentDAO attachmentDAO = new AttachmentDAO();
        AuditLogDAO auditLogDAO = new AuditLogDAO();
        int deletedCount = 0;
        List<String> failures = new ArrayList<>();
        for (Document d : selected) {
            try {
                // Remove the physical attachment files first so nothing is left
                // orphaned on disk once the DB rows cascade-delete.
                List<Attachment> attachments = attachmentDAO.findByDocument(d.getDocumentId());
                for (Attachment a : attachments) {
                    new File(a.getFilePath()).delete();
                }
                // Log before deleting: action_log.document_id references documents
                // ON DELETE SET NULL, so the row (with the tracking no. already in
                // its text) survives the delete even though the link is cleared.
                auditLogDAO.log(d.getDocumentId(), currentUser.getUserId(), "DELETE",
                        "Deleted document " + d.getTrackingNo() + " (" + d.getSubject() + ")");
                documentDAO.delete(d.getDocumentId());
                deletedCount++;
            } catch (SQLException ex) {
                AppLogger.logError("Failed to delete document " + d.getTrackingNo(), ex);
                failures.add(d.getTrackingNo());
            }
        }

        refresh();
        if (failures.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Deleted " + deletedCount + " document(s).");
        } else {
            JOptionPane.showMessageDialog(this, "Deleted " + deletedCount + " document(s). "
                    + "Failed: " + String.join(", ", failures),
                    "Partial Failure", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void exportCsv() {
        if (currentResults == null || currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No documents to export.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("documents_backup.csv"));
        int result = chooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                CsvUtil.exportDocuments(currentResults, chooser.getSelectedFile());
                new AuditLogDAO().log(null, currentUser.getUserId(), "EXPORT",
                        "Exported " + currentResults.size() + " document(s) to CSV: "
                                + chooser.getSelectedFile().getPath());
                JOptionPane.showMessageDialog(this, "Exported " + currentResults.size()
                        + " document(s) to " + chooser.getSelectedFile().getName()
                        + "\nThis file opens directly in Excel.");
            } catch (Exception ex) {
                AppLogger.logError("CSV export failed", ex);
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
