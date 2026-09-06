package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.Document;

import java.sql.*;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class DocumentDAO {

    /** Generates the next tracking number for the current year, e.g. DTS-2026-000123 */
    public String generateTrackingNo() throws SQLException {
        int year = Year.now().getValue();
        String prefix = "DTS-" + year + "-";
        String sql = "SELECT COUNT(*) FROM documents WHERE tracking_no LIKE ?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int count = rs.next() ? rs.getInt(1) : 0;
                return prefix + String.format("%06d", count + 1);
            }
        }
    }

    public int insert(Document d) throws SQLException {
        String sql = "INSERT INTO documents (tracking_no, doc_type, direction, subject, source_name, "
                + "origin_office_id, current_office_id, current_holder_id, status, priority, "
                + "date_received, due_date, remarks, created_by) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.getTrackingNo());
            ps.setString(2, d.getDocType());
            ps.setString(3, d.getDirection());
            ps.setString(4, d.getSubject());
            ps.setString(5, d.getSourceName());
            setNullableInt(ps, 6, d.getOriginOfficeId());
            setNullableInt(ps, 7, d.getCurrentOfficeId());
            setNullableInt(ps, 8, d.getCurrentHolderId());
            ps.setString(9, d.getStatus());
            ps.setString(10, d.getPriority());
            ps.setString(11, d.getDateReceived());
            ps.setString(12, d.getDueDate());
            ps.setString(13, d.getRemarks());
            setNullableInt(ps, 14, d.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void update(Document d) throws SQLException {
        String sql = "UPDATE documents SET doc_type=?, direction=?, subject=?, source_name=?, "
                + "origin_office_id=?, current_office_id=?, current_holder_id=?, status=?, priority=?, "
                + "date_received=?, due_date=?, date_closed=?, remarks=? WHERE document_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.getDocType());
            ps.setString(2, d.getDirection());
            ps.setString(3, d.getSubject());
            ps.setString(4, d.getSourceName());
            setNullableInt(ps, 5, d.getOriginOfficeId());
            setNullableInt(ps, 6, d.getCurrentOfficeId());
            setNullableInt(ps, 7, d.getCurrentHolderId());
            ps.setString(8, d.getStatus());
            ps.setString(9, d.getPriority());
            ps.setString(10, d.getDateReceived());
            ps.setString(11, d.getDueDate());
            ps.setString(12, d.getDateClosed());
            ps.setString(13, d.getRemarks());
            ps.setInt(14, d.getDocumentId());
            ps.executeUpdate();
        }
    }

    public void delete(int documentId) throws SQLException {
        String sql = "DELETE FROM documents WHERE document_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            ps.executeUpdate();
        }
    }

    public Document findById(int documentId) throws SQLException {
        String sql = baseSelect() + " WHERE d.document_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Document> findAll() throws SQLException {
        return runQuery(baseSelect() + " ORDER BY d.date_created DESC");
    }

    /** Search across tracking no, subject and source name; empty filters are ignored. */
    public List<Document> search(String keyword, String status, String direction) throws SQLException {
        StringBuilder sql = new StringBuilder(baseSelect() + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (d.tracking_no LIKE ? OR d.subject LIKE ? OR d.source_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append(" AND d.status = ?");
            params.add(status);
        }
        if (direction != null && !direction.trim().isEmpty() && !direction.equalsIgnoreCase("ALL")) {
            sql.append(" AND d.direction = ?");
            params.add(direction);
        }
        sql.append(" ORDER BY d.date_created DESC");

        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Document> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    /** Documents past their due_date and not yet closed -- for the "overdue" report. */
    public List<Document> findOverdue() throws SQLException {
        String sql = baseSelect()
                + " WHERE d.due_date IS NOT NULL AND d.due_date != ''"
                + " AND d.status NOT IN ('COMPLIED','CLOSED')"
                + " AND date(d.due_date) < date('now','localtime')"
                + " ORDER BY d.due_date ASC";
        return runQuery(sql);
    }

    private List<Document> runQuery(String sql) throws SQLException {
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Document> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    private String baseSelect() {
        return "SELECT d.*, o.office_name AS current_office_name, p.full_name AS current_holder_name "
                + "FROM documents d "
                + "LEFT JOIN offices o ON d.current_office_id = o.office_id "
                + "LEFT JOIN personnel p ON d.current_holder_id = p.personnel_id";
    }

    private Document mapRow(ResultSet rs) throws SQLException {
        Document d = new Document();
        d.setDocumentId(rs.getInt("document_id"));
        d.setTrackingNo(rs.getString("tracking_no"));
        d.setDocType(rs.getString("doc_type"));
        d.setDirection(rs.getString("direction"));
        d.setSubject(rs.getString("subject"));
        d.setSourceName(rs.getString("source_name"));
        d.setOriginOfficeId(getNullableInt(rs, "origin_office_id"));
        d.setCurrentOfficeId(getNullableInt(rs, "current_office_id"));
        d.setCurrentHolderId(getNullableInt(rs, "current_holder_id"));
        d.setStatus(rs.getString("status"));
        d.setPriority(rs.getString("priority"));
        d.setDateReceived(rs.getString("date_received"));
        d.setDueDate(rs.getString("due_date"));
        d.setDateClosed(rs.getString("date_closed"));
        d.setRemarks(rs.getString("remarks"));
        d.setDateCreated(rs.getString("date_created"));
        d.setCreatedBy(getNullableInt(rs, "created_by"));
        d.setCurrentOfficeName(rs.getString("current_office_name"));
        d.setCurrentHolderName(rs.getString("current_holder_name"));
        return d;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }

    private Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
