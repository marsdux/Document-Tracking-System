package doctrack.util;

import doctrack.model.AuditLogEntry;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.List;

/** Renders a filtered audit log listing as a formatted, paginated printable report. */
public class AuditLogPrinter implements Printable {

    private final List<AuditLogEntry> entries;
    private final String filterSummary;
    private static final int LINE_HEIGHT = 15;
    private static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 16);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 10);
    private static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 10);

    public AuditLogPrinter(List<AuditLogEntry> entries, String filterSummary) {
        this.entries = entries;
        this.filterSummary = filterSummary;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        Graphics2D g = (Graphics2D) graphics;
        g.translate((int) pageFormat.getImageableX(), (int) pageFormat.getImageableY());
        int pageWidth = (int) pageFormat.getImageableWidth();
        int pageHeight = (int) pageFormat.getImageableHeight();

        List<String> lines = buildLines(pageWidth, g.getFontMetrics(BODY_FONT));
        int linesPerPage = Math.max(1, (pageHeight - 60) / LINE_HEIGHT);
        int totalPages = (int) Math.ceil((double) lines.size() / linesPerPage);
        if (pageIndex >= totalPages) return NO_SUCH_PAGE;

        int y = 0;
        g.setFont(TITLE_FONT);
        g.drawString("Audit Log Report", 0, y += LINE_HEIGHT + 6);
        g.setFont(BODY_FONT);
        g.drawString("Printed: " + java.time.LocalDateTime.now() + "   |   " + filterSummary, 0, y += LINE_HEIGHT);
        g.drawString(entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies"), 0, y += LINE_HEIGHT);
        g.drawLine(0, y + 4, pageWidth, y + 4);
        y += 10;

        int start = pageIndex * linesPerPage;
        int end = Math.min(lines.size(), start + linesPerPage);
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            g.setFont(line.startsWith("§") ? HEADER_FONT : BODY_FONT);
            g.drawString(line.replace("§", ""), 0, y += LINE_HEIGHT);
        }

        g.setFont(BODY_FONT);
        g.drawString("Page " + (pageIndex + 1) + " of " + totalPages, 0, pageHeight - 10);
        return PAGE_EXISTS;
    }

    private List<String> buildLines(int pageWidth, FontMetrics fm) {
        List<String> lines = new java.util.ArrayList<>();
        int seq = 1;
        for (AuditLogEntry e : entries) {
            String header = seq + ". " + safe(e.getActionDate()) + "   [" + safe(e.getActionType()) + "]   "
                    + "User: " + safe(e.getUserFullName())
                    + (e.getDocumentTrackingNo() != null ? "   Doc: " + e.getDocumentTrackingNo() : "");
            lines.add("§" + header);
            if (e.getDetails() != null && !e.getDetails().trim().isEmpty()) {
                for (String wrapped : wrap("     " + e.getDetails(), pageWidth, fm)) {
                    lines.add(wrapped);
                }
            }
            seq++;
        }
        return lines;
    }

    private List<String> wrap(String text, int pageWidth, FontMetrics fm) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (fm.stringWidth(candidate) > pageWidth && current.length() > 0) {
                out.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    private String safe(String v) {
        return (v == null || v.isEmpty()) ? "—" : v;
    }
}
