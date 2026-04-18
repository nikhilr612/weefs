# WeeFsApp — Bad-Design Analysis Report (v2)

> This report documents logical errors and design-principle violations found
> in the `bad-design` branch after the second round of upstream changes.
> It supersedes the first-iteration report.

---

## 1  Logical Errors

### 1.1 `NfsSyncingByteChannel` — `position` field integer overflow
**File:** `src/io/wfs/core/nfs/NfsSyncingByteChannel.java`

`position` was declared as `int`.  In `write()`, the expression
`position + count` uses `int` arithmetic; if `position` is close to
`Integer.MAX_VALUE` the sum silently wraps to a negative number, which is
then passed to `ensureCapacity()` → `Arrays.copyOf()` and throws
`NegativeArraySizeException` instead of a meaningful `IOException`.

**Fix applied:** `position` promoted to `long`; overflow guard added in
`write()` (`(long) position + count > Integer.MAX_VALUE`); all array-index
uses cast to `(int)` after validation.

---

### 1.2 `NfsPath.hashCode()` — inconsistency with `equals()`
**File:** `src/io/wfs/core/nfs/NfsPath.java`

`equals()` uses the `delegate` field (`Path.of(virtualPath)`) for path
comparison, which honours platform-specific semantics (case-insensitive on
Windows).  `hashCode()` hashed the raw `String virtualPath`, so on
case-insensitive file systems two paths that compare equal could have
different hash codes — violating the `Object.hashCode()` contract.

**Fix applied:** `hashCode()` now hashes `delegate` instead of `virtualPath`.

---

### 1.3 `NfsFileSystem.ensureWritableFor()` — fragile option name matching
**File:** `src/io/wfs/core/nfs/NfsFileSystem.java`

Write-access detection used `String.valueOf(option).toUpperCase().contains("WRITE")` etc.  Any custom `OpenOption` whose `toString()` accidentally contains "CREATE", "WRITE", or "DELETE" would be mis-identified.  The inner `if` block was also misaligned by four extra spaces (cosmetic indentation bug).

**Fix applied:** Replaced with explicit `StandardOpenOption` enum comparisons
(`WRITE`, `APPEND`, `CREATE`, `CREATE_NEW`, `TRUNCATE_EXISTING`,
`DELETE_ON_CLOSE`).  Indentation corrected.

---

### 1.4 `ArchiveController` — SFTP mounts entirely broken
**Files:** `ArchiveController.java`, `ArchiveModel.java`, `MenuBarFactory.java`

`mountNfs()` was rewritten to call `model.openMountUri(uri, readOnly)`, but
`currentNfsConfig` was never updated.  Three downstream effects:

| Symptom | Root cause |
|---------|-----------|
| `isNfsMounted()` always `false` after SFTP mount | checked `currentNfsConfig != null` only |
| `unmountNfs()` always a no-op | guarded on `currentNfsConfig == null` |
| `extractNfsSelected()` NPE risk | called `nfsFileOps.extractTo(currentNfsConfig, …)` with null |
| NFS menu items never enabled | `MenuBarFactory` listened only to `PROP_NFS_CONFIG`, never fired for SFTP |

**Fix applied:**
* `ArchiveModel`: added `remoteMounted` boolean flag; `openMountUri()` sets it
  for `weefs://` URIs and fires new `PROP_REMOTE_MOUNTED` event;
  `closeArchive()` clears and fires the inverse event.
* `ArchiveController.isNfsMounted()`: delegates to `model.isRemoteMounted()` in
  addition to checking `currentNfsConfig`.
* `ArchiveController.unmountNfs()`: routes to `model.closeArchive()` for SFTP
  mounts.
* `ArchiveController.extractNfsSelected()`: falls back to
  `fileOps.extractTo()` when `currentNfsConfig == null`.
* `MenuBarFactory`: NFS menu now also subscribes to `PROP_REMOTE_MOUNTED`.

---

## 2  SOLID Violations

### 2.1 Single-Responsibility Principle (SRP)

| Class | Problem |
|-------|---------|
| `ArchiveModel` | God object: manages archive file systems, legacy NFS config, SFTP remote-mount state, property-change notifications, UI-tree helpers, and file I/O helpers all in one class |
| `ArchiveController` | Handles archive lifecycle, NFS lifecycle, file operations, background threading, dialog orchestration, and format detection |
| `NfsSftpFsIO` | Handles SFTP session management, file I/O, directory listing, attribute reading, and path normalisation |

### 2.2 Open/Closed Principle (OCP)

* **`FileSystemFactory.registerDefaults()`** hardcodes exactly three drivers.
  Adding a fourth driver (e.g., S3, WebDAV) requires editing the factory
  rather than just registering a new implementation.
* **`ArchiveFormatDetector`** is a chain of `if/endsWith` blocks; adding a
  new format requires editing the detector.
* **`CompressionStrategyFactory`** uses a `switch`; same problem.

### 2.3 Dependency-Inversion Principle (DIP)

* `NfsFileSystemDriver` instantiates `NfsSftpFsProvider` directly with `new`.
* `ZipFileSystemDriver` instantiates `ExtZipFsProvider` directly with `new`.
  Both depend on concrete classes; neither accepts an interface.
* `INfsController.getNfsFileOps()` returns the concrete `NfsFileOperations`
  class, leaking an implementation detail through the interface.
* `WeeFsApp` wires `ArchiveModel` and `ArchiveController` by direct
  instantiation — no dependency injection, making substitution impossible.

### 2.4 Interface Segregation Principle (ISP)

`IFileOperations` bundles seven methods (`createFile`, `createDirectory`,
`delete`, `rename`, `saveFile`, `extractTo`, `copy`).  Callers that only need
read operations still depend on the write half.

---

## 3  DRY Violations

| Duplication | Locations |
|-------------|-----------|
| `deleteRecursive` / `deleteRecursively` | `FileOperations.java`, `NfsIO.java` — near-identical walk-and-delete logic |
| Error dialog boilerplate | `FileOperations.showError()`, `NfsFileOperations.showError()`, `ArchiveController.showError()` — three separate `JOptionPane.showMessageDialog` wrappers |
| Read-only guard pattern | Repeated `if (config.isReadOnly()) return false;` in every `NfsFileOperations` method |
| Session-per-operation pattern | `NfsSftpFsIO.withChannel()` duplicated inline for read, write, list, stat, etc. |

---

## 4  Anti-Patterns

### 4.1 Stale-State / Split-Brain (ArchiveController)
`ArchiveController.currentNfsConfig` and `ArchiveModel.nfsConfig` track
overlapping state that can diverge (as they did post-refactor).  A single
source of truth should live in the model.

### 4.2 Dual-Constructor Mode (`NfsFileSystem`)
`NfsFileSystem` has two constructors — one for SFTP, one for legacy NFS —
with runtime `null` checks throughout (`config != null ? … : legacyConfig`).
This is a textbook case of inappropriate intimacy and violates SRP; the two
modes should be separate classes sharing an interface.

### 4.3 Connection-Per-Operation (`NfsSftpFsIO`)
`withChannel()` opens a new JSch SSH session for every single SFTP call.
This is an O(n) connection overhead where a connection pool or a persistent
session would give O(1).  Additionally, `listDirectory()` opens two channels
where one would suffice.

### 4.4 Primitive Obsession (`NfsParsedUri`)
URI parsing and SSH configuration are bundled together; the result is a
package-private record that callers cannot subclass, test with fakes, or
extend without editing `NfsParsedUri`.

### 4.5 Singleton (`WeeFsApp`)
`WeeFsApp` uses a static singleton with a `synchronized getInstance()`.
This makes the application untestable in isolation and couples start-up logic
to global state.

### 4.6 Magic String Keys (`FileSystemFactory`)
`FileSystemFactory` maps URI schemes to drivers with hard-coded string
literals (`"xzip"`, `"weefs"`, `"file"`).  Typos are not caught at
compile time.

---

## 5  Maintainability Issues

| Issue | Impact |
|-------|--------|
| No connection pooling in SFTP layer | Performance degrades linearly with SFTP operation count; every file browse triggers a new SSH handshake |
| `ArchiveModel.listChildren()` branching on `isNfsMounted()` | Every new backend (S3, WebDAV…) would require another `if` branch |
| `NfsFileSystem` dual-mode | Every new method must branch on `config != null` — complexity grows with each addition |
| `SwingUtilities.invokeLater` in non-UI classes (`NfsFileOperations`) | Couples business logic to Swing EDT; breaks headless or test use |
| No `@GuardedBy` annotations despite `volatile` and `AtomicBoolean` use | Race conditions are hard to spot during code review |
| `InstallShutdownHook()` must be called manually | If the caller forgets, SFTP sessions leak on JVM exit |

---

## 6  Summary Table

| # | Category | Severity | Fixed in bad-design? |
|---|----------|----------|----------------------|
| 1 | `NfsSyncingByteChannel` int overflow | **Critical** | ✅ |
| 2 | `NfsPath.hashCode()` contract violation | **High** | ✅ |
| 3 | `ensureWritableFor()` fragile string match | **Medium** | ✅ |
| 4 | SFTP mount invisible to controller/UI | **Critical** | ✅ |
| 5 | `ArchiveModel` god object (SRP) | Medium | ❌ (deferred to good-design) |
| 6 | `FileSystemFactory` OCP violation | Medium | ❌ |
| 7 | `NfsFileSystemDriver`/`ZipFileSystemDriver` DIP | Medium | ❌ |
| 8 | Connection-per-operation anti-pattern | High | ❌ |
| 9 | `NfsFileSystem` dual-constructor | Medium | ❌ |
| 10 | DRY violations (delete, error dialog) | Low | ❌ |

Items 5–10 are addressed in the `good-design` branch.
