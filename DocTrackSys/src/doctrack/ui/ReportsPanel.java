package doctrack.ui;

import doctrack.dao.AttachmentDAO;
import doctrack.dao.AuditLogDAO;
import doctrack.dao.DocumentDAO;
import doctrack.dao.RoutingDAO;
import doctrack.model.Document;
import doctrack.report.SummaryReportGenerator;
import doctrack.util.AppLogger;
import doctrack.util.CsvUtil;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ReportsPanel extends JPanel {

    private final DocumentDAO documentDAO = new DocumentDAO();
    private final RoutingDAO routingDAO = new RoutingDAO();
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final SummaryReportGenerator reportGenerator = new SummaryReportGenerator();

    private final JTextArea summaryArea = new JTextArea(20, 40);

    private final DefaultTableModel overdueModel = new DefaultTableModel(
            new String[]{"Tracking No.", "Subject", "Current Office", "Current Holder", "Due Date", "Days Overdue"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable overdueTable = new JTable(overdueModel);

    private String lastSummaryText = "";

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
        JButton printSummaryBtn = new JButton("Print Summary");
        printSummaryBtn.addActionListener(e -> printSummary());
        JButton exportBtn = new JButton("Export Overdue List to Excel (CSV)");
        exportBtn.addActionListener(e -> exportOverdue());
        JButton printBtn = new JButton("Print Overdue List");
        printBtn.addActionListener(e -> printOverdue());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(refreshBtn);
        btnPanel.add(printSummaryBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(printBtn);
        top.add(btnPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
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

            // System-wide figures that don't come from the document list itself
            int totalRoutingActions = routingDAO.countAll();
            int totalAttachments = attachmentDAO.countAll();
            int totalAuditEntries = auditLogDAO.countAll();

            StringBuilder sb = new StringBuilder();
            sb.append("=== OVERVIEW ===\n");
            sb.append(String.format("Total documents tracked: %d%n", s.total));
            sb.append(String.format("  Open / in progress: %d%n", s.openCount));
            sb.append(String.format("  Complied or closed: %d%n", s.closedOrCompliedCount));
            sb.append(String.format("  Overdue (past due date, still open): %d%n", s.overdueCount));
            sb.append(String.format("New in last 7 days: %d    New in last 30 days: %d%n",
                    s.createdLast7Days, s.createdLast30Days));

            sb.append("\n=== BY STATUS ===\n");
            appendBreakdown(sb, s.byStatus, s.total);

            sb.append("\n=== BY DOCUMENT TYPE ===\n");
            appendBreakdown(sb, s.byDocType, s.total);

            sb.append("\n=== BY DIRECTION ===\n");
            appendBreakdown(sb, s.byDirection, s.total);

            sb.append("\n=== BY PRIORITY ===\n");
            appendBreakdown(sb, s.byPriority, s.total);

            sb.append("\n=== WORKLOAD BY CURRENT OFFICE ===\n");
            appendBreakdown(sb, s.byCurrentOffice, s.total);

            sb.append("\n=== TURNAROUND TIME (received to closed) ===\n");
            if (s.turnaroundSampleSize == 0) {
                sb.append("  No closed documents with both a received and closed date yet.\n");
            } else {
                sb.append(String.format("  Based on %d closed document(s):%n", s.turnaroundSampleSize));
                sb.append(String.format("  Average: %.1f day(s)    Median: %.1f day(s)%n",
                        s.avgTurnaroundDays, s.medianTurnaroundDays));
                sb.append(String.format("  Fastest: %.0f day(s)    Slowest: %.0f day(s)%n",
                        s.minTurnaroundDays, s.maxTurnaroundDays));
            }

            sb.append("\n=== SYSTEM ACTIVITY VOLUME ===\n");
            sb.append(String.format("  Routing/tracking actions logged: %d%n", totalRoutingActions));
            sb.append(String.format("  Attachments on file (evidence + general): %d%n", totalAttachments));
            sb.append(String.format("  Audit log entries: %d%n", totalAuditEntries));

            lastSummaryText = sb.toString();
            summaryArea.setText(lastSummaryText);
            summaryArea.setCaretPosition(0);

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
            AppLogger.logError("Failed to build activity summary", ex);
            JOptionPane.showMessageDialog(this, "Failed to build report: " + ex.getMessage());
        }
    }

    private void appendBreakdown(StringBuilder sb, Map<String, Integer> counts, int total) {
        if (counts.isEmpty()) {
            sb.append("  (no data)\n");
            return;
        }
        // largest first -- the point of a breakdown is seeing what dominates
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    double pct = total > 0 ? 100.0 * e.getValue() / total : 0;
                    sb.append(String.format("  %-22s %4d  (%.0f%%)%n", e.getKey(), e.getValue(), pct));
                });
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
            AppLogger.logError("Failed to export overdue list", ex);
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
        }
    }

    private void printOverdue() {
        try {
            boolean ok = overdueTable.print(JTable.PrintMode.FIT_WIDTH, null, null);
            if (!ok) JOptionPane.showMessageDialog(this, "Print cancelled.");
        } catch (Exception ex) {
            AppLogger.logError("Failed to print overdue list", ex);
            JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage());
        }
    }

    private void printSummary() {
        if (lastSummaryText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nothing to print yet -- click Refresh first.");
            return;
        }
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(new SummaryPrintable(lastSummaryText));
            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException ex) {
            AppLogger.logError("Failed to print activity summary", ex);
            JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage());
        }
    }

    /** Minimal paginated plain-text printable for the summary report. */
    private static final class SummaryPrintable implements Printable {
        private final String[] lines;
        private static final int LINE_HEIGHT = 15;
        private static final Font FONT = new Font("Monospaced", Font.PLAIN, 10);

        SummaryPrintable(String text) {
            this.lines = text.split("\n");
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            Graphics2D g = (Graphics2D) graphics;
            g.translate((int) pageFormat.getImageableX(), (int) pageFormat.getImageableY());
            g.setFont(FONT);
            int pageHeight = (int) pageFormat.getImageableHeight();
            int linesPerPage = Math.max(1, pageHeight / LINE_HEIGHT);
            int totalPages = (int) Math.ceil((double) lines.length / linesPerPage);
            if (pageIndex >= totalPages) return NO_SUCH_PAGE;

            int y = LINE_HEIGHT;
            int start = pageIndex * linesPerPage;
            int end = Math.min(lines.length, start + linesPerPage);
            for (int i = start; i < end; i++) {
                g.drawString(lines[i], 0, y);
                y += LINE_HEIGHT;
            }
            return PAGE_EXISTS;
        }
    }
}
