# WeEFS Project - How It Works

## Quick Start

### 1. Run Integration Tests (Archive Functionality)
```bash
cd /Users/prajwalnaganagoudar/Desktop/weefs
java -jar bin/artifact.jar integration
```
**Output:**
```
[PASS] zip   ← ZIP archives work
[PASS] tar   ← TAR archives work
All integration checks passed.
```

### 2. Run GUI Application
```bash
java -jar bin/artifact.jar gui
```
This opens the WeEFS desktop application with:
- File browser for archives
- Menu bar (File, NFS, Edit, View, Help)
- Toolbar with quick action buttons
- Tree view showing archive/NFS contents
- File preview panel
- Status bar

---

## Architecture Overview

### MVC Pattern Application
```
┌─────────────────────────────────────────────┐
│           VIEW LAYER (Swing UI)             │
├─────────────────────────────────────────────┤
│  MainFrame -> MenuBar, ToolBar, Tree, Panel │
└────────────────────────┬────────────────────┘
                         │ (Observable)
┌────────────────────────▼────────────────────┐
│         MODEL LAYER (State Manager)         │
├─────────────────────────────────────────────┤
│  ArchiveModel: Manages archive/NFS state    │
│  - currentArchive/nfsConfig                 │
│  - selectedFile                             │
│  - PropertyChangeSupport (Observer pattern) │
└────────────────────────┬────────────────────┘
                         │ (Commands)
┌────────────────────────▼────────────────────┐
│       CONTROLLER LAYER (Orchestration)      │
├─────────────────────────────────────────────┤
│  ArchiveController implements:              │
│  - IArchiveController (archive ops)         │
│  - INfsController (NFS ops)                 │
│           ↓                                  │
│  FileOperations (Archive)                   │
│  NfsFileOperations (NFS)                    │
└────────────────────────┬────────────────────┘
                         │
┌────────────────────────▼────────────────────┐
│          CORE LAYER (File Systems)          │
├─────────────────────────────────────────────┤
│  Archives:                                  │
│  ExtZipFsProvider → ExtZipFileSystem        │
│                                             │
│  NFS:                                       │
│  NfsFsProvider → NfsFileSystem              │
│       ↓                                      │
│  Both use Java NIO.2 FileSystem SPI         │
└─────────────────────────────────────────────┘
```

---

## How Archive Operations Work

### Opening an Archive
```
User clicks "File" → "Open Archive..."
         ↓
ArchiveController.openArchive()
         ↓
Show JFileChooser dialog
         ↓
User selects ZIP/TAR file
         ↓
model.openArchive(path, readOnly)
         ↓
ExtZipFsProvider mounts archive
         ↓
Model fires PropertyChange event
         ↓
Views refresh to show archive contents
```

### Example: Opening `documents.zip`
```
┌─ documents.zip
│  ├─ reports/
│  │  ├─ q1-summary.pdf
│  │  └─ q2-summary.pdf
│  ├─ notes.txt
│  └─ images/
│     ├─ chart1.png
│     └─ chart2.png
```

### Extracting a File
```
User selects "reports/q1-summary.pdf" in tree
         ↓
Clicks "Extract To..." (toolbar or menu)
         ↓
FileOperations.extractTo(archivePath, destination)
         ↓
Reads file bytes from archive FileSystem
         ↓
Writes to local disk destination
         ↓
File saved: ~/Downloads/q1-summary.pdf
```

---

## How NFS Operations Work

### Mounting an NFS Share
```
User clicks "NFS" → "Mount NFS..."
         ↓
NfsConnectionDialog appears with fields:
├─ Host: 192.168.1.100
├─ Port: 2049
├─ Export Path: /exports/documents
├─ Mount Path: /exports/documents
├─ Timeout: 30 seconds
└─ [✓] Read-Only
         ↓
User clicks "Mount"
         ↓
ArchiveController.mountNfs()
         ↓
NfsIO.verifyConnection(config)
         ↓
NfsFsProvider.newFileSystem(nfs://host:port/path)
         ↓
NfsFileSystem created and mounted
         ↓
Model fires PROP_NFS_CONFIG event
         ↓
UI shows NFS contents in tree view
```

### NFS File Browsing
```
├─ /exports/documents (mounted)
│  ├─ annual_reports/
│  │  ├─ 2024-report.pdf
│  │  └─ 2023-report.pdf
│  ├─ current_files/
│  │  ├─ draft.docx
│  │  └─ notes.md
│  └─ shared_data.csv
```

### NFS File Operations
```
Creating a file:
  User: Right-click → "New File"
  NfsFileOperations.createFile(config, "/path/file.txt", content)
       ↓
  NfsIO.writeFile(config, path, bytes)
       ↓
  File written to NFS

Deleting a file:
  User: Select file → "Delete"
  NfsFileOperations.delete(config, path)
       ↓
  NfsIO.delete(config, path)
       ↓
  File removed from NFS

Extracting from NFS:
  User: Select file → "Extract NFS File"
  NfsFileOperations.extractTo(config, nfsPath, localPath)
       ↓
  NfsIO.readFile(config, nfsPath) → byte[]
       ↓
  Files.write(localPath, bytes)
       ↓
  File saved locally
```

---

## Key Classes & Their Roles

### Archive Core (src/io/wfs/core/extractor/)
```
ExtZipFsProvider         - Creates archive FileSystem
ExtZipFileSystem         - Implements FileSystem for archives
ExtZipPath               - Implements Path for archive files
ExtZipFsIO               - I/O utilities (extract, write, compress)
ExtZipDirectoryStream    - Navigates archive directories
```

### NFS Core (src/io/wfs/core/nfs/)
```
NfsFsProvider            - Creates NFS FileSystem
NfsFileSystem            - Implements FileSystem for NFS
NfsPath                  - Implements Path for NFS files
NfsIO                    - I/O utilities (read, write, delete, etc.)
NfsConnectionConfig      - NFS connection parameters
NfsFileInfo              - NFS file metadata
```

### UI Controller (src/io/wfs/ui/controller/)
```
ArchiveController        - Main orchestrator (IArchiveController + INfsController)
IArchiveController       - Interface for archive operations
INfsController           - Interface for NFS operations
FileOperations           - Archive file mutations (create, delete, rename)
NfsFileOperations        - NFS file mutations
NfsConnectionDialog      - Modal dialog for NFS connection
```

### UI View (src/io/wfs/ui/view/)
```
MainFrame                - Main application window
MenuBarFactory           - File, NFS, Edit, View, Help menus
ToolBarFactory           - Quick action buttons
ArchiveTreePanel         - Tree view of files/folders
FileContentPanel         - File content viewer/editor
FileTreeCellRenderer     - Custom tree node rendering
StatusBarPanel           - File info display
```

### UI Model (src/io/wfs/ui/model/)
```
ArchiveModel             - Central state holder
                           - Manages archive + NFS mount state
                           - Fires PropertyChangeEvents
FileNode                 - Immutable file representation
```

---

## File Operation Flow Examples

### Scenario 1: Create a File in Archive

```
┌─ User Action ─────────────────────────────┐
│ Right-click in tree → "New File..."       │
└─────────────────────┬─────────────────────┘
                      │
        ┌─────────────▼─────────────┐
        │ ArchiveController         │
        │ .newFile()                │
        │ - Get selected directory  │
        │ - Show name input dialog  │
        └─────────────┬─────────────┘
                      │
        ┌─────────────▼─────────────┐
        │ FileOperations            │
        │ .createFile(path, "")     │
        │ - Check writable          │
        │ - Write to archive FS     │
        │ - Fire tree refresh event │
        └─────────────┬─────────────┘
                      │
        ┌─────────────▼─────────────┐
        │ ArchiveModel              │
        │ .fireTreeRefresh()        │
        │ - Property change event   │
        └─────────────┬─────────────┘
                      │
        ┌─────────────▼─────────────┐
        │ Views                     │
        │ - Tree updates            │
        │ - Shows new file          │
        │ - User can edit it        │
        └───────────────────────────┘
```

### Scenario 2: Read File Content

```
┌─ User Action ───────────────────────────┐
│ Double-click file in tree               │
└────────────────────┬────────────────────┘
                     │
        ┌────────────▼────────────┐
        │ MainFrame               │
        │ Tree selection listener │
        │ .onFileSelected()       │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ FileContentPanel        │
        │ .loadFile(path)         │
        │ - Detect file type      │
        │ - Read content          │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ ArchiveModel            │
        │ .readFileContent(path)  │
        │ or                      │
        │ Files.readString(path)  │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ File System             │
        │ (Archive or NFS)        │
        │ Returns content...      │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ UI Display              │
        │ - Shows text in panel   │
        │ - Enable edit/save      │
        │ - User can modify       │
        └────────────────────────┘
```

### Scenario 3: Switch Between Archive and NFS

```
┌─ User has ZIP open ──────────────────┐
│ File menu: "documents.zip" open      │
└────────────┬────────────────────────┘
             │
   ┌─────────▼──────────┐
   │ User: NFS → Mount  │
   │ NFS...             │
   └─────────┬──────────┘
             │
   ┌─────────▼─────────────────────────┐
   │ NfsConnectionDialog appears       │
   │ User enters NFS details           │
   │ Clicks "Mount"                    │
   └─────────┬───────────────────────────┘
             │
   ┌─────────▼──────────────────────────┐
   │ ArchiveModel.setNfsConfig(config)  │
   │ - Closes current archive           │
   │ - Sets NFS config                  │
   │ - Fires PROP_NFS_CONFIG event      │
   │ - Fires PROP_OPEN event            │
   └─────────┬────────────────────────────┘
             │
   ┌─────────▼──────────────────────────┐
   │ Views React:                       │
   │ - MenuBar updates (Close → Unmount)│
   │ - ToolBar updates buttons          │
   │ - Tree shows NFS structure         │
   │ - Status shows: "NFS mounted"      │
   └──────────────────────────────────────┘
```

---

## UI State Transitions

```
┌─────────────────┐
│    START        │
│ (No archive,    │
│  No NFS)        │
└────────┬────────┘
         │
         │ User: File → Open Archive
         ▼
    ┌──────────────────┐
    │ ARCHIVE OPEN     │
    │ (Read/Write mode)│
    └────────┬─────────┘
             │
   ┌─────────┴──────────┐
   │                    │
   │  New File, Delete, │ ◄─── NFS → Mount NFS
   │  Rename, Extract   │      │
   │                    │      │ Closes archive
   │                    │      │
   └─────────┬──────────┘      │
             │                 │
             └──────────────────┼──────┐
                                │      │
                                ▼      │
                        ┌──────────────────┐
                        │  NFS MOUNTED     │
                        │  (Read/Write)    │
                        └──────────────────┘
                                │
                                │ NFS → Unmount NFS
                                │
                                ▼
                        ┌──────────────────┐
                        │  START           │
                        │  (Ready for      │
                        │   new archive)   │
                        └──────────────────┘
```

---

## Design Pattern Examples in Action

### 1. Observer Pattern (PropertyChangeSupport)
```java
// Model fires event
model.addPropertyChangeListener((evt) -> {
    if (evt.getPropertyName().equals(PROP_TREE_REFRESH)) {
        treePanel.refresh();      // View1 reacts
        statusBar.update();        // View2 reacts
    }
});
```

### 2. Factory Pattern (MenuBarFactory)
```java
JMenuBar menuBar = MenuBarFactory.create(controller, model);
// Creates entire menu structure with:
// - NFS Menu
// - File Menu  
// - Edit Menu
// All properly configured
```

### 3. Command Pattern (NfsFileOperations)
```java
// Each operation is encapsulated as a command
nfsFileOps.createFile(config, path, content);
nfsFileOps.delete(config, path);
nfsFileOps.rename(config, oldPath, newPath);
// Each is independent, atomic operation
```

### 4. Adapter Pattern (NfsFileSystem)
```java
// NFS is adapted to standard FileSystem interface
FileSystem nfs = nfsProvider.newFileSystem(uri, env);
// Now can use standard APIs:
DirectoryStream<Path> = Files.newDirectoryStream(path);
byte[] content = Files.readAllBytes(path);
```

---

## How to Test Features

### Test Archive Operations
```bash
# 1. Create a test ZIP
zip test.zip file1.txt file2.txt

# 2. Run GUI
java -jar bin/artifact.jar gui

# 3. File → Open Archive
# Select test.zip

# 4. Try these operations:
# - View file contents
# - Create new file (right-click)
# - Delete file
# - Rename file
# - Extract file
# - Edit text file
```

### Test NFS Operations
```bash
# 1. Run GUI
java -jar bin/artifact.jar gui

# 2. NFS → Mount NFS
# Enter:
#   Host: localhost (or your NFS server)
#   Port: 2049
#   Export Path: /nfs/share
#   Read-Only: [unchecked]

# 3. Try these operations:
# - Browse NFS files
# - Create new file
# - Delete file on NFS
# - Extract NFS file locally
# - Rename NFS file
```

---

## Key Takeaways

✅ **Unified Interface**
- Archives and NFS both use Java NIO.2 FileSystem SPI
- Same operations work on both

✅ **MVC Architecture**
- Model manages state (which archive/NFS is open)
- Views react to property changes
- Controller orchestrates user actions

✅ **Design Patterns**
- Factory: Menu/Toolbar/Dialog creation
- Adapter: NFS/Archive as FileSystem
- Command: File operations
- Observer: Model → Views

✅ **Extensible**
- Can add SMB, SFTP, or other file systems
- Same UI layer can support any FileSystem provider

✅ **Clean Code**
- Single responsibility per class
- Interface contracts honored
- Comprehensive error handling
