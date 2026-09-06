package doctrack.db;

import doctrack.util.AppLogger;
import doctrack.util.AppPaths;
import doctrack.util.PasswordUtil;

import java.io.*;
import java.sql.*;

/**
 * Central database access point. The SQLite file lives at
 * &lt;app folder&gt;/data/doctrack.db, where "app folder" is resolved by
 * {@link AppPaths} to wherever the application itself is actually
 * running from -- not the OS/launcher's current working directory. This
 * guarantees the same install always finds (or creates) the same data,
 * regardless of how or from where it's launched.
 *
 * Only external dependency in the whole project: the SQLite JDBC driver
 * jar (sqlite-jdbc), added to the NetBeans project's Libraries.
 */
public final class DBConnection {

    private static final String DB_FILE = "doctrack.db";
    /** Bumped whenever schema.sql changes in a way that needs a migration step below. */
    private static final int CURRENT_SCHEMA_VERSION = 3;

    private static Connection connection;

    private DBConnection() { }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = createConnection();
        }
        return connection;
    }

    /**
     * Drops the cached connection without deleting any data, so the next
     * getConnection() call reopens from disk. Used after a backup restore
     * replaces the database file out from under a running app.
     */
    public static synchronized void reset() {
        closeConnection();
        connection = null;
    }

    public static File getDbFile() {
        return new File(AppPaths.getDataDir(), DB_FILE);
    }

    private static Connection createConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on classpath.", e);
        }

        File dbFile = getDbFile();
        boolean firstRun = !dbFile.exists();

        String url = "jdbc:sqlite:" + dbFile.getPath();
        Connection conn = DriverManager.getConnection(url);

        // enforce FK constraints (off by default in sqlite)
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }

        if (firstRun) {
            initializeSchema(conn);
            AppLogger.logInfo("New database created at " + dbFile.getPath());
        } else {
            runMigrations(conn);
        }
        return conn;
    }

    /** Runs schema.sql (bundled as a resource) to create all tables on first run. */
    private static void initializeSchema(Connection conn) throws SQLException {
        String sql = readSchemaResource();

        // Generate a fresh random salted hash for the seed admin account at
        // install time, rather than shipping one fixed salt/hash baked into
        // the jar for every install of the app.
        PasswordUtil.Hashed adminHash = PasswordUtil.hashNew("admin123".toCharArray());
        sql = sql.replace("__ADMIN_HASH__", adminHash.hashHex)
                 .replace("__ADMIN_SALT__", adminHash.saltHex);

        executeScript(conn, sql);
    }

    private static String readSchemaResource() throws SQLException {
        StringBuilder sql = new StringBuilder();
        try (InputStream is = DBConnection.class.getResourceAsStream("schema.sql")) {
            if (is == null) {
                throw new SQLException("schema.sql resource not found. "
                        + "Make sure it is included in the build under doctrack/db/.");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                sql.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read schema.sql", e);
        }
        return sql.toString();
    }

    private static void executeScript(Connection conn, String script) throws SQLException {
        // split on ";" at end of statements (schema.sql has no semicolons inside strings)
        String[] statements = script.split(";\\s*\\n");
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            // strip full-line comments so a comment-only fragment never gets executed
            String withoutComments = trimmed.replaceAll("(?m)^--.*$", "").trim();
            if (withoutComments.isEmpty()) continue;
            // use a fresh Statement per execution -- some JDBC driver versions
            // misbehave when a Statement object is reused across many executes
            try (Statement st = conn.createStatement()) {
                st.execute(withoutComments);
            }
        }
    }

    /**
     * Brings an existing database up to the current schema without losing
     * data. This is what lets an older data/doctrack.db (e.g. carried over
     * from a previous version of the app, or restored from an old backup)
     * keep working after the app is upgraded -- and defensively self-heals
     * a database that's missing a table entirely (e.g. one left in a
     * partially-created state by a crash during first run), not just one
     * that's missing a column.
     */
    private static void runMigrations(Connection conn) throws SQLException {
        // Every core table, defensively re-asserted with IF NOT EXISTS. This
        // is a no-op on a healthy database (every table already exists) and
        // a self-heal on a damaged/partial one, at negligible cost either way.
        ensureTable(conn, "users",
                "CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, password_salt TEXT, "
                        + "full_name TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'STAFF', "
                        + "active INTEGER NOT NULL DEFAULT 1, "
                        + "date_created TEXT NOT NULL DEFAULT (datetime('now','localtime')))");
        ensureTable(conn, "offices",
                "CREATE TABLE IF NOT EXISTS offices (office_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "office_name TEXT NOT NULL, parent_office_id INTEGER, active INTEGER NOT NULL DEFAULT 1, "
                        + "sort_order INTEGER NOT NULL DEFAULT 0, "
                        + "FOREIGN KEY (parent_office_id) REFERENCES offices(office_id) ON DELETE SET NULL)");
        ensureTable(conn, "personnel",
                "CREATE TABLE IF NOT EXISTS personnel (personnel_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "full_name TEXT NOT NULL, position TEXT, office_id INTEGER, "
                        + "active INTEGER NOT NULL DEFAULT 1, "
                        + "FOREIGN KEY (office_id) REFERENCES offices(office_id) ON DELETE SET NULL)");
        ensureTable(conn, "documents",
                "CREATE TABLE IF NOT EXISTS documents (document_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "tracking_no TEXT UNIQUE NOT NULL, doc_type TEXT NOT NULL, direction TEXT NOT NULL, "
                        + "subject TEXT NOT NULL, source_name TEXT, origin_office_id INTEGER, "
                        + "current_office_id INTEGER, current_holder_id INTEGER, "
                        + "status TEXT NOT NULL DEFAULT 'RECEIVED', priority TEXT NOT NULL DEFAULT 'NORMAL', "
                        + "date_received TEXT NOT NULL, due_date TEXT, date_closed TEXT, remarks TEXT, "
                        + "date_created TEXT NOT NULL DEFAULT (datetime('now','localtime')), created_by INTEGER, "
                        + "FOREIGN KEY (origin_office_id) REFERENCES offices(office_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (current_office_id) REFERENCES offices(office_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (current_holder_id) REFERENCES personnel(personnel_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL)");
        ensureTable(conn, "routing_steps",
                "CREATE TABLE IF NOT EXISTS routing_steps (step_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "document_id INTEGER NOT NULL, batch_id TEXT, from_office_id INTEGER, "
                        + "to_office_id INTEGER, to_personnel_id INTEGER, action TEXT NOT NULL, "
                        + "action_date TEXT NOT NULL DEFAULT (datetime('now','localtime')), remarks TEXT, "
                        + "logged_by INTEGER, "
                        + "FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (from_office_id) REFERENCES offices(office_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (to_office_id) REFERENCES offices(office_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (to_personnel_id) REFERENCES personnel(personnel_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (logged_by) REFERENCES users(user_id) ON DELETE SET NULL)");
        ensureTable(conn, "attachments",
                "CREATE TABLE IF NOT EXISTS attachments (attachment_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "document_id INTEGER NOT NULL, file_name TEXT NOT NULL, file_path TEXT NOT NULL, "
                        + "description TEXT, uploaded_by INTEGER, "
                        + "date_uploaded TEXT NOT NULL DEFAULT (datetime('now','localtime')), "
                        + "FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE SET NULL)");
        ensureTable(conn, "action_log",
                "CREATE TABLE IF NOT EXISTS action_log (log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "document_id INTEGER, user_id INTEGER, action_type TEXT NOT NULL, details TEXT, "
                        + "action_date TEXT NOT NULL DEFAULT (datetime('now','localtime')), "
                        + "FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE SET NULL, "
                        + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL)");
        ensureTable(conn, "schema_meta",
                "CREATE TABLE IF NOT EXISTS schema_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT)");

        if (!hasColumn(conn, "users", "password_salt")) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE users ADD COLUMN password_salt TEXT");
            }
            AppLogger.logInfo("Migration: added users.password_salt (legacy accounts will be "
                    + "upgraded to salted hashing automatically on next successful login)");
        }

        if (!hasColumn(conn, "routing_steps", "batch_id")) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE routing_steps ADD COLUMN batch_id TEXT");
            }
            AppLogger.logInfo("Migration: added routing_steps.batch_id (multi-recipient routing support)");
        }

        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_routing_batch ON routing_steps(batch_id)");
        }

        boolean documentTypesJustCreated = !hasTable(conn, "document_types");
        ensureTable(conn, "document_types",
                "CREATE TABLE IF NOT EXISTS document_types (type_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "type_name TEXT UNIQUE NOT NULL, active INTEGER NOT NULL DEFAULT 1, "
                        + "sort_order INTEGER NOT NULL DEFAULT 0)");
        if (documentTypesJustCreated) {
            // Seed it with the types this app has always shipped with, so an
            // upgraded install doesn't suddenly show an empty type list --
            // and back-fill any types already in use by existing documents
            // that aren't in that default set (e.g. a custom type someone
            // had been typing free-hand before this lookup table existed).
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT OR IGNORE INTO document_types (type_name, sort_order) VALUES "
                        + "('Memo', 1), ('Letter', 2), ('Communication', 3), "
                        + "('Compliance Report', 4), ('Directive', 5), ('Other', 6)");
            }
            if (hasTable(conn, "documents")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("INSERT OR IGNORE INTO document_types (type_name, sort_order) "
                            + "SELECT DISTINCT doc_type, 99 FROM documents "
                            + "WHERE doc_type IS NOT NULL AND doc_type != ''");
                }
            }
            AppLogger.logInfo("Migration: added document_types lookup table");
        }

        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_action_log_date ON action_log(action_date)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_action_log_document ON action_log(document_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_action_log_user ON action_log(user_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_action_log_type ON action_log(action_type)");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_meta (meta_key, meta_value) VALUES ('schema_version', ?) "
                        + "ON CONFLICT(meta_key) DO UPDATE SET meta_value=excluded.meta_value")) {
            ps.setString(1, String.valueOf(CURRENT_SCHEMA_VERSION));
            ps.executeUpdate();
        }
    }

    private static boolean hasTable(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    private static void ensureTable(Connection conn, String table, String createSql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSql);
        }
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                AppLogger.logError("Failed to close DB connection", ex);
            }
        }
    }
}
