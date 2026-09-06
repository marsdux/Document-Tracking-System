package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.Attachment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttachmentDAO {

    public int insert(Attachment a) throws SQLException {
        String sql = "INSERT INTO attachments (document_id, file_name, file_path, description, uploaded_by) "
                + "VALUES (?,?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getDocumentId());
            ps.setString(2, a.getFileName());
            ps.setString(3, a.getFilePath());
            ps.setString(4, a.getDescription());
            if (a.getUploadedBy() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, a.getUploadedBy());
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

    public List<Attachment> findByDocument(int documentId) throws SQLException {
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
        a.setFileName(rs.getString("file_name"));
        a.setFilePath(rs.getString("file_path"));
        a.setDescription(rs.getString("description"));
        int uploadedBy = rs.getInt("uploaded_by");
        a.setUploadedBy(rs.wasNull() ? null : uploadedBy);
        a.setDateUploaded(rs.getString("date_uploaded"));
        return a;
    }
}
