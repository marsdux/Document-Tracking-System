package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.Personnel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PersonnelDAO {

    public int insert(Personnel p) throws SQLException {
        String sql = "INSERT INTO personnel (full_name, position, office_id, active) VALUES (?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getFullName());
            ps.setString(2, p.getPosition());
            setNullableInt(ps, 3, p.getOfficeId());
            ps.setInt(4, p.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void update(Personnel p) throws SQLException {
        String sql = "UPDATE personnel SET full_name=?, position=?, office_id=?, active=? WHERE personnel_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getFullName());
            ps.setString(2, p.getPosition());
            setNullableInt(ps, 3, p.getOfficeId());
            ps.setInt(4, p.isActive() ? 1 : 0);
            ps.setInt(5, p.getPersonnelId());
            ps.executeUpdate();
        }
    }

    public void deactivate(int personnelId) throws SQLException {
        String sql = "UPDATE personnel SET active=0 WHERE personnel_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personnelId);
            ps.executeUpdate();
        }
    }

    public List<Personnel> findAll() throws SQLException {
        return runQuery(baseSelect() + " ORDER BY p.full_name");
    }

    public List<Personnel> findActive() throws SQLException {
        return runQuery(baseSelect() + " WHERE p.active=1 ORDER BY p.full_name");
    }

    public List<Personnel> findByOffice(int officeId) throws SQLException {
        String sql = baseSelect() + " WHERE p.office_id=? AND p.active=1 ORDER BY p.full_name";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, officeId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Personnel> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    private List<Personnel> runQuery(String sql) throws SQLException {
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Personnel> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    private String baseSelect() {
        return "SELECT p.*, o.office_name FROM personnel p LEFT JOIN offices o ON p.office_id = o.office_id";
    }

    private Personnel mapRow(ResultSet rs) throws SQLException {
        Personnel p = new Personnel();
        p.setPersonnelId(rs.getInt("personnel_id"));
        p.setFullName(rs.getString("full_name"));
        p.setPosition(rs.getString("position"));
        int officeId = rs.getInt("office_id");
        p.setOfficeId(rs.wasNull() ? null : officeId);
        p.setActive(rs.getInt("active") == 1);
        p.setOfficeName(rs.getString("office_name"));
        return p;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}
