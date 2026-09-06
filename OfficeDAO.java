package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.Office;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OfficeDAO {

    public int insert(Office o) throws SQLException {
        String sql = "INSERT INTO offices (office_name, parent_office_id, active, sort_order) VALUES (?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, o.getOfficeName());
            setNullableInt(ps, 2, o.getParentOfficeId());
            ps.setInt(3, o.isActive() ? 1 : 0);
            ps.setInt(4, o.getSortOrder());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void update(Office o) throws SQLException {
        String sql = "UPDATE offices SET office_name=?, parent_office_id=?, active=?, sort_order=? WHERE office_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, o.getOfficeName());
            setNullableInt(ps, 2, o.getParentOfficeId());
            ps.setInt(3, o.isActive() ? 1 : 0);
            ps.setInt(4, o.getSortOrder());
            ps.setInt(5, o.getOfficeId());
            ps.executeUpdate();
        }
    }

    /** Soft delete preferred (keeps history intact); hard delete only if never referenced. */
    public void deactivate(int officeId) throws SQLException {
        String sql = "UPDATE offices SET active=0 WHERE office_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, officeId);
            ps.executeUpdate();
        }
    }

    public List<Office> findAll() throws SQLException {
        String sql = "SELECT * FROM offices ORDER BY parent_office_id IS NOT NULL, sort_order, office_name";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Office> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    public List<Office> findActive() throws SQLException {
        String sql = "SELECT * FROM offices WHERE active=1 ORDER BY parent_office_id IS NOT NULL, sort_order, office_name";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Office> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    public List<Office> findChildren(int parentOfficeId) throws SQLException {
        String sql = "SELECT * FROM offices WHERE parent_office_id=? AND active=1 ORDER BY sort_order, office_name";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parentOfficeId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Office> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    private Office mapRow(ResultSet rs) throws SQLException {
        Office o = new Office();
        o.setOfficeId(rs.getInt("office_id"));
        o.setOfficeName(rs.getString("office_name"));
        int parent = rs.getInt("parent_office_id");
        o.setParentOfficeId(rs.wasNull() ? null : parent);
        o.setActive(rs.getInt("active") == 1);
        o.setSortOrder(rs.getInt("sort_order"));
        return o;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}
