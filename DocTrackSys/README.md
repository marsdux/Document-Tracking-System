# Document Tracking System (DocTrackSys)

A Java Swing desktop application for tracking documents, memos, and
communications through an organization — who has them, what's been done,
proof of compliance, and how long each step took.

Every feature below has been compiled and exercised end-to-end by an
automated headless test (DB init and migration, login and lockout,
multi-recipient routing, batch forward, attachments, CSV export, full
backup/export/import with tamper detection) before being handed to you.
It's a working, audited starting point — see "What's built vs. what's
next" below for the honest list of what's still missing.

## Requirements

- **JDK 17 or newer** (not just a JRE — you need `javac` to build in
  NetBeans). If `java -version` works but you get "javac not found",
  install a JDK, e.g. on Ubuntu/Debian: `sudo apt install openjdk-21-jdk`.
- NetBeans IDE (any recent version), OR Apache Ant if you want to build
  from the command line without NetBeans.

## Opening in NetBeans

1. File → Open Project → select the `DocTrackSys` folder.
2. NetBeans will recognize it as a Java SE (Ant-based) project and
   regenerate `nbproject/build-impl.xml` automatically the first time you
   build — that's expected, ignore the message if it appears.
3. Right-click the project → Properties → Libraries → confirm
   `lib/sqlite-jdbc.jar` is listed under Compile-time Libraries. It should
   already be wired up via `nbproject/project.properties`
   (`file.reference.sqlite-jdbc.jar`), but check if the build complains
   about `org.sqlite.JDBC` not being found.
4. Right-click the project → Run.

## Building from the command line (no NetBeans)

```
cd DocTrackSys
ant clean jar
java -jar dist/DocTrackSys.jar
```

## First run

- The SQLite database is created automatically the first time the app
  runs, in a `data/` folder placed **next to wherever the app itself is
  running from** (see "Portable paths" below) — no manual setup, and no
  risk of it landing in a different folder depending on how you launched it.
- Default login: **username `admin`, password `admin123`**. Change this
  immediately in a real deployment — there's no "change password" screen
  in the UI yet (see below), so for now call `UserDAO.changePassword()`
  from a small script. The password is stored as a salted PBKDF2 hash,
  not reversible, so there's no way to "look up" the old one.

## Project structure

```
src/doctrack/
  db/       DBConnection.java, schema.sql   -- connection, schema init, migrations
  model/    Document, Office, Personnel, RoutingStep, Attachment, User
  dao/      one CRUD class per model, all plain JDBC (no ORM), plus AuditLogDAO
  ui/       LoginFrame, MainFrame, and all panels/dialogs
  report/   SummaryReportGenerator -- counts, overdue list, turnaround time
  util/     AppPaths (portable folders), AppLogger (file logging),
            BackupService (verified export/import), FileHashUtil (SHA-256),
            PasswordUtil (salted PBKDF2), LoginAttemptTracker (lockout),
            UITheme (fonts/colors), CsvUtil (Excel-compatible export),
            AuditReportPrinter / AuditLogPrinter (formatted printable reports),
            AttachmentFileUtil (shared secure file-copy for all attachments)
lib/        sqlite-jdbc.jar (the one external dependency)
data/       doctrack.db lives here once you run the app (created next to the app)
attachments/  scanned files, organized by document ID (created next to the app)
backups/    exported/auto-safety backups land here by default
logs/       app.log -- errors and key events, for troubleshooting a live install
```

## What's new in this round

### 10. Evidence attachments tied to a specific tracking action
Previously, attachments lived at the document level only — a general
bucket of files. Now every tracking action (received, forwarded,
reviewed, complied, returned, closed) can carry its **own** scanned
evidence, distinct from that general bucket:

- **Adding a document** now has an optional "Scanned Copy (proof of
  receipt)" field — attach the incoming scan right when you log it, tied
  to that document's initial RECEIVED entry.
- **Logging any action** in `RoutingDialog` (forward/review/comply/
  return/close) has an optional "Attach Evidence" field — the file
  becomes proof of *that specific action*, not just a loose document
  attachment. Forward one document to five recipients with one evidence
  file selected, and each of the five routing entries gets its own
  independent copy of it.
- The history table gained an **Evidence** column showing a per-entry
  count, and a **View/Manage Evidence for Selected** button opens the
  same view/print/remove interface scoped to just that one entry.
- **Printing** now actually prints — `Desktop.getDesktop().print(file)`
  sends the file straight to the system print dialog when supported,
  falling back to "open it and print from there" with a clear message
  when the platform doesn't support direct printing.

Design notes worth knowing:
- Each attachment — general or evidence — always gets its **own**
  independent physical file copy on disk, even when the same source file
  is attached to five recipients in one send. This costs a bit of extra
  disk space for identical scans, but means removing evidence from any
  one entry can never delete a file another entry still depends on — no
  shared-reference bookkeeping, no risk of an accidental cross-deletion.
  Verified in testing by attaching one file across a 2-recipient send,
  confirming both copies exist independently, then removing one and
  confirming the other was untouched.
- Deleting a routing entry or a whole document cleans up its evidence's
  *physical files* before the database cascade removes the metadata rows
  — the cascade alone only deletes the DB rows, not the files on disk.
- All of this is reused, secured, and tested through one shared
  `AttachmentFileUtil` (filename sanitization + path-containment check
  against the folder it's supposed to land in) rather than three
  near-identical copies of the same file-handling code that could drift
  out of sync with each other over time.

### 11. Migration and cleanup fixes found during this round's audit
- **Real migration gap closed:** the `attachments` table's new
  `routing_step_id` column is now added via `ALTER TABLE` for existing
  installs that already have an `attachments` table (the prior round's
  defensive table-recreation only covered a table being *entirely*
  missing, not an existing table needing a new column) — verified with a
  dedicated test that builds a pre-migration database, confirms existing
  attachment data survives the migration untouched, and confirms the new
  column is immediately usable afterward.
- **Document deletion now cleans up every attachment**, not just
  general/document-level ones — a document's per-action evidence files
  are also removed from disk before the row cascade-deletes, closing a
  gap that would otherwise have left orphaned evidence files behind.

## What's new in the previous round

### 7. Editable document types
The **Document Type** field in Add Document now has **+** / **−** buttons
right next to it — add a new type on the fly, or remove one your office
no longer uses. Types are stored in their own table (`document_types`,
seeded with the original six), not hardcoded, and removing a type is a
soft delete: any document that already used it keeps that type text
untouched (you're told how many documents use it before you remove it),
it just stops being offered for new ones. Adding a type that already
exists is rejected with a clear message instead of a raw SQL error.

### 8. System-wide Audit Log (admin-only tab)
A new **Audit Log** tab shows every recorded operation across the whole
app — not just one document's history (that's still `RoutingDialog`),
but everything: document create/edit/delete, routing corrections, office/
personnel changes, document-type changes, attachments, CSV/backup
exports and imports, and login successes/failures. Each entry shows
**when**, **who**, **what type of action**, **which document** (if
applicable), and **details** — filterable by action type, date range, and
free-text keyword search (parameterized queries throughout, verified
resistant to SQL injection in testing). Results default to the most
recent 500 entries with a "Show all" option, so the view stays responsive
as the log grows. **Print** produces a formatted report the same way the
per-document audit trail does; **Export to Excel (CSV)** gives you an
external copy (with the same formula-injection guard as document export).

This surfaced a real coverage gap worth closing: office/personnel edits
and login attempts weren't being logged anywhere before this. They are now.

### 9. Final hardening pass for release
- **Minimal role-based access control.** The Audit Log and Backup &
  Restore tabs — the two screens that can expose the full activity
  history or wipe/replace all data — are now visible only to users with
  the `ADMIN` role. Every other tab remains available to all logged-in
  users, since the app doesn't yet have a granular permission model (see
  "not yet built" below).
- **Real migration bug found and fixed.** The schema-migration path only
  defensively re-created two tables (`document_types`, `schema_meta`);
  every other table was assumed to already exist, which is true for a
  normal upgrade but not for a database left in a partially-created state
  (e.g. by a crash during a prior first run). `runMigrations()` now
  defensively re-asserts every core table with `CREATE TABLE IF NOT
  EXISTS`, so a damaged or partial database self-heals on next launch
  instead of throwing "no such table" the first time something touches
  the missing one. Caught by testing a deliberately-damaged database, not
  by inspection — worth knowing since it means this class of bug doesn't
  announce itself until the specific missing table is touched.
- **Login attempts are now audited**, success and failure, which also
  gives you a real trail to review if you ever suspect someone's been
  trying to guess a password (paired with the existing 5-attempt lockout).

## What's new in the previous round

### 6. Usability & data-correction fixes
- **Tab key in Remarks fields** no longer jumps out of the field on the
  first press — `UITheme.enableTabToAdvanceFocus()` makes Tab/Shift+Tab
  move focus to the next/previous field like every other field in the
  form, instead of inserting a literal tab character (Swing's default,
  disorienting `JTextArea` behavior).
- **Routing/tracking history is now correctable.** In `RoutingDialog`,
  select any entry and use **Edit Selected Entry** or **Delete Selected
  Entry** to fix a mistake (wrong recipient, wrong action, a duplicate
  entry) without leaving it in the trail forever. The original timestamp
  is preserved on edit (only the corrected fields change); the document's
  "current holder/status" is automatically recalculated from whatever
  history remains afterward, so a correction never leaves the document
  pointing at a recipient or status with no supporting entry. Both
  actions write their own `action_log` entry — correcting or removing a
  mistake is itself recorded, which is the right instinct for an
  accountability system.
- **Tracking No. is manually encoded** when adding a document — the field
  now starts pre-filled with a suggested auto-generated number but is
  fully editable, so it matches whatever numbering scheme your office
  already uses. Saving with a number that's already in use is rejected
  with a clear "already in use" message rather than a raw SQL error
  (existing documents still can't have their tracking no. changed after
  creation, since other data references it).
- **Documents can be deleted** from the Documents tab — multi-select
  works here too, so erroneous, redundant, or discarded entries can be
  cleared in bulk. Deleting a document cascades its routing history and
  attachment records, and removes the actual attachment files from disk
  (not just the database rows, so nothing is left orphaned). It's logged
  to `action_log` before deletion, and the confirmation dialog reminds
  you a backup is one click away if you're not certain.

## What's new in the previous round

### 1. Full history trace, viewable and printable
`RoutingDialog` ("Route / Track History") now shows, for every document:
who currently has it (including when it's been distributed to several
people at once), and a complete chronological trail — timestamp, action,
from/to office, to personnel, remarks, and who logged it. The **Print
Full Audit Trail** button (`AuditReportPrinter`) produces a proper
formatted, paginated report — document header plus the full numbered
history — not just a raw table dump.

### 2. Multi-recipient / multi-document forwarding
A `batch_id` groups every routing row created by one "send" action.
- **One document, several recipients:** in `RoutingDialog`, the recipient
  list is multi-select (offices and/or specific people, Ctrl/Shift+Click)
  — one Log Action click creates one routing entry per recipient, all in
  a single database transaction.
- **Several documents, one or more recipients:** select multiple rows in
  the main document list (now multi-select) and click **Batch Forward**.
  Every (document × recipient) pair gets its own routing row, sharing one
  batch id, inserted atomically — the whole batch succeeds or none of it
  does.

Design note: a document's "current holder" field stays single-valued for
simple display, set to the first recipient of the latest send. The full
distribution list for that send is always recoverable via
`RoutingDAO.findCurrentRecipients()` and shown in the dialog ("Currently
distributed to N: ...") — nothing about who received it is lost.

### 3. Portable paths (`AppPaths`)
`data/`, `attachments/`, `backups/`, and `logs/` are now resolved
relative to the actual location of the running application (the folder
containing `DocTrackSys.jar`), not the OS/launcher's working directory.
Previously a relative `new File("data")` meant two different launch
methods (double-click vs. a shortcut vs. a terminal in a different folder)
could point at two different, silently-created empty databases. That
class of bug is now closed — verified by logging to `logs/app.log` and
confirming it always lands next to the jar regardless of launch method.

### 4. Verified full backup/restore (`BackupService`)
**Backup & Restore** tab. Export bundles the database (via SQLite's
`VACUUM INTO`, which takes a live consistent snapshot — not a raw file
copy that could catch a write mid-flight) and every attachment file into
one zip, together with a manifest listing a SHA-256 checksum for each
file. Import re-verifies every checksum before touching anything: a
corrupted or altered archive is rejected outright, with nothing changed.
A safety backup of your *current* data is taken automatically before any
restore, so a restore can always be undone by importing that safety
backup again. This is what makes an exported file trustworthy even after
being copied to a different computer with a completely fresh install.

### 5. Security & reliability audit
This was a real audit, not a rubber stamp — it found and fixed two
genuine bugs along with the security hardening:

- **Bug found: connection churn.** Every DAO call was closing the shared
  database connection via `try-with-resources` on the `Connection` object
  itself. It "worked" because the connection is a lazily-recreated
  singleton, but it meant reconnecting to SQLite on literally every query.
  Fixed across all 7 DAOs — connections are now only closed at app
  shutdown.
- **Bug found: raw exception on a corrupted backup.** A tampered backup
  archive surfaced as an unhandled `ZipException` instead of the intended
  clean error message. Fixed — now reported as a normal "integrity check
  failed" dialog.
- **Password storage:** upgraded from a single unsalted SHA-256 digest to
  salted PBKDF2-HMAC-SHA256 (120,000 iterations, random salt per user).
  Accounts created under the old scheme are verified once against the
  legacy digest and transparently re-hashed with a fresh salt on that
  successful login — no forced password reset, no manual migration step.
- **CSV/formula injection:** a document field starting with `=`, `+`,
  `-`, or `@` is now neutralized on export so it can't execute as a
  formula when the file is opened in Excel.
- **Attachment path traversal:** uploaded file names are sanitized
  (directory separators and control characters stripped) and the final
  destination path is verified to stay inside the intended attachments
  folder before the file is written.
- **Backup zip-slip:** every entry in an imported archive is checked to
  resolve inside the extraction folder before being written, so a crafted
  archive can't write files outside where it's supposed to.
- **Login lockout:** 5 failed attempts locks a username out for 5 minutes
  (in-memory, per process — appropriate for a single-user desktop tool).
- **Schema migrations:** `DBConnection` now checks an existing database
  for missing columns (e.g. `password_salt`, `batch_id`) on every startup
  and adds them automatically, so upgrading the app doesn't strand
  existing installs on an old schema or lose their data.
- **Error logging:** unexpected errors now write to `logs/app.log`
  (context + stack trace) instead of only a one-line dialog the user
  would have to transcribe by hand — and a global uncaught-exception
  handler (`Main.java`) catches anything that would otherwise silently
  kill the UI thread.
- **Date validation:** Date Received / Due Date are now validated as real
  `yyyy-mm-dd` dates before a document can be saved.

## How the original design maps to what you asked for

- **Accountability / who-did-what / timely monitoring** — every hop a
  document takes is written to `routing_steps` with a timestamp and the
  logged-in user; `action_log` (via `AuditLogDAO`) additionally records
  system-level events (create, edit, route, attach, detach) for a
  system-wide accountability log, not just per-document.
- **Flexible org structure you can edit yourself** — Organization
  Structure tab. Offices are a self-referencing tree, personnel are
  assigned to offices, both can be renamed, moved, or deactivated at any
  time. Deactivating keeps history intact (soft delete).
- **Readable fonts/colors** — `UITheme.java` sets one base font and a
  consistent status color palette across every screen.
- **Timestamps / duration metrics** — the Reports tab computes average
  turnaround time (received → closed) and lists everything past its due
  date.

## Why CSV instead of real .xlsx

You asked for "no libraries." Writing a genuine binary `.xlsx` file
without a library like Apache POI isn't practically achievable in plain
Java — the format is a zipped bundle of interdependent XML parts. CSV
opens directly in Excel/Sheets/LibreOffice and needs zero dependencies.
Say the word and I'll add Apache POI for real `.xlsx` — a one-line
dependency add, not a rewrite.

## The one dependency: sqlite-jdbc.jar

Pure Java can't talk to a SQLite file without a JDBC driver. It's a
single jar, added the normal NetBeans way, not a framework.
`lib/sqlite-jdbc.jar` is already included.

## What's built vs. what's next

**Working now:** login/auth with lockout, add/edit/search documents,
multi-recipient routing with printable audit trail, batch forwarding
across documents, attachments, editable org structure, CSV export,
overdue detection, turnaround-time reporting, full verified backup/
restore, forward-compatible schema migrations, file-based error logging.

**Not yet built — natural next steps:**
- CSV **import** back into the database (the parser in `CsvUtil.readRows()`
  exists; wiring it to `DocumentDAO.insert()` with validation/duplicate
  handling doesn't)
- A user-management screen (add/deactivate users, change password) — the
  DAO methods exist, there's no UI for them yet
- **Granular role-based permissions.** Right now there are exactly two
  tiers: ADMIN (sees Audit Log + Backup & Restore, plus everything else)
  and everyone else (sees everything except those two tabs). There's no
  way yet to say "this person can view documents but not delete them," or
  to restrict routing/org-structure changes by role. If more than one
  trust level of user will use this app, this is the next thing to build.
- A "change my password" screen for the logged-in user
- Scheduled/automatic backups (currently manual, on demand, from the
  Backup & Restore tab)
- Purging/archiving old audit log entries (currently the log grows
  indefinitely; the "Show all" toggle in the Audit Log tab is a stopgap
  for viewing it, not a maintenance tool)

Tell me which of these matters most and I'll build it next.
