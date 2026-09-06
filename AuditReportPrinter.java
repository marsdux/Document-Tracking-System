package doctrack.util;

import doctrack.model.Document;
import doctrack.model.RoutingStep;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.List;

/**
 * Renders a document's full audit/history trail as a formatted, paginated
 * printable report -- document header info followed by every routing
 * step (timestamp, action, from/to office, to personnel, remarks, logged
 * by). This is the "print the history trace" deliverable: a proper
 * formatted report rather than a raw table dump.
 */
public class AuditReportPrinter implements Printable {

    private final Document document;
    private final List<RoutingStep> steps;
    private static final int LINE_HEIGHT = 16;
    private static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 16);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 11);

    public AuditReportPrinter(Document document, List<RoutingStep> steps) {
        this.document = document;
        this.steps = steps;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        Graphics2D g = (Graphics2D) graphics;
        g.translate((int) pageFormat.getImageableX(), (int) pageFormat.getImageableY());
        int pageWidth = (int) pageFormat.getImageableWidth();
        int pageHeight = (int) pageFormat.getImageableHeight();

        java.util.List<String> lines = buildLines(pageWidth, g.getFontMetrics(BODY_FONT));
        int linesPerPage = Math.max(1, (pageHeight - 60) / LINE_HEIGHT);
        int totalPages = (int) Math.ceil((double) lines.size() / linesPerPage);
        if (pageIndex >= totalPages) return NO_SUCH_PAGE;

        int y = 0;
        g.setFont(TITLE_FONT);
        g.drawString("Document Audit Trail Report", 0, y += LINE_HEIGHT + 6);
        g.setFont(BODY_FONT);
        g.drawString("Printed: " + java.time.LocalDateTime.now(), 0, y += LINE_HEIGHT);
        g.drawLine(0, y + 4, pageWidth, y + 4);
        y += 12;

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

    private java.util.List<String> buildLines(int pageWidth, FontMetrics fm) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§Tracking No.: " + safe(document.getTrackingNo())
                + "        Type: " + safe(document.getDocType())
                + "        Direction: " + safe(document.getDirection()));
        lines.add("§Subject: " + safe(document.getSubject()));
        lines.add("§Source: " + safe(document.getSourceName())
                + "        Status: " + safe(document.getStatus())
                + "        Priority: " + safe(document.getPriority()));
        lines.add("§Date Received: " + safe(document.getDateReceived())
                + "        Due Date: " + safe(document.getDueDate())
                + "        Date Closed: " + safe(document.getDateClosed()));
        lines.add("");
        lines.add("§--- Routing / Action History (" + steps.size() + " entr" + (steps.size() == 1 ? "y" : "ies") + ") ---");
        lines.add("");

        int seq = 1;
        for (RoutingStep s : steps) {
            lines.add("§" + seq + ". " + safe(s.getActionDate()) + " — " + safe(s.getAction()));
            lines.add("     From: " + safe(s.getFromOfficeName())
                    + "    To Office: " + safe(s.getToOfficeName())
                    + "    To Personnel: " + safe(s.getToPersonnelName()));
            if (s.getRemarks() != null && !s.getRemarks().trim().isEmpty()) {
                for (String wrapped : wrap("     Remarks: " + s.getRemarks(), pageWidth, fm)) {
                    lines.add(wrapped);
                }
            }
            lines.add("     Logged by: " + safe(s.getLoggedByName()));
            lines.add("");
            seq++;
        }
        return lines;
    }

    private java.util.List<String> wrap(String text, int pageWidth, FontMetrics fm) {
        java.util.List<String> out = new java.util.ArrayList<>();
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
