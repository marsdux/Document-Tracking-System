# Document Tracking System (DocTrackSys)

A Java Swing desktop application for tracking documents, memos and communications through an organization: who holds them, what was done, proof of compliance, and how long each step took. Data is stored locally in SQLite.

<!--
Add screenshots after you upload them to a docs/ folder, then remove these comment markers:
![Document list](docs/document-list.png)
![Routing and history](docs/routing.png)
-->

## Features

- **Secure login:** salted PBKDF2 password hashing, a lockout after 5 failed attempts (5 minutes), and role separation between admins and other users.
- **Document management:** add, edit and search incoming and outgoing documents, with editable document types.
- **Routing:** forward a document to one or several recipients, or forward several documents at once. Every step is recorded, with a full printable history.
- **Evidence attachments:** attach scanned proof to a specific action, with file hashing to detect tampering.
- **Organization structure:** editable offices and personnel hierarchy used for routing.
- **Monitoring and reports:** overdue detection, turnaround-time reports, a summary report and CSV export.
- **Audit log (admin):** system-wide record of actions, including login successes and failures.
- **Backup and restore (admin):** verified full backup and restore with tamper detection.
- **Reliability:** automatic schema migrations on startup, file-based error logging, and database and data paths that stay the same wherever the app is launched from.

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| UI | Swing / AWT |
| Database | SQLite via `sqlite-jdbc` (`lib/sqlite-jdbc.jar`) |
| Build | NetBeans, Apache Ant |
| Architecture | model / DAO / UI layers |

## Getting started

**Requirements:** JDK 17 or newer (not just a JRE). NetBeans or Ant.

**NetBeans:** *File > Open Project*, select the `DocTrackSys` folder, confirm `lib/sqlite-jdbc.jar` is under *Properties > Libraries*, then run.

**Command line:**

```bash
cd DocTrackSys
ant clean jar
java -jar dist/DocTrackSys.jar
```

The database is created automatically on first run in a `data/` folder next to the application.

**Demo login:** username `admin`, password `admin123`. Change this before using the app with real data. A change-password screen is not built yet.

## Project structure

```text
DocTrackSys/
├── build.xml
├── lib/sqlite-jdbc.jar
└── src/doctrack/
    ├── Main.java
    ├── model/    Document, Office, Personnel, User, Attachment, RoutingStep
    ├── dao/      database access for each model
    ├── db/       DBConnection and schema.sql
    ├── ui/       login, main window, panels and dialogs
    ├── report/   summary report generation
    └── util/     password hashing, file hashing, backup, CSV, logging, paths
```

## Roadmap

- CSV import
- User management screen and change-password screen
- Fine-grained, role-based permissions
- Scheduled backups
- Archiving old audit log entries

## Author

Built by [@marsdux](https://github.com/marsdux).
