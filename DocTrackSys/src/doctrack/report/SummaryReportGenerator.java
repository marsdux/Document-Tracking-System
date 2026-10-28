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
        public int openCount;
        public int closedOrCompliedCount;
        public Map<String, Integer> byStatus = new LinkedHashMap<>();
        public Map<String, Integer> byDirection = new LinkedHashMap<>();
        public Map<String, Integer> byDocType = new LinkedHashMap<>();
        public Map<String, Integer> byPriority = new LinkedHashMap<>();
        public Map<String, Integer> byCurrentOffice = new LinkedHashMap<>();
        public int overdueCount;
        public int createdLast7Days;
        public int createdLast30Days;
        public double avgTurnaroundDays;   // for CLOSED/COMPLIED docs with a date_closed
        public double minTurnaroundDays;
        public double maxTurnaroundDays;
        public double medianTurnaroundDays;
        public int turnaroundSampleSize;   // how many documents the turnaround stats above are based on
    }

    public Summary summarize(List<Document> documents) {
        Summary s = new Summary();
        s.total = documents.size();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

        List<Long> turnarounds = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        LocalDate thirtyDaysAgo = today.minusDays(30);

        for (Document d : documents) {
            s.byStatus.merge(nullToDash(d.getStatus()), 1, Integer::sum);
            s.byDirection.merge(nullToDash(d.getDirection()), 1, Integer::sum);
            s.byDocType.merge(nullToDash(d.getDocType()), 1, Integer::sum);
            s.byPriority.merge(nullToDash(d.getPriority()), 1, Integer::sum);
            s.byCurrentOffice.merge(nullToDash(d.getCurrentOfficeName()), 1, Integer::sum);

            boolean closedOrComplied = "CLOSED".equals(d.getStatus()) || "COMPLIED".equals(d.getStatus());
            if (closedOrComplied) s.closedOrCompliedCount++; else s.openCount++;

            if (isOverdue(d, today)) {
                s.overdueCount++;
            }

            if (d.getDateCreated() != null && d.getDateCreated().length() >= 10) {
                try {
                    LocalDate created = LocalDate.parse(d.getDateCreated().substring(0, 10), fmt);
                    if (!created.isBefore(sevenDaysAgo)) s.createdLast7Days++;
                    if (!created.isBefore(thirtyDaysAgo)) s.createdLast30Days++;
                } catch (Exception ignored) {
                    // malformed date -- skip this record's recency calc
                }
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

        s.turnaroundSampleSize = turnarounds.size();
        if (!turnarounds.isEmpty()) {
            Collections.sort(turnarounds);
            s.avgTurnaroundDays = turnarounds.stream().mapToLong(Long::longValue).average().orElse(0.0);
            s.minTurnaroundDays = turnarounds.get(0);
            s.maxTurnaroundDays = turnarounds.get(turnarounds.size() - 1);
            int mid = turnarounds.size() / 2;
            s.medianTurnaroundDays = (turnarounds.size() % 2 == 0)
                    ? (turnarounds.get(mid - 1) + turnarounds.get(mid)) / 2.0
                    : turnarounds.get(mid);
        }

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
