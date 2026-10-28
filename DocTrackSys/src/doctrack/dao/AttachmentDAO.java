package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.Attachment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttachmentDAO {

    public int insert(Attachment a) throws SQLException {
        String sql = "INSERT INTO attachments (document_id, routing_step_id, file_name, file_path, "
                + "description, uploaded_by) VALUES (?,?,?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getDocumentId());
            if (a.getRoutingStepId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, a.getRoutingStepId());
            ps.setString(3, a.getFileName());
            ps.setString(4, a.getFilePath());
            ps.setString(5, a.getDescription());
            if (a.getUploadedBy() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, a.getUploadedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void delete(int attachmentId) throws SQLException {
        String sql = "DELETE FROM attachments WHERE attachment_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attachmentId);
            ps.executeUpdate();
        }
    }

    /** General, document-level attachments only (not tied to a specific tracking action). */
    public List<Attachment> findByDocument(int documentId) throws SQLException {
        String sql = "SELECT * FROM attachments WHERE document_id=? AND routing_step_id IS NULL "
                + "ORDER BY date_uploaded DESC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Attachment> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    /** Evidence attached to one specific tracking/routing action. */
    public List<Attachment> findByRoutingStep(int stepId) throws SQLException {
        String sql = "SELECT * FROM attachments WHERE routing_step_id=? ORDER BY date_uploaded DESC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stepId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Attachment> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    /** Quick counts of evidence per routing step, for badge display in a history table without N+1 queries. */
    public java.util.Map<Integer, Integer> countByRoutingSteps(java.util.Collection<Integer> stepIds) throws SQLException {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        if (stepIds == null || stepIds.isEmpty()) return counts;
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < stepIds.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }
        String sql = "SELECT routing_step_id, COUNT(*) AS cnt FROM attachments "
                + "WHERE routing_step_id IN (" + placeholders + ") GROUP BY routing_step_id";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : stepIds) ps.setInt(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) counts.put(rs.getInt("routing_step_id"), rs.getInt("cnt"));
            }
        }
        return counts;
    }

    /** Every attachment for a document regardless of scope (general + all per-action evidence) -- for full cleanup on document delete. */
    public List<Attachment> findAllForDocument(int documentId) throws SQLException {
        String sql = "SELECT * FROM attachments WHERE document_id=? ORDER BY date_uploaded DESC";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Attachment> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    private Attachment mapRow(ResultSet rs) throws SQLException {
        Attachment a = new Attachment();
        a.setAttachmentId(rs.getInt("attachment_id"));
        a.setDocumentId(rs.getInt("document_id"));
        int stepId = rs.getInt("routing_step_id");
        a.setRoutingStepId(rs.wasNull() ? null : stepId);
        a.setFileName(rs.getString("file_name"));
        a.setFilePath(rs.getString("file_path"));
        a.setDescription(rs.getString("description"));
        int uploadedBy = rs.getInt("uploaded_by");
        a.setUploadedBy(rs.wasNull() ? null : uploadedBy);
        a.setDateUploaded(rs.getString("date_uploaded"));
        return a;
    }

    /** Total attachments (general + per-action evidence) in the whole system, for the Activity Summary. */
    public int countAll() throws SQLException {
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM attachments");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
