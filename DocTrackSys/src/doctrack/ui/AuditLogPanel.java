package doctrack.ui;

import doctrack.dao.AuditLogDAO;
import doctrack.model.AuditLogEntry;
import doctrack.util.AppLogger;
import doctrack.util.AuditLogPrinter;
import doctrack.util.CsvUtil;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * System-wide audit log: every create/edit/delete/route/attach/export/
 * login event recorded by AuditLogDAO, searchable, printable, and
 * exportable. This is the "who did what, when" view across the whole
 * app, not just one document's history (that's RoutingDialog).
 */
public class AuditLogPanel extends JPanel {

    private static final int DEFAULT_LIMIT = 500;

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private final JComboBox<String> actionTypeFilter = new JComboBox<>();
    private final JTextField fromDateField = new JTextField(10);
    private final JTextField toDateField = new JTextField(10);
    private final JTextField keywordField = new JTextField(20);
    private final JCheckBox showAllCheckbox = new JCheckBox("Show all (may be slow for a large log)");
    private final JLabel resultCountLabel = new JLabel(" ");

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Date/Time", "Action", "User", "Document", "Details"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private List<AuditLogEntry> currentResults;

    public AuditLogPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.add(new JLabel("Action:"));
        actionTypeFilter.addItem("ALL");
        for (String t : AuditLogDAO.KNOWN_ACTION_TYPES) actionTypeFilter.addItem(t);
        filterBar.add(actionTypeFilter);
        filterBar.add(new JLabel("From (yyyy-mm-dd):"));
        filterBar.add(fromDateField);
        filterBar.add(new JLabel("To:"));
        filterBar.add(toDateField);
        filterBar.add(new JLabel("Search:"));
        filterBar.add(keywordField);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> refresh());
        filterBar.add(searchBtn);
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            actionTypeFilter.setSelectedIndex(0);
            fromDateField.setText("");
            toDateField.setText("");
            keywordField.setText("");
            showAllCheckbox.setSelected(false);
            refresh();
        });
        filterBar.add(clearBtn);
        add(filterBar, BorderLayout.NORTH);

        table.setFont(UITheme.FONT_TABLE);
        table.getTableHeader().setFont(UITheme.FONT_LABEL);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        JPanel southTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        showAllCheckbox.addActionListener(e -> refresh());
        southTop.add(showAllCheckbox);
        southTop.add(resultCountLabel);
        southPanel.add(southTop, BorderLayout.NORTH);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton printBtn = new JButton("Print");
        printBtn.addActionListener(e -> printLog());
        JButton exportBtn = new JButton("Export to Excel (CSV)");
        exportBtn.addActionListener(e -> exportCsv());
        actionBar.add(printBtn);
        actionBar.add(exportBtn);
        southPanel.add(actionBar, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);
    }

    private void refresh() {
        try {
            String actionType = (String) actionTypeFilter.getSelectedItem();
            String fromDate = fromDateField.getText().trim();
            String toDate = toDateField.getText().trim();
            String keyword = keywordField.getText().trim();
            int limit = showAllCheckbox.isSelected() ? 0 : DEFAULT_LIMIT;

            currentResults = auditLogDAO.search(actionType, fromDate, toDate, keyword, limit);
            tableModel.setRowCount(0);
            for (AuditLogEntry e : currentResults) {
                tableModel.addRow(new Object[]{
                        e.getActionDate(), e.getActionType(), e.getUserFullName(),
                        e.getDocumentTrackingNo(), e.getDetails()
                });
            }

            int total = auditLogDAO.countAll();
            if (limit > 0 && currentResults.size() == limit && total > limit) {
                resultCountLabel.setText("Showing most recent " + limit + " of " + total
                        + " total entries — check \"Show all\" to see everything.");
            } else {
                resultCountLabel.setText("Showing " + currentResults.size() + " entr"
                        + (currentResults.size() == 1 ? "y" : "ies") + " (of " + total + " total).");
            }
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load audit log", ex);
            JOptionPane.showMessageDialog(this, "Failed to load audit log: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String filterSummary() {
        String actionType = (String) actionTypeFilter.getSelectedItem();
        StringBuilder sb = new StringBuilder();
        sb.append("Action: ").append(actionType == null ? "ALL" : actionType);
        if (!fromDateField.getText().trim().isEmpty()) sb.append("  From: ").append(fromDateField.getText().trim());
        if (!toDateField.getText().trim().isEmpty()) sb.append("  To: ").append(toDateField.getText().trim());
        if (!keywordField.getText().trim().isEmpty()) sb.append("  Search: \"").append(keywordField.getText().trim()).append("\"");
        return sb.toString();
    }

    private void printLog() {
        if (currentResults == null || currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entries to print.");
            return;
        }
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(new AuditLogPrinter(currentResults, filterSummary()));
            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException ex) {
            AppLogger.logError("Failed to print audit log", ex);
            JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage());
        }
    }

    private void exportCsv() {
        if (currentResults == null || currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entries to export.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("audit_log.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                CsvUtil.exportAuditLog(currentResults, chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Exported " + currentResults.size() + " entr"
                        + (currentResults.size() == 1 ? "y" : "ies") + " to "
                        + chooser.getSelectedFile().getName());
            } catch (Exception ex) {
                AppLogger.logError("Failed to export audit log", ex);
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
