# WeEFS — Extended File System Library for Java

**WeEFS** (Work/Extended/Embedded File System) is a Java NIO.2-compatible
file-system library that exposes **ZIP/TAR archives** and **NFS mounts** as
standard `java.nio.file.FileSystem` instances. It ships with a **Swing desktop
GUI** for browsing, editing, and managing files inside archives and on remote
NFS shares.

---

## Features

| Capability | Description |
|---|---|
| **Archive browsing** | Open ZIP and TAR files as virtual file systems. |
| **NFS support** | Mount NFS exports and browse/edit remote files. |
| **NIO.2 SPI** | Custom `FileSystemProvider` implementations (`xzip://`, `nfs://`). |
| **Swing GUI** | Tree browser, file previewer/editor, toolbar, menus. |
| **Strategy-based formats** | Pluggable `ArchiveFormat` strategy (ZIP, TAR — extensible). |
| **Read/Write** | Create, rename, delete, copy, and save files in both backends. |

---

## Requirements

- **Java 21+** (tested with OpenJDK 25)
- [just](https://github.com/casey/just) command runner (optional — you can
  invoke `javac`/`jar` directly)
- `jtar-2.3.jar` (fetched automatically by `just deps`)

---

## Quick Start

```bash
# 1 — Clone and build
git clone <repo-url> && cd weefs
just build          # downloads jtar dependency, compiles, builds artifact.jar

# 2 — Run the GUI
just run-gui        # or: java -jar bin/artifact.jar gui

# 3 — Run integration tests
just run            # archive integration tests
java -jar bin/artifact.jar nfs-integration   # NFS tests
just test-ui        # UI integration tests
```

---

## Project Structure

```
weefs/
├── src/io/wfs/
│   ├── core/
│   │   ├── extractor/   # Archive FileSystem SPI (ZIP, TAR)
│   │   │   ├── ArchiveFormat.java          # Strategy interface
│   │   │   ├── ZipArchiveFormat.java       # ZIP strategy
│   │   │   ├── TarArchiveFormat.java       # TAR strategy
│   │   │   ├── ArchiveFormats.java         # Registry / resolver
│   │   │   ├── ExtZipFsProvider.java       # NIO.2 FileSystemProvider
│   │   │   ├── ExtZipFileSystem.java       # FileSystem implementation
│   │   │   ├── ExtZipPath.java             # Path implementation
│   │   │   ├── ExtZipFsIO.java             # I/O utilities
│   │   │   └── ExtZipDirectoryStream.java  # Directory iteration
│   │   └── nfs/         # NFS FileSystem SPI
│   │       ├── NfsFsProvider.java          # NIO.2 FileSystemProvider
│   │       ├── NfsFileSystem.java          # FileSystem implementation
│   │       ├── NfsPath.java                # Path implementation
│   │       ├── NfsIO.java                  # I/O utilities (with path-traversal guards)
│   │       ├── NfsConnectionConfig.java    # Connection parameters (validated)
│   │       └── NfsFileInfo.java            # File metadata DTO
│   ├── main/            # CLI entry points and integration tests
│   │   ├── App.java
│   │   ├── ArchiveIntegrationTest.java
│   │   └── NfsIntegrationTest.java
│   └── ui/              # Swing desktop application
│       ├── WeeFsApp.java / MainLauncher.java
│       ├── controller/
│       │   ├── IArchiveController.java     # Archive ops interface
│       │   ├── INfsController.java         # NFS ops interface
│       │   ├── IFileOperations.java        # Unified file ops (Strategy)
│       │   ├── ArchiveController.java      # Main controller
│       │   ├── FileOperations.java         # Archive file mutations
│       │   └── NfsFileOperations.java      # NFS file mutations
│       ├── model/
│       │   ├── ArchiveModel.java           # Central state + Observer
│       │   └── FileNode.java               # Immutable file record
│       ├── view/
│       │   ├── MainFrame.java              # Application window
│       │   ├── ArchiveTreePanel.java       # Tree browser
│       │   ├── FileContentPanel.java       # File viewer/editor
│       │   ├── MenuBarFactory.java         # Menu bar
│       │   ├── ToolBarFactory.java         # Toolbar
│       │   ├── StatusBarPanel.java         # Status bar
│       │   └── FileTreeCellRenderer.java   # Tree icons
│       └── util/
│           ├── FileTypeDetector.java       # MIME detection
│           ├── IconFactory.java            # Icon provider
│           └── SwingUtils.java             # UI helpers
├── lib/                 # External JARs (jtar-2.3.jar)
├── bin/                 # Compiled output
├── justfile             # Build recipes
├── DESIGN.md            # Architecture & antipattern analysis
├── HOW_IT_WORKS.md      # Detailed operation flows
├── NFS_IMPLEMENTATION_DETAILS.md
├── NFS_VERIFICATION_REPORT.md
├── UI_GUIDE.md / UI_QUICK_START.md
└── USAGE_DEMO_GUIDE.md
```

---

## Architecture

WeEFS is organised in three layers:

```
 ┌──────────────────────────────────┐
 │          VIEW (Swing UI)         │  MainFrame, Tree, ContentPanel
 └────────────────┬─────────────────┘
                  │  PropertyChangeEvents (Observer)
 ┌────────────────▼─────────────────┐
 │     MODEL  (ArchiveModel)        │  State, selection, config
 └────────────────┬─────────────────┘
                  │  IFileOperations (Strategy)
 ┌────────────────▼─────────────────┐
 │   CONTROLLER + CORE LAYER        │  ArchiveController ─► FileSystemProvider
 └──────────────────────────────────┘
```

- **Model** — `ArchiveModel` holds the current archive/NFS state and fires
  `PropertyChangeEvent`s. Views listen and refresh.
- **Controller** — `ArchiveController` coordinates user actions, delegating
  file I/O to `IFileOperations` (polymorphic: `FileOperations` for archives,
  `NfsFileOperations` for NFS).
- **Core** — Custom `FileSystemProvider` implementations expose archives and
  NFS exports via the standard `java.nio.file` API.

### Key Design Patterns

| Pattern | Where |
|---|---|
| **Strategy** | `ArchiveFormat` (ZIP/TAR), `IFileOperations` (archive/NFS) |
| **Observer** | `PropertyChangeSupport` in `ArchiveModel` |
| **Factory** | `MenuBarFactory`, `ToolBarFactory`, `IconFactory` |
| **Adapter** | `ExtZipFileSystem` / `NfsFileSystem` adapt archives and NFS to NIO.2 |
| **Command** | Action lambdas wired up in menus and toolbar |

---

## Building

```bash
just build          # full build (deps → compile → jar)
just clean          # remove bin/
just deps           # download jtar dependency only
```

Manual compilation:

```bash
javac -cp "lib/*" -d bin $(find src -type f -name "*.java")
jar cvfm bin/artifact.jar MANIFEST.MF -C bin .
```

---

## Static Analysis

Strict PMD and Checkstyle configurations are included:

```bash
# PMD (download pmd-bin-7.x and add to PATH first)
pmd check -d src -R pmd-ruleset.xml -f text

# Checkstyle (download checkstyle-10.x-all.jar first)
java -jar checkstyle-10.*-all.jar -c checkstyle.xml src/
```

---

## Testing

```bash
just run              # archive integration tests
just test-ui          # UI integration tests
java -jar bin/artifact.jar nfs-integration    # NFS tests
java -jar bin/artifact.jar all-integration    # all tests
```

---

## Documentation

| Document | Purpose |
|---|---|
| [DESIGN.md](DESIGN.md) | Architecture, antipattern analysis, improvement roadmap |
| [HOW_IT_WORKS.md](HOW_IT_WORKS.md) | Detailed operation flows and class roles |
| [NFS_IMPLEMENTATION_DETAILS.md](NFS_IMPLEMENTATION_DETAILS.md) | NFS provider internals |
| [NFS_VERIFICATION_REPORT.md](NFS_VERIFICATION_REPORT.md) | NFS verification results |
| [UI_GUIDE.md](UI_GUIDE.md) | Full UI documentation |
| [UI_QUICK_START.md](UI_QUICK_START.md) | Quick-start for the GUI |
| [USAGE_DEMO_GUIDE.md](USAGE_DEMO_GUIDE.md) | Usage demo walkthrough |

---

## License

See [LICENSE](LICENSE).
