package doctrack.report;

import doctrack.model.Document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Computes summary counts and turnaround-time metrics from a list of
 * documents. Kept separate from the UI so the same numbers can be reused
 * for on-screen display, printing, or export.
 */
public class SummaryReportGenerator {

    public static final class Summary {
        public int total;
        public Map<String, Integer> byStatus = new LinkedHashMap<>();
        public Map<String, Integer> byDirection = new LinkedHashMap<>();
        public int overdueCount;
        public double avgTurnaroundDays; // for CLOSED/COMPLIED docs with a date_closed
    }

    public Summary summarize(List<Document> documents) {
        Summary s = new Summary();
        s.total = documents.size();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

        List<Long> turnarounds = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Document d : documents) {
            s.byStatus.merge(nullToDash(d.getStatus()), 1, Integer::sum);
            s.byDirection.merge(nullToDash(d.getDirection()), 1, Integer::sum);

            if (isOverdue(d, today)) {
                s.overdueCount++;
            }

            if (d.getDateReceived() != null && !d.getDateReceived().isEmpty()
                    && d.getDateClosed() != null && !d.getDateClosed().isEmpty()) {
                try {
                    LocalDate received = LocalDate.parse(d.getDateReceived().substring(0, 10), fmt);
                    LocalDate closed = LocalDate.parse(d.getDateClosed().substring(0, 10), fmt);
                    long days = java.time.temporal.ChronoUnit.DAYS.between(received, closed);
                    if (days >= 0) turnarounds.add(days);
                } catch (Exception ignored) {
                    // malformed date -- skip this record's turnaround calc
                }
            }
        }

        s.avgTurnaroundDays = turnarounds.isEmpty() ? 0.0
                : turnarounds.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return s;
    }

    private boolean isOverdue(Document d, LocalDate today) {
        if (d.getDueDate() == null || d.getDueDate().isEmpty()) return false;
        if ("COMPLIED".equals(d.getStatus()) || "CLOSED".equals(d.getStatus())) return false;
        try {
            LocalDate due = LocalDate.parse(d.getDueDate().substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            return due.isBefore(today);
        } catch (Exception e) {
            return false;
        }
    }

    private String nullToDash(String value) {
        return (value == null || value.isEmpty()) ? "(none)" : value;
    }
}
