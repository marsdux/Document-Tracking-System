package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.DocumentType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DocumentTypeDAO {

    public int insert(String typeName) throws SQLException {
        String sql = "INSERT INTO document_types (type_name, sort_order) "
                + "VALUES (?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM document_types))";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, typeName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    /** Soft delete -- keeps existing documents that already used this type name intact. */
    public void deactivate(int typeId) throws SQLException {
        String sql = "UPDATE document_types SET active=0 WHERE type_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            ps.executeUpdate();
        }
    }

    public List<DocumentType> findActive() throws SQLException {
        String sql = "SELECT * FROM document_types WHERE active=1 ORDER BY sort_order, type_name";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<DocumentType> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    public int countDocumentsUsingType(String typeName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM documents WHERE doc_type = ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, typeName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private DocumentType mapRow(ResultSet rs) throws SQLException {
        DocumentType t = new DocumentType();
        t.setTypeId(rs.getInt("type_id"));
        t.setTypeName(rs.getString("type_name"));
        t.setActive(rs.getInt("active") == 1);
        t.setSortOrder(rs.getInt("sort_order"));
        return t;
    }
}
