package doctrack.ui;

import doctrack.dao.DocumentDAO;
import doctrack.model.Document;
import doctrack.report.SummaryReportGenerator;
import doctrack.util.CsvUtil;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ReportsPanel extends JPanel {

    private final DocumentDAO documentDAO = new DocumentDAO();
    private final SummaryReportGenerator reportGenerator = new SummaryReportGenerator();

    private final JTextArea summaryArea = new JTextArea(12, 40);

    private final DefaultTableModel overdueModel = new DefaultTableModel(
            new String[]{"Tracking No.", "Subject", "Current Office", "Current Holder", "Due Date", "Days Overdue"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable overdueTable = new JTable(overdueModel);

    public ReportsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Activity Summary");
        title.setFont(UITheme.FONT_HEADER);
        top.add(title, BorderLayout.WEST);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JButton exportBtn = new JButton("Export Overdue List to Excel (CSV)");
        exportBtn.addActionListener(e -> exportOverdue());
        JButton printBtn = new JButton("Print Overdue List");
        printBtn.addActionListener(e -> printOverdue());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(refreshBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(printBtn);
        top.add(btnPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        summaryArea.setEditable(false);
        summaryArea.setFont(UITheme.FONT_TABLE);
        JPanel center = new JPanel(new GridLayout(2, 1, 10, 10));
        center.add(new JScrollPane(summaryArea));

        overdueTable.setFont(UITheme.FONT_TABLE);
        overdueTable.getTableHeader().setFont(UITheme.FONT_LABEL);
        JPanel overduePanel = new JPanel(new BorderLayout());
        overduePanel.setBorder(BorderFactory.createTitledBorder("Overdue Documents (past due date, not yet complied/closed)"));
        overduePanel.add(new JScrollPane(overdueTable), BorderLayout.CENTER);
        center.add(overduePanel);

        add(center, BorderLayout.CENTER);
    }

    private void refresh() {
        try {
            List<Document> all = documentDAO.findAll();
            SummaryReportGenerator.Summary s = reportGenerator.summarize(all);

            StringBuilder sb = new StringBuilder();
            sb.append("Total documents tracked: ").append(s.total).append("\n\n");
            sb.append("By Status:\n");
            for (Map.Entry<String, Integer> e : s.byStatus.entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\nBy Direction:\n");
            for (Map.Entry<String, Integer> e : s.byDirection.entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            sb.append("\nOverdue (past due date, still open): ").append(s.overdueCount).append("\n");
            sb.append(String.format("Average turnaround time (received to closed): %.1f day(s)%n", s.avgTurnaroundDays));

            summaryArea.setText(sb.toString());

            List<Document> overdue = documentDAO.findOverdue();
            overdueModel.setRowCount(0);
            java.time.LocalDate today = java.time.LocalDate.now();
            for (Document d : overdue) {
                long daysOver = -1;
                try {
                    java.time.LocalDate due = java.time.LocalDate.parse(d.getDueDate().substring(0, 10));
                    daysOver = java.time.temporal.ChronoUnit.DAYS.between(due, today);
                } catch (Exception ignored) { }
                overdueModel.addRow(new Object[]{
                        d.getTrackingNo(), d.getSubject(), d.getCurrentOfficeName(),
                        d.getCurrentHolderName(), d.getDueDate(), daysOver
                });
            }
        } catch (SQLException ex) {
            doctrack.util.AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to build report: " + ex.getMessage());
        }
    }

    private void exportOverdue() {
        try {
            List<Document> overdue = documentDAO.findOverdue();
            if (overdue.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No overdue documents to export.");
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("overdue_documents.csv"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                CsvUtil.exportDocuments(overdue, chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Exported " + overdue.size() + " overdue document(s).");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
        }
    }

    private void printOverdue() {
        try {
            boolean ok = overdueTable.print(JTable.PrintMode.FIT_WIDTH, null, null);
            if (!ok) JOptionPane.showMessageDialog(this, "Print cancelled.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage());
        }
    }
}
