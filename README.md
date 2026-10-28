# DocTrackSys (Document Tracking System)

**DocTrackSys** is a robust, desktop-based Document Tracking System built using Java (Swing) and backed by a SQLite database. It is designed to manage, monitor, route, and secure corporate or institutional documentation across various office hierarchies and personnel departments.

---

## 🛠️ Tech Stack & Architecture

* **Language:** Java 8+ (or compatible JDK versions)
* **GUI Framework:** Java Swing / AWT
* **Database:** SQLite (Embedded relational database)
* **Build System:** Apache Ant (`build.xml`)
* **Logging System:** Custom application logging framework (`app.log`)

### MVC Architecture
The system follows a decoupled Model-View-Controller/DAO pattern:
* **`model/`**: Contains core data structures (`Document`, `Office`, `Personnel`, `User`, `Attachment`, `RoutingStep`).
* **`dao/` (Data Access Objects)**: Manages database interactions (`DocumentDAO`, `OfficeDAO`, `UserDAO`, etc.).
* **`ui/`**: Holds the Swing graphical interface elements (`MainFrame`, `LoginFrame`, dashboards, dialogs).
* **`util/`**: Essential utility classes handling cryptographic password hashing, CSV handling, file hash verifications, and system backups.

---

## 🚀 Key Features

* **Secure Authentication:** User login architecture utilizing hashed password verifications and brute-force attempt tracking mechanisms.
* **Document Lifecycle Management:** Complete processing of document types, tracking updates, ownership mappings, and metadata.
* **Granular Document Routing:** Visual modeling of organizational workflows allowing transactional document forwarding across distinct steps and office stations.
* **File Attachments Control:** Secure local storage mapping and system hash integration (`FileHashUtil`) to prevent file tampering or collision errors.
* **Organizational Chart Visualization:** Interactive representation of the operational hierarchy of company office nodes and mapped personnel units.
* **Comprehensive Audit Trail:** Automatic foreground capture of system operations logging transactional histories to an immutable database registry.
* **Automated Data Protection:** Built-in panels managing physical cryptographic data export and restoration structures.

---

## 📁 Repository Directory Structure

```text
DocTrackSys/
├── build.xml                     # Apache Ant build script
├── README.md                     # Project documentation
├── src/                          # Application source code
│   └── doctrack/
│       ├── Main.java             # System entry point
│       ├── model/                # Enterprise data entities
│       ├── dao/                  # Database connectivity layer
│       ├── db/                   # DB connection setup and raw schema.sql
│       ├── ui/                   # Swing GUI layout panels and components
│       ├── report/               # Analytical document report scripts
│       └── util/                 # Logger, backup engines, hash & password tools
├── lib/
│   └── sqlite-jdbc.jar           # Embedded SQLite relational connector driver
├── nbproject/                    # NetBeans IDE runtime project meta configurations
└── dist/                         # Distribution target folder compiled binaries
    ├── DocTrackSys.jar           # Executable client application
    ├── data/
    │   └── doctrack.db           # Live structural active SQLite database file
    └── logs/
        └── app.log               # Live operation debugging trail registry
```

---

## ⚡ Getting Started

### Prerequisites
* Ensure **Java Development Kit (JDK) 8** or higher is installed.
* Verify `ant` is configured in your global path variables if utilizing CLI compiling structures.

### Building from Source (Apache Ant)
To compile the system and bundle dependencies into the distribution package, open your terminal at the root of the project folder and run:
```bash
ant clean compile jar
```

### Running the Application
You can run the application directly from the generated executable jar container located inside the distributed output hierarchy:
```bash
java -jar dist/DocTrackSys.jar
```

---

## 🔒 Security & Utilities Implementation Notes

1. **Database Schema Initialization:** On standard deployments, the relational baseline can be initialized manually or evaluated sequentially via the included `src/doctrack/db/schema.sql` database build script.
2. **File Authenticity Control:** Document storage integrations require standard runtime configurations mapping toward system execution environments. Files uploaded to the data container utilize custom data streams verified continuously by automated hashing functions.
