# WeEFS: NFS Implementation - Complete Guide

## What is WeEFS?

**WeEFS** (Web/Wide File System) is a Java desktop application that provides a unified file browser for accessing both **archive file systems (ZIP/TAR)** and **network file systems (NFS)** through a single, familiar GUI. It leverages Java's NIO.2 FileSystem SPI to mount archives and NFS shares as if they were local folders, enabling users to browse, create, edit, and extract files through an MVC-based Swing interface.

The project implements a **production-ready abstraction layer** that treats diverse storage backends (archives, NFS) uniformly using standard Java I/O APIs, making it extensible to other file system types in the future.

---

## Major Use Cases

### 1. **Archive Browsing & Extraction** (Production-Ready)
- Browse ZIP/TAR archives like standard directories
- Extract files to local disk
- Create new archives
- Add/edit/delete files within archives (Read/Write mode)
- **Why?** Users don't need specialized archive tools; just open the file in WeEFS

### 2. **NFS File Management** (Simulated, Production-Extensible)
- Mount remote NFS shares with host, port, and export path
- Browse directory structure remotely
- Create files and directories on NFS
- Rename, copy, and delete remote files
- Extract NFS files to local system
- Read-only mounting for safety
- **Why?** System administrators need lightweight, GUI-based NFS browsing without mounting at OS level

### 3. **Unified Multi-Backend Navigation**
- Switch between archive and NFS browsing in same UI
- Same operations work consistently across backends
- **Why?** Users don't need mentally switching tools per storage type

### 4. **Integration Testing Infrastructure**
- Automated tests for archive operations (ZIP/TAR round-trip)
- Automated tests for NFS operations (list, read, write, delete, rename, copy, mkdir)
- Command-line test runners for CI/CD pipelines
- **Why?** Ensures reliability as features are added

---

## Architecture & Design Patterns

### **1. MVC (Model-View-Controller) Pattern**

**Structure:**
```
┌─────────────────────────────────────────────┐
│    VIEWS (Swing Components)                 │
│  MainFrame, ArchiveTreePanel, MenuBar, etc. │
└────────────────┬────────────────────────────┘
                 │ (Observable PropertyChangeListener)
┌────────────────▼────────────────────────────┐
│    MODEL (State Management)                 │
│  ArchiveModel - Manages archive/NFS state   │
│  FileNode - File metadata                   │
└────────────────┬────────────────────────────┘
                 │ (Commands/Actions)
┌────────────────▼────────────────────────────┐
│    CONTROLLERS (Orchestration)              │
│  ArchiveController implements:              │
│  - IArchiveController (archive ops)         │
│  - INfsController (NFS ops)                 │
└────────────────┬────────────────────────────┘
                 │ (Delegated I/O)
┌────────────────▼────────────────────────────┐
│    CORE LAYER (File Systems)                │
│  FileOperations (archive I/O)               │
│  NfsFileOperations (NFS I/O)                │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│    FILE SYSTEM PROVIDERS (Java SPI)         │
│  ExtZipFileSystem + NfsFileSystem           │
│  (both implement FileSystem interface)      │
└─────────────────────────────────────────────┘
```

**Justification:**
- **Separation of Concerns:** Model handles state, controller handles actions, views only render
- **Testability:** Model can be tested independently of GUI
- **Reusability:** Controller logic works with any UI framework
- **Maintainability:** Changes to one layer don't ripple to others

---

### **2. Provider Pattern (FileSystem SPI)**

**Implementation:**
```java
// Archive provider
class ExtZipFsProvider extends FileSystemProvider {
    FileSystem newFileSystem(URI uri, Map<String, ?> env)
}

// NFS provider
class NfsFsProvider extends FileSystemProvider {
    FileSystem newFileSystem(URI uri, Map<String, ?> env)
}
```

**How It Works:**
1. Both providers register with Java's `ServiceLoader` mechanism
2. Users mount via: `FileSystems.newFileSystem(URI, Map)`
3. Returns a file system that implements standard `FileSystem` interface
4. Client code uses standard `java.nio.file.*` APIs

**Justification:**
- **Standard Interface:** No custom APIs; uses Java stdlib familiarity
- **Plugin Architecture:** New file systems can be added without changing core code
- **Language Integration:** Works with all Java NIO.2 operations (Files class)
- **Future-Proof:** Production NFS libraries (jNFS, NFS4J) can drop-in replace simulation

**Example Usage:**
```java
// Client code is generic - doesn't know it's NFS
URI nfsUri = URI.create("nfs://192.168.1.100:2049/exports/docs?mount=/mnt&readOnly=false");
FileSystem nfs = FileSystems.newFileSystem(nfsUri, Map.of());
Path file = nfs.getPath("/reports/q1.pdf");
byte[] content = Files.readAllBytes(file);  // Works exactly like local files
```

---

### **3. Adapter Pattern (NfsFileSystem)**

**Problem:**
NFS protocol fundamentally works with `String` paths and byte arrays, not Java NIO.2 abstractions.

**Solution:**
```java
class NfsFileSystem extends FileSystem {
    private NfsConnectionConfig config;
    
    // Adapts NIO.2 Path interface to NFS-style string paths
    @Override
    public Path getPath(String first, String... more) {
        return new NfsPath(this, join(first, more));
    }
}

class NfsPath implements Path {
    private final NfsFileSystem fs;
    private final String pathStr;
    
    // Delegates NIO.2 operations to NfsIO static utilities
    public byte[] read() {
        return NfsIO.readFile(fs.getConfig(), pathStr);
    }
}
```

**Justification:**
- **Semantic Gap:** NFS is string/bytes-based; NIO.2 is path-based
- **User Expectation:** Users expect `Files.readAllBytes(nfsPath)` to work
- **No Breaking Changes:** Adapter makes NFS transparent to client code

---

### **4. Pure Fabrication Pattern (NfsIO Utility Class)**

**Structure:**
```java
public final class NfsIO {
    // Static utility methods
    public static void verifyConnection(NfsConnectionConfig config)
    public static List<NfsFileInfo> listDirectory(NfsConnectionConfig config, String path)
    public static byte[] readFile(NfsConnectionConfig config, String path)
    public static void writeFile(NfsConnectionConfig config, String path, byte[] data)
    public static void createDirectory(NfsConnectionConfig config, String path)
    public static void delete(NfsConnectionConfig config, String path)
    public static void rename(NfsConnectionConfig config, String old, String new)
    public static void copy(NfsConnectionConfig config, String src, String dst)
}
```

**Justification (GRASP):**
- **High Cohesion:** All NFS I/O belongs together; no class owns it naturally
- **Low Coupling:** Controllers don't couple to NfsFsProvider internals
- **Testability:** Static utilities are easy to mock in tests
- **Clarity:** Intent is explicit — this class does "pure" I/O translation
- **Simulation → Production:** Current implementation uses local FileSystem; production can call real NFS RPC

---

### **5. Command Pattern (FileOperations Classes)**

**Structure:**
```java
class FileOperations {
    boolean createFile(Path path, String content)
    boolean createDirectory(Path path)
    boolean delete(FileNode node)
    boolean rename(FileNode node, String newName)
    boolean saveFile(...content...)
}

class NfsFileOperations {
    boolean createFile(NfsConnectionConfig config, String path, String content)
    boolean createDirectory(NfsConnectionConfig config, String path)
    boolean delete(NfsConnectionConfig config, String path)
    boolean rename(NfsConnectionConfig config, String oldPath, String newPath)
}
```

**Key Features:**
- **Atomic Operations:** Each method is one logical operation
- **Error Handling:** Returns boolean + shows error dialogs; no exceptions bubble to UI
- **State Validation:** Checks `model.isOpen()` and `model.isReadOnly()` before executing
- **Model Notification:** Calls `model.fireTreeRefresh()` after mutation

**Justification:**
- **Encapsulation:** Each operation is self-contained, not scattered across controller
- **Reusability:** Operations can be called from menu, toolbar, context menu, etc.
- **Error Recovery:** Errors handled gracefully without crashing UI thread
- **Extensibility:** New operations added without changing ArchiveController

---

### **6. Observer Pattern (PropertyChangeSupport)**

**Model Fires Events:**
```java
class ArchiveModel {
    public static final String PROP_ARCHIVE_PATH = "archivePath";
    public static final String PROP_NFS_CONFIG = "nfsConfig";
    public static final String PROP_OPEN = "open";
    public static final String PROP_READ_ONLY = "readOnly";
    public static final String PROP_SELECTED_FILE = "selectedFile";
    
    void openArchive(Path archive, boolean readOnly) {
        // ... mount archive ...
        pcs.firePropertyChange(PROP_ARCHIVE_PATH, oldPath, newPath);
        pcs.firePropertyChange(PROP_OPEN, false, true);
    }
}
```

**Views Listen:**
```java
class MainFrame {
    public MainFrame(ArchiveModel model) {
        model.addPropertyChangeListener(PROP_ARCHIVE_PATH, evt -> {
            refreshTreeView((Path) evt.getNewValue());
        });
        model.addPropertyChangeListener(PROP_SELECTED_FILE, evt -> {
            updateFilePreview((FileNode) evt.getNewValue());
        });
    }
}
```

**Justification:**
- **Loose Coupling:** Model doesn't know about views; views know about model
- **Multiple Observers:** Menu bar, tree view, and status bar all update independently
- **Automatic Sync:** UI automatically reflects model state changes
- **Standard Java:** Uses built-in `PropertyChangeSupport`, not custom event system

---

### **7. Strategy Pattern (Read-Only vs Read/Write)**

**Implementation:**
```java
// Different behavior based on mode
if (model.isReadOnly()) {
    // Disable delete, rename, create buttons
    // All NfsFileOperations.delete() checks: if (model.isReadOnly()) return false
}

// In NfsIO level:
public static void writeFile(NfsConnectionConfig config, String path, byte[] data) {
    if (config.isReadOnly()) {
        throw new IOException("NFS mount is read-only");
    }
    // ... write ...
}
```

**Justification:**
- **Defensive Programming:** Multiple layers check read-only to catch errors early
- **User Safety:** Prevents accidental modification of critical shares
- **Clear Intent:** Read-only status is checked at every mutation point
- **Production Ready:** NFS servers enforce read-only; simulator enforces it here

---

## NFS Implementation: From Scratch to Complete

### **Phase 1: Configuration & Connection Model**

**File:** `NfsConnectionConfig.java`

```java
class NfsConnectionConfig {
    String host             // e.g., "192.168.1.100"
    int port                // e.g., 2049 (NFS standard)
    String exportPath       // e.g., "/exports/documents"
    String mountPath        // e.g., "/exports/documents" (for display)
    int timeoutSeconds      // e.g., 30 (connection timeout)
    boolean readOnly        // Safety mode
}
```

**Design Decisions:**
- **Immutable:** Once created, config cannot change (thread-safe)
- **Validation:** Port 1-65535, timeout 1-3600 seconds
- **Equality Based on Connection:** Two configs with same host/port/export are equivalent
- **Production Extension:** Future: Add authentication (username/password), security options

---

### **Phase 2: File Metadata Representation**

**File:** `NfsFileInfo.java`

```java
class NfsFileInfo {
    String name;            // "document.pdf"
    String fullPath;        // "/exports/documents/doc.pdf"
    boolean directory;      // true/false
    long size;              // bytes
    long lastModified;      // timestamp
}
```

**Why Separate from FileNode?**
- `FileNode` represents archive files (inherits from JTree model)
- `NfsFileInfo` represents NFS metadata (pure data, no UI coupling)
- Allows parallel development of archive and NFS

---

### **Phase 3: Pure I/O Translation Layer**

**File:** `NfsIO.java`

**Design:** Static utility with no state (Pure Fabrication)

**Methods:**

```java
// Connection verification
verifyConnection(config)
// → Ensures mount directory exists
// → Future: RPC test to NFS server

// Directory listing (sorted: folders first, then alphabetically)
List<NfsFileInfo> listDirectory(config, "/path")

// File I/O
byte[] readFile(config, "/path/file.txt")
writeFile(config, "/path/file.txt", bytes)

// Directory operations
createDirectory(config, "/path/newdir")

// File mutations
delete(config, "/path")
rename(config, "/old", "/new")
copy(config, "/src", "/dst")

// Cleanup
disconnect(config)
```

**Simulation Strategy:**
```
Real NFS:
  Host: 192.168.1.100:2049 → /mnt/nfs
  
Simulated (implemented):
  Host: 192.168.1.100:2049 → ~/.tmp/weefs-nfs/192_168_1_100_2049___exports_documents/
```

**Caching Location:**
```
getCachePath(config) = 
  $TMPDIR/weefs-nfs/{host}_{port}_{exportPath_normalized}/
```

**Example:**
```
Host: 192.168.1.100
Port: 2049
Export: /exports/documents

Cache directory:
  /tmp/weefs-nfs/192_168_1_100_2049___exports_documents/

Remote file /reports/q1.pdf maps to:
  /tmp/weefs-nfs/192_168_1_100_2049___exports_documents/reports/q1.pdf
```

---

### **Phase 4: FileSystem Provider Registration**

**File:** `NfsFsProvider.java` (extends `FileSystemProvider`)

**Key Methods:**

```java
@Override
public String getScheme() { return "nfs"; }

@Override
public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
    // Parse URI: nfs://host:port/export/path
    // Check if already mounted (prevent duplicates)
    // Create NfsConnectionConfig from parsed URI
    // Verify connection to NFS
    // Create NfsFileSystem instance
    // Cache it in Map<String, NfsFileSystem>
    // Install JVM shutdown hook for cleanup
    // Return file system
}

@Override
public FileSystem getFileSystem(URI uri) {
    // Lookup existing mount in cache
    // Throw FileSystemNotFoundException if not found
}
```

**URI Format:**
```
nfs://hostname:port/export/path?mount=/mount/path&readOnly=false&timeout=30

Example:
nfs://192.168.1.100:2049/exports/documents?readOnly=false&timeout=30
```

**Mount Cache:**
```java
private final Map<String, NfsFileSystem> mounted = new ConcurrentHashMap<>();

// Key: "192.168.1.100:2049:/exports/documents"
// Value: NfsFileSystem instance

// Purpose: Prevent duplicate mounts of same server/export
```

---

### **Phase 5: FileSystem & Path Implementation**

**File:** `NfsFileSystem.java` (extends `FileSystem`)

```java
class NfsFileSystem extends FileSystem {
    NfsFsProvider provider;
    NfsConnectionConfig config;
    AtomicBoolean open;
    
    @Override
    public NfsPath getPath(String first, String... more)
    
    @Override
    public Path getPath(String pathname)
    
    @Override
    public void close()  // Unmount
    
    @Override
    public boolean isOpen()
    
    @Override
    public boolean isReadOnly()
}
```

**File:** `NfsPath.java` (implements `Path`)

```java
class NfsPath implements Path {
    NfsFileSystem fs;
    String pathStr;
    
    // Delegates all operations to NfsIO through filesystem
    @Override
    public int compareTo(Path other)
    
    @Override
    public Path resolve(String other)
    
    @Override
    public Path resolveSibling(String other)
    
    @Override
    public Path relativize(Path other)
    
    @Override
    public Path normalize()
    
    @Override
    public Path getFileName()
    
    @Override
    public Path getParent()
}
```

---

### **Phase 6: UI Controller Integration**

**File:** `ArchiveController.java` implements both:
- `IArchiveController` (archive operations)
- `INfsController` (NFS operations)

**Key Methods for NFS:**

```java
// Mount NFS share
void mountNfs() {
    // Show NfsConnectionDialog
    // User enters host, port, export path, read-only flag
    // NfsConnectionConfig created
    // NfsFsProvider.newFileSystem() called
    // model.mountNfs(config) updates model
}

// Unmount NFS share
void unmountNfs() {
    // Get current NFS file system
    // Call fileSystem.close()
    // model.unmountNfs() clears model
    // UI updates
}

// File operations
void createFile() {
    // Show input dialog for filename/content
    // nfsFileOps.createFile(config, path, content)
    // Model fires refresh event
}

void deleteSelected() {
    // Get selected file
    // nfsFileOps.delete(config, nfsPath)
    // Model fires refresh event
}

void extractNfsSelected() {
    // Show JFileChooser for destination
    // nfsFileOps.extractTo(config, nfsPath, localPath)
}
```

---

### **Phase 7: Model State Management**

**File:** `ArchiveModel.java`

```java
class ArchiveModel {
    // Archive state
    Path archivePath;
    FileSystem fileSystem;  // Either archive or NFS
    
    // NFS state
    NfsConnectionConfig nfsConfig;
    
    // UI state
    FileNode selectedFile;
    boolean readOnly;
    
    // Observable events
    PROP_ARCHIVE_PATH      // Archive mounted/unmounted
    PROP_NFS_CONFIG        // NFS mounted/unmounted
    PROP_OPEN              // Something is open
    PROP_READ_ONLY         // Mode changed
    PROP_SELECTED_FILE     // Selection changed
    PROP_TREE_REFRESH      // Full tree needs refresh
    
    void mountNfs(NfsConnectionConfig config) {
        this.nfsConfig = config;
        pcs.firePropertyChange(PROP_NFS_CONFIG, null, config);
    }
    
    void unmountNfs() {
        this.nfsConfig = null;
        pcs.firePropertyChange(PROP_NFS_CONFIG, ..., null);
    }
}
```

---

### **Phase 8: View Integration**

**File:** `MainFrame.java`

```java
class MainFrame extends JFrame {
    ArchiveModel model;
    ArchiveTreePanel treePanel;
    
    void setupModelListeners() {
        model.addPropertyChangeListener(PROP_NFS_CONFIG, evt -> {
            NfsConnectionConfig config = (NfsConnectionConfig) evt.getNewValue();
            if (config != null) {
                updateTitle("NFS: " + config.getHost() + ":" + config.getPort());
                refreshTreeView();
            }
        });
        
        model.addPropertyChangeListener(PROP_TREE_REFRESH, evt -> {
            treePanel.refresh();
        });
    }
}
```

**File:** `ArchiveTreePanel.java`

```java
class ArchiveTreePanel extends JPanel {
    // Shows tree of archive or NFS contents
    // Clicking items selects them in model
    // Model changes refresh tree automatically
}
```

---

### **Phase 9: Integration Tests**

**File:** `NfsIntegrationTest.java`

```java
class NfsIntegrationTest {
    // Test 1: Create and List
    //   - Write files via NfsIO
    //   - List directory
    //   - Verify files present
    
    // Test 2: Read/Write
    //   - Write content
    //   - Read content
    //   - Verify match
    
    // Test 3: Delete
    //   - Write file
    //   - Delete file
    //   - Verify removed from listing
    
    // Test 4: Rename
    //   - Write file
    //   - Rename it
    //   - Verify new name exists, old name gone
    
    // Test 5: Copy
    //   - Write file
    //   - Copy to new location
    //   - Verify content identical
    
    // Test 6: Mkdir
    //   - Create directory
    //   - Create nested file
    //   - List both levels
    //   - Verify structure
    
    // Test 7: Read-Only Mode
    //   - Try write → exception
    //   - Try delete → exception
    //   - Try mkdir → exception
}
```

**Run Tests:**
```bash
java -jar bin/artifact.jar nfs-integration        # NFS tests only
java -jar bin/artifact.jar integration            # Archive tests only
java -jar bin/artifact.jar all-integration        # Both test suites
```

---

## Key Design Decisions & Justifications

### **Decision 1: Use Java NIO.2 FileSystem SPI**

**Alternative:** Custom API like `NfsClient.readFile(path)`

**Why NIO.2 SPI:**
- ✅ Standard Java interfaces; users already know them
- ✅ Works with existing file processing libraries
- ✅ Can be extended to real NFS libraries later
- ✅ Enables `Files.readAllBytes(nfsPath)` — familiar API
- ✅ Multiple providers can coexist (archive + NFS side-by-side)

---

### **Decision 2: Pure Fabrication with NfsIO Static Class**

**Alternative:** Scatter I/O logic through NfsFileSystem/NfsPath

**Why Separate:**
- ✅ High cohesion: all I/O translation in one place
- ✅ Easy to test: no need to instantiate file systems
- ✅ Easy to mock: static methods replace easily in tests
- ✅ Clear boundaries: NfsFileSystem = abstraction, NfsIO = implementation
- ✅ Production transition: NfsIO can become real NFS RPC layer later

---

### **Decision 3: Immutable NfsConnectionConfig**

**Alternative:** Mutable config object with setters

**Why Immutable:**
- ✅ Thread-safe: no synchronization needed
- ✅ Hashable: can be used as map keys
- ✅ Predictable: config never changes mid-operation
- ✅ Testable: no hidden state
- ✅ Clear intent: "this config represents a specific mount"

---

### **Decision 4: MVC Pattern vs. Monolithic GUI**

**Alternative:** All logic in MainFrame class

**Why MVC:**
- ✅ Model logic testable without GUI
- ✅ Easy to add NFS after archive was working
- ✅ Easy to add new UI frameworks later (JavaFX, etc.)
- ✅ Separate development: GUI designer works on views, backend dev works on model
- ✅ Explicit data flow: unidirectional (Controller → Model → View)

---

### **Decision 5: Observer Pattern for Reactivity**

**Alternative:** View asks model after each action

```java
// BAD
openArchive();
treePanel.refresh();  // Manual refresh

// GOOD
openArchive();  // Model fires event
// ↓ treePanel automatically refreshes
```

**Why Observer:**
- ✅ Automatic: UI stays in sync without manual refresh calls
- ✅ Decoupled: Model doesn't know about views
- ✅ Multiple receivers: menu bar, tree, status bar all update
- ✅ Standard Java: built-in PropertyChangeSupport

---

### **Decision 6: Local FileSystem Simulation vs. Real NFS**

**Current:** Uses `$TMPDIR` to simulate NFS

**Production Path:**
1. Current: Simulate with local FS (development/testing)
2. Future: Add jNFS library dependency
3. Future: Replace `NfsIO` static methods to use real NFS RPC
4. No UI changes needed; all abstracted through FileSystem SPI

**Justification:**
- ✅ Functional development without real NFS infrastructure
- ✅ Deterministic tests (no network timeouts)
- ✅ Easy path to production-ready code
- ✅ Validates architecture before expensive NFS library integration

---

## Extension Points for Future Development

### **1. Add Another File System (S3, Azure, etc.)**

```java
class S3FileSystemProvider extends FileSystemProvider {
    @Override
    public String getScheme() { return "s3"; }
    
    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        // Parse s3://bucket/prefix
        // Create S3FileSystem
        // Return it
    }
}

// In UI: no code changes needed
// User: can mount s3://mybucket/data?region=us-east-1
```

### **2. Upgrade NFS to Real Protocol**

```java
// Replace NfsIO methods with real NFS RPC calls
public static byte[] readFile(NfsConnectionConfig config, String remotePath) {
    // Current: Path localPath = getCachePath(config).resolve(remotePath)
    // Future: Use jNFS library
    jnfs.openFile(config.getFHandle(), remotePath);
    // ...
}
```

### **3. Add Authentication/Encryption**

```java
class NfsConnectionConfig {
    // Current: host, port, export
    // Future: add username, password, encryptionCipher
    
    String username;
    String password;
    String cipher;  // "AES-256"
}
```

### **4. Add Compression for Transfers**

```java
// Compress before sending, decompress on receive
class CompressedNfsIO extends NfsIO {
    public static byte[] readFile(...) {
        byte[] compressed = super.readFile(...);
        return decompress(compressed);
    }
}
```

---

## Summary: Why This Architecture Works

| Aspect | Solution | Benefit |
|--------|----------|---------|
| **Code Reuse** | Combined archive + NFS under one FileSystem abstraction | Add new storage backends easily |
| **Testability** | Model layer separate from UI | Write unit tests without GUI framework |
| **Maintainability** | MVC + GRASP patterns | Changes localized; clear responsibility |
| **Extensibility** | Provider SPI pattern | Plug in new file systems; no core changes |
| **User Experience** | Observer pattern + reactive updates | UI always in sync; automatic refresh |
| **Future-Ready** | Simulation → Production transition path | Move from local FS → real NFS without architecture changes |
| **Safety** | Read-only mode at multiple layers | Prevents accidental modification |
| **Performance** | Command pattern for operations | Client code doesn't block; operations optimizable |

This architecture balances **immediate functionality** (works today with local FS) with **production readiness** (scales to real NFS, S3, Azure tomorrow).
