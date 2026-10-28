package doctrack.dao;

import doctrack.db.DBConnection;
import doctrack.model.User;
import doctrack.util.PasswordUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    /**
     * Returns the user if username/password match an active account, else
     * null. Accounts still on the old unsalted-SHA-256 scheme (password_salt
     * IS NULL) are checked against the legacy digest and, on success,
     * transparently rehashed with a fresh random salt so they never need to
     * be checked the old way again.
     */
    public User authenticate(String username, char[] password) throws SQLException {
        String sql = "SELECT * FROM users WHERE username=? AND active=1";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String storedHash = rs.getString("password_hash");
                String storedSalt = rs.getString("password_salt");
                int userId = rs.getInt("user_id");

                boolean ok;
                if (storedSalt != null && !storedSalt.isEmpty()) {
                    ok = PasswordUtil.verify(password, storedHash, storedSalt);
                } else {
                    ok = PasswordUtil.verifyLegacy(password, storedHash);
                    if (ok) {
                        upgradeToSaltedHash(userId, password);
                    }
                }
                return ok ? mapRow(rs) : null;
            }
        }
    }

    private void upgradeToSaltedHash(int userId, char[] password) throws SQLException {
        PasswordUtil.Hashed hashed = PasswordUtil.hashNew(password);
        String sql = "UPDATE users SET password_hash=?, password_salt=? WHERE user_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashed.hashHex);
            ps.setString(2, hashed.saltHex);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    public int insert(User u, char[] password) throws SQLException {
        PasswordUtil.Hashed hashed = PasswordUtil.hashNew(password);
        String sql = "INSERT INTO users (username, password_hash, password_salt, full_name, role, active) "
                + "VALUES (?,?,?,?,?,?)";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, hashed.hashHex);
            ps.setString(3, hashed.saltHex);
            ps.setString(4, u.getFullName());
            ps.setString(5, u.getRole());
            ps.setInt(6, u.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void changePassword(int userId, char[] newPassword) throws SQLException {
        PasswordUtil.Hashed hashed = PasswordUtil.hashNew(newPassword);
        String sql = "UPDATE users SET password_hash=?, password_salt=? WHERE user_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashed.hashHex);
            ps.setString(2, hashed.saltHex);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    public void setActive(int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET active=? WHERE user_id=?";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY full_name";
        Connection conn = DBConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<User> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getInt("active") == 1);
        return u;
    }
}
