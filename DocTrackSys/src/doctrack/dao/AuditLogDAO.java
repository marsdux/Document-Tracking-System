package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.AuditLogEntry;
import doctrack.util.AppLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Records system-level accountability events (create/edit/delete/route/
 * attach/print/export) to the action_log table, and lets that log be
 * searched and retrieved for the Audit Log tab. Writing (log()) is
 * deliberately soft-fail -- a logging failure must never block or
 * corrupt the user's actual work, so any SQLException there is caught,
 * written to the file log, and swallowed. Reading (search()) is not
 * soft-fail -- if the log can't be displayed the caller needs to know.
 */
public class AuditLogDAO {

    /** Every distinct action_type currently used by the app -- for the filter dropdown. */
    public static final String[] KNOWN_ACTION_TYPES = {
            "CREATE", "EDIT", "DELETE", "ROUTE", "ATTACH", "DETACH",
            "EXPORT", "IMPORT", "LOGIN_SUCCESS", "LOGIN_FAILURE"
    };

    public void log(Integer documentId, Integer userId, String actionType, String details) {
        String sql = "INSERT INTO action_log (document_id, user_id, action_type, details) VALUES (?,?,?,?)";
        try {
            Connection conn = DBConnection.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (documentId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, documentId);
                if (userId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, userId);
                ps.setString(3, actionType);
                ps.setString(4, details);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            AppLogger.logError("Failed to write action_log entry (" + actionType + ")", ex);
        }
    }

    /**
     * Searches the audit log with optional filters (pass null/empty/"ALL"
     * to skip a filter). Most recent first. limit caps how many rows come
     * back (a growing log shouldn't be able to stall the UI or blow up
     * memory); pass 0 for no limit.
     */
    public List<AuditLogEntry> search(String actionType, String fromDate, String toDate,
                                       String keyword, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT a.log_id, a.document_id, a.user_id, a.action_type, a.details, a.action_date, "
                        + "d.tracking_no, u.full_name AS user_full_name "
                        + "FROM action_log a "
                        + "LEFT JOIN documents d ON a.document_id = d.document_id "
                        + "LEFT JOIN users u ON a.user_id = u.user_id "
                        + "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (actionType != null && !actionType.isEmpty() && !"ALL".equalsIgnoreCase(actionType)) {
            sql.append(" AND a.action_type = ?");
            params.add(actionType);
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append(" AND date(a.action_date) >= date(?)");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append(" AND date(a.action_date) <= date(?)");
            params.add(toDate.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (a.details LIKE ? OR d.tracking_no LIKE ? OR u.full_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append(" ORDER BY a.action_date DESC, a.log_id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<AuditLogEntry> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    public int countAll() throws SQLException {
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM action_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private AuditLogEntry mapRow(ResultSet rs) throws SQLException {
        AuditLogEntry e = new AuditLogEntry();
        e.setLogId(rs.getInt("log_id"));
        int docId = rs.getInt("document_id");
        e.setDocumentId(rs.wasNull() ? null : docId);
        int userId = rs.getInt("user_id");
        e.setUserId(rs.wasNull() ? null : userId);
        e.setActionType(rs.getString("action_type"));
        e.setDetails(rs.getString("details"));
        e.setActionDate(rs.getString("action_date"));
        e.setDocumentTrackingNo(rs.getString("tracking_no"));
        e.setUserFullName(rs.getString("user_full_name"));
        return e;
    }
}

