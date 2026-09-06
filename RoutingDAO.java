package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.RoutingStep;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoutingDAO {

    /** Generates a new batch id to group the rows of one multi-recipient/multi-document send. */
    public String newBatchId() {
        return UUID.randomUUID().toString();
    }

    public int insert(RoutingStep s) throws SQLException {
        String sql = "INSERT INTO routing_steps (document_id, batch_id, from_office_id, to_office_id, "
                + "to_personnel_id, action, remarks, logged_by) VALUES (?,?,?,?,?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getDocumentId());
            ps.setString(2, s.getBatchId());
            setNullableInt(ps, 3, s.getFromOfficeId());
            setNullableInt(ps, 4, s.getToOfficeId());
            setNullableInt(ps, 5, s.getToPersonnelId());
            ps.setString(6, s.getAction());
            ps.setString(7, s.getRemarks());
            setNullableInt(ps, 8, s.getLoggedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Inserts every step in one JDBC transaction so a multi-recipient /
     * multi-document send either fully succeeds or fully rolls back --
     * you never end up with the document logged as sent to some
     * recipients but not others because of a mid-batch failure.
     */
    public void insertBatch(List<RoutingStep> steps) throws SQLException {
        if (steps.isEmpty()) return;
        Connection conn = DBConnection.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            String sql = "INSERT INTO routing_steps (document_id, batch_id, from_office_id, to_office_id, "
                    + "to_personnel_id, action, remarks, logged_by) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (RoutingStep s : steps) {
                    ps.setInt(1, s.getDocumentId());
                    ps.setString(2, s.getBatchId());
                    setNullableInt(ps, 3, s.getFromOfficeId());
                    setNullableInt(ps, 4, s.getToOfficeId());
                    setNullableInt(ps, 5, s.getToPersonnelId());
                    ps.setString(6, s.getAction());
                    ps.setString(7, s.getRemarks());
                    setNullableInt(ps, 8, s.getLoggedBy());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /** Full chronological trail for one document -- the accountability record. */
    public List<RoutingStep> findByDocument(int documentId) throws SQLException {
        String sql = baseSelect() + "WHERE r.document_id = ? ORDER BY r.action_date ASC, r.step_id ASC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RoutingStep> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    /**
     * The recipients of the most recent send for a document. When a
     * document was forwarded to multiple people/offices in one action,
     * this returns all of them (same batch_id, same document) -- this is
     * how the UI shows "currently with: A, B, C" instead of a single name.
     */
    public List<RoutingStep> findCurrentRecipients(int documentId) throws SQLException {
        String sql = baseSelect()
                + "WHERE r.document_id = ? AND r.batch_id = ("
                + "  SELECT batch_id FROM routing_steps WHERE document_id = ? AND batch_id IS NOT NULL "
                + "  ORDER BY action_date DESC, step_id DESC LIMIT 1"
                + ") ORDER BY r.step_id ASC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            ps.setInt(2, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RoutingStep> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    public RoutingStep findById(int stepId) throws SQLException {
        String sql = baseSelect() + "WHERE r.step_id = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stepId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Corrects an erroneous entry in place (action, recipient, remarks).
     * The original timestamp, document, and batch grouping are left
     * untouched so the entry stays in its place in the chronological
     * trail -- only the corrected fields change. Callers are expected to
     * also write an action_log entry noting the correction, since editing
     * history is itself something that should be accountable.
     */
    public void update(RoutingStep s) throws SQLException {
        String sql = "UPDATE routing_steps SET to_office_id=?, to_personnel_id=?, action=?, remarks=? "
                + "WHERE step_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInt(ps, 1, s.getToOfficeId());
            setNullableInt(ps, 2, s.getToPersonnelId());
            ps.setString(3, s.getAction());
            ps.setString(4, s.getRemarks());
            ps.setInt(5, s.getStepId());
            ps.executeUpdate();
        }
    }

    /** Removes a single erroneous routing entry. Does not touch other rows in the same batch. */
    public void delete(int stepId) throws SQLException {
        String sql = "DELETE FROM routing_steps WHERE step_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stepId);
            ps.executeUpdate();
        }
    }

    private String baseSelect() {
        return "SELECT r.*, "
                + "fo.office_name AS from_office_name, "
                + "to_o.office_name AS to_office_name, "
                + "p.full_name AS to_personnel_name, "
                + "u.full_name AS logged_by_name "
                + "FROM routing_steps r "
                + "LEFT JOIN offices fo ON r.from_office_id = fo.office_id "
                + "LEFT JOIN offices to_o ON r.to_office_id = to_o.office_id "
                + "LEFT JOIN personnel p ON r.to_personnel_id = p.personnel_id "
                + "LEFT JOIN users u ON r.logged_by = u.user_id ";
    }

    private RoutingStep mapRow(ResultSet rs) throws SQLException {
        RoutingStep s = new RoutingStep();
        s.setStepId(rs.getInt("step_id"));
        s.setDocumentId(rs.getInt("document_id"));
        s.setBatchId(rs.getString("batch_id"));
        int fromOffice = rs.getInt("from_office_id");
        s.setFromOfficeId(rs.wasNull() ? null : fromOffice);
        int toOffice = rs.getInt("to_office_id");
        s.setToOfficeId(rs.wasNull() ? null : toOffice);
        int toPersonnel = rs.getInt("to_personnel_id");
        s.setToPersonnelId(rs.wasNull() ? null : toPersonnel);
        s.setAction(rs.getString("action"));
        s.setActionDate(rs.getString("action_date"));
        s.setRemarks(rs.getString("remarks"));
        int loggedBy = rs.getInt("logged_by");
        s.setLoggedBy(rs.wasNull() ? null : loggedBy);
        s.setFromOfficeName(rs.getString("from_office_name"));
        s.setToOfficeName(rs.getString("to_office_name"));
        s.setToPersonnelName(rs.getString("to_personnel_name"));
        s.setLoggedByName(rs.getString("logged_by_name"));
        return s;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}
