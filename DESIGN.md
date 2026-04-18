# weefs — Design Document

## 1. Project Overview

**weefs** (Web/Extended File System) is a Java library and Swing GUI application providing
Java NIO.2 FileSystem implementations for accessing archive files (ZIP/TAR) and NFS
network shares. The application enables browsing, viewing, creating, and editing files
inside archives through both CLI and GUI interfaces.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Entry Points                            │
│  App.java (CLI)    MainLauncher.java    WeeFsApp.java (GUI)    │
└──────────┬──────────────────┬──────────────────┬────────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Controller Layer                            │
│  IArchiveController ─► ArchiveController ◄─ INfsController     │
│  FileOperations          NfsFileOperations                     │
│  NfsConnectionDialog                                           │
└──────────┬──────────────────┬──────────────────────────────────┘
           │                  │
           ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Model Layer                               │
│  ArchiveModel (PropertyChangeSupport)                          │
│  FileNode        NfsFileInfo                                   │
└──────────┬──────────────────┬──────────────────────────────────┘
           │                  │
           ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Core FileSystem Layer                       │
│  ┌── extractor (xzip) ──┐  ┌──── nfs ────────┐                │
│  │ ExtZipFsProvider      │  │ NfsFsProvider    │                │
│  │ ExtZipFileSystem      │  │ NfsFileSystem    │                │
│  │ ExtZipPath            │  │ NfsPath          │                │
│  │ ExtZipFsIO            │  │ NfsIO            │                │
│  │ ExtZipParsedUri       │  │ NfsConnectionCfg │                │
│  │ ExtZipDirectoryStream │  │ NfsFileInfo      │                │
│  │ ExtZipDirectoryIter   │  └─────────────────┘                │
│  │ ExtZipPathIterator    │                                     │
│  └───────────────────────┘                                     │
└─────────────────────────────────────────────────────────────────┘
           │                  │
           ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       View Layer                                │
│  MainFrame (Mediator)                                          │
│  ├── MenuBarFactory       (Factory)                            │
│  ├── ToolBarFactory       (Factory)                            │
│  ├── ArchiveTreePanel     (Lazy tree, Observer)                │
│  ├── FileContentPanel     (CardLayout Strategy)                │
│  ├── StatusBarPanel       (Observer)                           │
│  ├── FileTreeCellRenderer (Strategy)                           │
│  └── dialog/                                                   │
│      ├── AboutDialog                                           │
│      └── PropertiesDialog                                      │
│                                                                │
│  util/                                                         │
│  ├── FileTypeDetector     (Strategy)                           │
│  ├── IconFactory          (Factory)                            │
│  └── SwingUtils           (Utility)                            │
└─────────────────────────────────────────────────────────────────┘
```

### Design Patterns Used
- **Adapter**: ExtZipFileSystem/NfsFileSystem adapt archives/NFS to NIO.2 FileSystem
- **Observer**: PropertyChangeSupport in ArchiveModel for MVC event propagation
- **Factory**: MenuBarFactory, ToolBarFactory, IconFactory for UI component creation
- **Strategy**: FileTypeDetector + CardLayout for content rendering
- **Command**: FileOperations/NfsFileOperations for atomic operations
- **Mediator**: MainFrame wires all components together
- **Singleton**: WeeFsApp for application lifecycle

---

## 2. Module Descriptions

### 2.1 `io.wfs.core.extractor` — Archive FileSystem (xzip scheme)
Custom NIO.2 FileSystem provider that mounts ZIP and TAR archives as virtual
file systems. Extracts archives to temp directories and writes back on close.

### 2.2 `io.wfs.core.nfs` — NFS FileSystem
NFS FileSystem provider using local filesystem simulation (production stub).
Provides mount/unmount, CRUD operations on remote files.

### 2.3 `io.wfs.ui.controller` — MVC Controllers
ArchiveController implements both IArchiveController and INfsController.
FileOperations and NfsFileOperations handle low-level file mutations.

### 2.4 `io.wfs.ui.model` — MVC Model
ArchiveModel is the central state holder with PropertyChangeSupport.
FileNode is the immutable value object for JTree user objects.

### 2.5 `io.wfs.ui.view` — MVC Views
Swing panels, factories, and dialogs that render model state and forward
user actions to controllers.

### 2.6 `io.wfs.ui.util` — UI Utilities
File type detection, icon generation, and Swing helpers.

### 2.7 `io.wfs.main` — Entry Points & Integration Tests
Application entry point and CLI-driven integration test runners.

---

## 3. Antipatterns & Code Smells Identified

### 3.1 CRITICAL — Violations of Open/Closed Principle (OCP)

| Location | Issue |
|----------|-------|
| `ArchiveController` | Implements BOTH `IArchiveController` AND `INfsController` – God Object. Every new protocol requires modifying this single class. |
| `ArchiveModel.listChildren()` | Hard-coded `if (isNfsMounted())` conditional branching – adding a new FS type means editing the model. |
| `ArchiveModel.readFileContent/readFileBytes()` | Same NFS vs. archive branching duplicated. |
| `ArchiveController.newFile/newDirectory/deleteSelected/renameSelected/extractSelected/saveFileContent` | Each method has `if (isNfsMounted())` branching – six occurrences of the same conditional. |
| `ExtZipFsIO.extractArchiveToDirectory/writeDirectoryToArchive` | `if (isTarArchive())` branching – new archive formats require editing this class. |

### 3.2 HIGH — Tight Coupling

| Location | Issue |
|----------|-------|
| `ToolBarFactory.create()` | Downcasts `IArchiveController` to `INfsController` – breaks ISP and LSP. |
| `MenuBarFactory.createNfsMenu()` | Same unsafe downcast. |
| `MainFrame` constructor | Takes `IArchiveController` but imports concrete `ArchiveController`. |
| `ArchiveModel.provider` field | Hard-codes `new ExtZipFsProvider()` – cannot swap providers. |
| `NfsFileOperations/FileOperations` | Both depend on `ArchiveModel` directly for `isOpen()/isReadOnly()` checks – scattered guard logic. |
| `FileContentPanel` | Directly reads file content via model – should go through controller. |

### 3.3 HIGH — Security Issues

| Location | Issue |
|----------|-------|
| `NfsIO.getCachePath()` | Path traversal risk: host/export strings used directly in path construction without sanitization. A malicious hostname like `../../../etc` could escape the cache directory. |
| `NfsIO.normalizePath()` | Does not validate against `..` traversal – `"/../../../etc/passwd"` strips the leading `/` but preserves `..`. |
| `NfsFsProvider.checkAccess()` | **Always grants access** – this is a no-op, any path check succeeds regardless of permissions. |
| `NfsConnectionDialog` | User-supplied host/port not validated against SSRF patterns. |
| `ExtZipFsProvider.resolveArchivePath()` | Accepts raw `file:` URIs and arbitrary paths without sandbox validation. |
| `NfsConnectionConfig` | No hostname validation (allows empty-after-trim, special chars). |
| `FileOperations.showError()` | Passes `null` as parent to `JOptionPane` – minor but inconsistent. |

### 3.4 MEDIUM — DRY Violations & Code Duplication

| Location | Issue |
|----------|-------|
| `FileOperations` / `NfsFileOperations` | Near-identical class structure – same methods, same guards, same error handling. Should share an interface or abstract base. |
| `ArchiveController` helper methods | `getTargetDirectory()` and `getTargetNfsDirectory()` are near-duplicates. |
| `ArchiveController.joinNfsPath()` / `ArchiveModel.joinRemotePath()` | Identical path-joining logic duplicated across classes. |
| `ArchiveIntegrationTest.cleanup()` / `NfsIntegrationTest.cleanup()` / `UiIntegrationTest.deleteRecursive()` / `ExtZipFsIO.deleteRecursively()` / `NfsIO.deleteRecursively()` | Five copies of recursive delete. |
| `ExtZipFsIO.writeDirectoryToZip/writeDirectoryToTar` | Near-identical write loop with different entry types. |

### 3.5 MEDIUM — Design Smells

| Location | Issue |
|----------|-------|
| `WeeFsApp` | Singleton pattern with lazy init – not thread-safe despite `synchronized`. The `mainFrame` field is set on EDT but read from any thread without sync. |
| `ArchiveModel.setNfsConfig()` | Fires property changes NOT on EDT (unlike other methods that use `fireOnEdt`). |
| `ArchiveController.executeInBackground()` | Generic `Runnable` with no error segregation – exceptions in `doInBackground` are swallowed unless explicitly caught inside the task. |
| `App.main()` | Uses old-style `switch` with `break` – should use enhanced switch. |
| `NfsPath.equals()` | Compares `fileSystem` by `Objects.equals` which calls `NfsFileSystem.equals()` – but NfsFileSystem has no custom `equals`, so this falls through to identity comparison inconsistently with the `Objects.equals` wrapper. |
| `ExtZipPath.hashCode()` | Uses `System.identityHashCode(fileSystem)` – correct but unusual. |
| `ByteArraySeekableByteChannel` | Inner class in provider – should be extracted for testability. |

### 3.6 LOW — Naming & Convention Issues

| Location | Issue |
|----------|-------|
| `NfsFsProvider.mounted` | Field shadows conceptual meaning – should clarify it maps config keys to file systems. |
| `ExtZipParsedUri` constructor | Package-private record-like class could be a Java `record`. |
| `FileNode` constructor | Reads file attributes in constructor (I/O in constructor) – violation of separation of concerns. |
| `ArchiveTreePanel` | 300+ line class doing lazy loading, context menus, selection handling – should be decomposed. |

### 3.7 LOW — Missing Functionality

| Location | Issue |
|----------|-------|
| `NfsFsProvider.readAttributes()` | Throws `UnsupportedOperationException` – incomplete implementation. |
| `NfsPath.relativize()` | Throws `UnsupportedOperationException`. |
| `NfsPath.toUri()` | Throws `UnsupportedOperationException`. |
| `NfsPath.toFile()` | Throws `UnsupportedOperationException`. |
| No gzip/bz2 support | Despite `gzip` branch existing. |

---

## 4. Bugs Found

| # | Location | Bug |
|---|----------|-----|
| 1 | `ArchiveModel.setNfsConfig()` | Fires property changes off-EDT – can cause Swing threading violations and UI corruption. |
| 2 | `ArchiveController.saveArchive()` | Captures `wasReadOnly = model.isReadOnly()` then passes it to `openArchive()` inside background thread – race condition if model state changes. |
| 3 | `NfsIO.getCachePath()` | Cache path computed from user-supplied host/export – path traversal vulnerability. |
| 4 | `NfsIO.normalizePath()` | Does not prevent `..` traversal in path components. |
| 5 | `NfsFsProvider.checkAccess()` | No-op – always grants access, any path is "accessible". |
| 6 | `MenuBarFactory.exit` action | Calls `System.exit(0)` without checking unsaved changes (unlike `MainFrame.handleExit()`). |
| 7 | `ArchiveController.executeInBackground()` | SwingWorker's `done()` doesn't check `get()` for exceptions – background failures are silently lost. |
| 8 | `NfsFileOperations.createFile/createDirectory/etc` | Check `model.isOpen()` but NFS operations don't require an archive to be "open" in the traditional sense – the guard is incorrectly shared. |
| 9 | `FileContentPanel.LineNumberView` | Line number calculation assumes fixed line height without accounting for font scaling or DPI. |
| 10 | `NfsFsProvider.newByteChannel()` | Returns read-only channel even when write options are specified – silently ignores write semantics. |

---

## 5. Improvement Plan (Status)

1. ✅ **Extract common FileSystem operations interface** — `IFileOperations` unifies archive and NFS file ops
2. ✅ **Create `ArchiveFormat` strategy** — `ArchiveFormat` interface + `ZipArchiveFormat`, `TarArchiveFormat`, `ArchiveFormats` registry
3. ✅ **Introduce `IFileOperations` interface** — `FileOperations` and `NfsFileOperations` both implement it; controller uses polymorphic `getFileOps()`
4. ✅ **Fix all security vulnerabilities** — NfsIO path traversal, NfsFsProvider.checkAccess, NfsConnectionConfig hostname/path validation
5. ✅ **Fix EDT threading bugs** — `ArchiveModel.setNfsConfig()` now uses `fireOnEdt()`
6. ✅ **Fix MenuBarFactory exit handler** — Now checks unsaved changes before exit
7. ✅ **Fix executeInBackground exception swallow** — `done()` now calls `get()` to surface errors
8. ✅ **Simplify ArchiveController NFS branching** — Eliminated 6 `if (isNfsMounted())` branches using polymorphic `IFileOperations`
9. ✅ **Fix NfsFileOperations guards** — Changed incorrect `model.isOpen()` to `config.isReadOnly()` checks
10. ⬜ **Add comprehensive unit tests** for all core modules
11. ⬜ **Extract common utilities** (recursive delete, path joining)
12. ⬜ **Reduce ArchiveController size further** by extracting NFS concerns into separate mediator

---

## 6. Static Analysis Configuration

### PMD Configuration (pmd-ruleset.xml)
Configured with all major rulesets at strictest level:
- Best Practices, Code Style, Design, Error Prone, Multithreading, Performance, Security

### Checkstyle Configuration (checkstyle.xml)
Based on Google Java Style with strict enforcement:
- Line length: 120, method length: 60, cyclomatic complexity: 10
- Mandatory Javadoc for all public types and methods
- Import ordering, naming conventions, whitespace rules

---

*Document generated: 2026-04-16*
*Branch: bleeding*
