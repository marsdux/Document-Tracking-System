-- Document Tracking System schema
-- SQLite. Designed to keep the organizational/routing structure editable
-- at runtime rather than hardcoded.

PRAGMA foreign_keys = ON;

-- Users who log in to the system
CREATE TABLE IF NOT EXISTS users (
    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    password_salt TEXT,                             -- NULL = legacy unsalted hash, upgraded on next login
    full_name     TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'STAFF',   -- ADMIN, ENCODER, VIEWER, etc. (free text, editable)
    active        INTEGER NOT NULL DEFAULT 1,
    date_created  TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

-- Offices / Units / Divisions -- the org structure is a self-referencing
-- tree so it can be rearranged freely (add, rename, move, deactivate).
CREATE TABLE IF NOT EXISTS offices (
    office_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    office_name   TEXT NOT NULL,
    parent_office_id INTEGER,                      -- NULL = top level
    active        INTEGER NOT NULL DEFAULT 1,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (parent_office_id) REFERENCES offices(office_id) ON DELETE SET NULL
);

-- Personnel assigned to offices. A person can be reassigned any time;
-- history of a document's routing still points to the office+person as
-- they were at the time (see routing_steps which stores a name snapshot).
CREATE TABLE IF NOT EXISTS personnel (
    personnel_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name     TEXT NOT NULL,
    position      TEXT,
    office_id     INTEGER,
    active        INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (office_id) REFERENCES offices(office_id) ON DELETE SET NULL
);

-- Core document/memo/communication record
CREATE TABLE IF NOT EXISTS documents (
    document_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    tracking_no     TEXT UNIQUE NOT NULL,           -- e.g. DTS-2026-000123
    doc_type        TEXT NOT NULL,                  -- Memo, Letter, Communication, Compliance, etc.
    direction       TEXT NOT NULL,                  -- INCOMING, OUTGOING, INTERNAL
    subject         TEXT NOT NULL,
    source_name     TEXT,                           -- who/where it came from
    origin_office_id INTEGER,                       -- originating office, if internal
    current_office_id INTEGER,                      -- where it currently sits
    current_holder_id INTEGER,                      -- personnel currently holding it
    status          TEXT NOT NULL DEFAULT 'RECEIVED',-- RECEIVED, IN_PROGRESS, FORWARDED, COMPLIED, CLOSED, OVERDUE
    priority        TEXT NOT NULL DEFAULT 'NORMAL',  -- NORMAL, URGENT, HIGH
    date_received   TEXT NOT NULL,                   -- when it entered the system
    due_date        TEXT,                             -- expected response/compliance date
    date_closed     TEXT,
    remarks         TEXT,
    date_created    TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    created_by      INTEGER,
    FOREIGN KEY (origin_office_id) REFERENCES offices(office_id) ON DELETE SET NULL,
    FOREIGN KEY (current_office_id) REFERENCES offices(office_id) ON DELETE SET NULL,
    FOREIGN KEY (current_holder_id) REFERENCES personnel(personnel_id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Routing history: every hop of a document through offices/personnel.
-- This is what lets the org structure stay flexible while still giving
-- a full accountability trail. A single "send" action can fan out to
-- multiple recipients (multiple offices/personnel, possibly across
-- multiple documents) -- all rows created by that one send share the
-- same batch_id so the UI can display and print them as one entry.
CREATE TABLE IF NOT EXISTS routing_steps (
    step_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id     INTEGER NOT NULL,
    batch_id        TEXT,                            -- groups rows created by one multi-recipient send
    from_office_id  INTEGER,
    to_office_id    INTEGER,
    to_personnel_id INTEGER,
    action          TEXT NOT NULL,                  -- RECEIVED, FORWARDED, REVIEWED, COMPLIED, RETURNED, CLOSED
    action_date     TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    remarks         TEXT,
    logged_by       INTEGER,
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE,
    FOREIGN KEY (from_office_id) REFERENCES offices(office_id) ON DELETE SET NULL,
    FOREIGN KEY (to_office_id) REFERENCES offices(office_id) ON DELETE SET NULL,
    FOREIGN KEY (to_personnel_id) REFERENCES personnel(personnel_id) ON DELETE SET NULL,
    FOREIGN KEY (logged_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Attachments: scanned hardcopies, proof of compliance/response, etc.
-- Files themselves live on disk under /attachments; this stores the path.
CREATE TABLE IF NOT EXISTS attachments (
    attachment_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id     INTEGER NOT NULL,
    file_name       TEXT NOT NULL,
    file_path       TEXT NOT NULL,
    description     TEXT,
    uploaded_by     INTEGER,
    date_uploaded   TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Document types (Memo, Letter, etc.) -- an editable lookup list rather
-- than a hardcoded set, so an office can add or retire its own types.
-- documents.doc_type stores the type name as plain text (not a foreign
-- key), so removing a type from this list never corrupts existing
-- documents that already used it -- it just stops appearing as a choice
-- for new ones.
CREATE TABLE IF NOT EXISTS document_types (
    type_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    type_name     TEXT UNIQUE NOT NULL,
    active        INTEGER NOT NULL DEFAULT 1,
    sort_order    INTEGER NOT NULL DEFAULT 0
);

-- General accountability/audit log (who did what, when, in the system itself)
CREATE TABLE IF NOT EXISTS action_log (
    log_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id     INTEGER,
    user_id         INTEGER,
    action_type     TEXT NOT NULL,                  -- CREATE, EDIT, DELETE, ROUTE, ATTACH, PRINT, EXPORT
    details         TEXT,
    action_date     TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_office ON documents(current_office_id);
CREATE INDEX IF NOT EXISTS idx_routing_document ON routing_steps(document_id);
CREATE INDEX IF NOT EXISTS idx_routing_batch ON routing_steps(batch_id);
CREATE INDEX IF NOT EXISTS idx_attachments_document ON attachments(document_id);
CREATE INDEX IF NOT EXISTS idx_action_log_date ON action_log(action_date);
CREATE INDEX IF NOT EXISTS idx_action_log_document ON action_log(document_id);
CREATE INDEX IF NOT EXISTS idx_action_log_user ON action_log(user_id);
CREATE INDEX IF NOT EXISTS idx_action_log_type ON action_log(action_type);

-- Tracks the schema version so the app can detect and apply migrations
-- when an older database is opened by a newer build (see DBConnection).
CREATE TABLE IF NOT EXISTS schema_meta (
    meta_key    TEXT PRIMARY KEY,
    meta_value  TEXT
);
INSERT OR IGNORE INTO schema_meta (meta_key, meta_value) VALUES ('schema_version', '3');

-- Seed a default admin user (username: admin / password: admin123).
-- Stored as a salted PBKDF2-HMAC-SHA256 hash (120,000 iterations) --
-- NOT a plain SHA-256 digest. Change this password after first login.
INSERT OR IGNORE INTO users (user_id, username, password_hash, password_salt, full_name, role)
VALUES (1, 'admin',
        '__ADMIN_HASH__',
        '__ADMIN_SALT__',
        'System Administrator', 'ADMIN');

-- Seed a top-level office so the org tree isn't empty on first run
INSERT OR IGNORE INTO offices (office_id, office_name, parent_office_id)
VALUES (1, 'Office of the Head', NULL);

-- Seed the default set of document types (all editable/removable afterward)
INSERT OR IGNORE INTO document_types (type_name, sort_order) VALUES
    ('Memo', 1), ('Letter', 2), ('Communication', 3),
    ('Compliance Report', 4), ('Directive', 5), ('Other', 6);
