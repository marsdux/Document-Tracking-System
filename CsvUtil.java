package doctrack.util;

import doctrack.model.AuditLogEntry;
import doctrack.model.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV export/import for backup purposes. CSV opens natively in Microsoft
 * Excel/Google Sheets/LibreOffice, so this gives you an external, editable
 * backup without needing a third-party library such as Apache POI to write
 * true .xlsx binary files.
 */
public final class CsvUtil {

    private static final String[] HEADERS = {
        "TrackingNo", "DocType", "Direction", "Subject", "Source",
        "Status", "Priority", "DateReceived", "DueDate", "DateClosed",
        "CurrentOffice", "CurrentHolder", "Remarks"
    };

    private static final String[] AUDIT_LOG_HEADERS = {
        "DateTime", "ActionType", "User", "DocumentTrackingNo", "Details"
    };

    private CsvUtil() { }

    public static void exportDocuments(List<Document> documents, File outFile) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            // UTF-8 BOM so Excel on Windows reads it correctly
            w.write('\uFEFF');
            w.write(joinCsv(HEADERS));
            w.write("\n");
            for (Document d : documents) {
                String[] row = {
                    d.getTrackingNo(), d.getDocType(), d.getDirection(), d.getSubject(),
                    d.getSourceName(), d.getStatus(), d.getPriority(), d.getDateReceived(),
                    d.getDueDate(), d.getDateClosed(), d.getCurrentOfficeName(),
                    d.getCurrentHolderName(), d.getRemarks()
                };
                w.write(joinCsv(row));
                w.write("\n");
            }
        }
    }

    public static void exportAuditLog(List<AuditLogEntry> entries, File outFile) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write(joinCsv(AUDIT_LOG_HEADERS));
            w.write("\n");
            for (AuditLogEntry e : entries) {
                String[] row = {
                    e.getActionDate(), e.getActionType(), e.getUserFullName(),
                    e.getDocumentTrackingNo(), e.getDetails()
                };
                w.write(joinCsv(row));
                w.write("\n");
            }
        }
    }

    /**
     * Parses a CSV file back into raw rows (header row excluded) for review
     * before re-import. Caller is responsible for mapping columns to
     * Document objects and inserting via DocumentDAO, since importing should
     * go through normal validation (tracking number uniqueness, etc).
     */
    public static List<String[]> readRows(File csvFile) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { // skip header, strip BOM if present
                    first = false;
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                rows.add(splitCsvLine(line));
            }
        }
        return rows;
    }

    private static String joinCsv(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        // CSV/formula-injection mitigation: a cell starting with =, +, -, or @
        // can be interpreted as a formula by Excel/Sheets when the file is
        // opened, potentially executing attacker-supplied content that made
        // it into a subject/remarks field. Prefixing with a single quote
        // forces it to be read as plain text while staying human-readable.
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        boolean needsQuoting = v.contains(",") || v.contains("\"") || v.contains("\n");
        v = v.replace("\"", "\"\"");
        return needsQuoting ? "\"" + v + "\"" : v;
    }

    /** Minimal RFC-4180-ish CSV line splitter (handles quoted fields with commas). */
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
