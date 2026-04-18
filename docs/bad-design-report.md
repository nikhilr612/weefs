# Bad Design Report — weefs

This report catalogs every bad design choice identified in the `bad-design` branch,
organized by category: logical errors, SOLID violations, DRY violations,
anti-patterns, and maintainability issues.

---

## 1. Logical Errors (Fixed in this branch)

| # | File | Issue | Fix Applied |
|---|------|-------|-------------|
| 1 | `ArchiveModel.java:139` | `pcs.firePropertyChange(PROP_READ_ONLY, !readOnly, readOnly)` — the "old value" argument is `!readOnly` (negation of the *new* value) instead of the actual *previous* value. Listeners see a wrong before/after transition. | Capture `previousReadOnly` before mutating the field; pass it as the old value. |
| 2 | `NfsFsProvider.java` inner class `NfsWritableByteChannel` | `position` field declared as `int`; `position(long)` silently truncates via `(int) newPosition`. Files > 2 GB produce a corrupt position. | Change field to `long`; validate in `position(long)` — throw `IOException` if value exceeds `Integer.MAX_VALUE` (buffer-backed channel constraint). |
| 3 | `NfsFsProvider.newFileSystem()` | `catch (IOException ex) { throw ex; }` — a no-op catch-rethrow that adds noise, suppresses IDE warnings, and hides intent. | Remove the try/catch wrapper entirely (method already declares `throws IOException`). |
| 4 | `MenuBarFactory.createNfsMenu()` | Unconditional `(INfsController) controller` cast. Any `IArchiveController` that does not implement `INfsController` throws `ClassCastException`. | Guard with `instanceof` check; return a disabled "NFS not available" menu item if the cast is invalid. |
| 5 | `ToolBarFactory.create()` | Same unconditional `(INfsController) controller` cast. NFS buttons call methods on `null`-equivalent reference. | Guard with `instanceof`; store as `final`; null-check in every lambda. |

---

## 2. SOLID Violations

### 2.1 Single Responsibility Principle (SRP)

| Class | Problem |
|-------|---------|
| `ArchiveModel` | Manages **both** the local ZIP/TAR archive lifecycle **and** the NFS mount state. These are entirely separate concerns (different data, different events, different error modes). One change to NFS config inadvertently affects archive-open state logic. |
| `ArchiveController` | 502-line **god class**. Implements `IArchiveController` *and* `INfsController`, handles archive open/save/close, NFS mount/unmount, file CRUD, dialog orchestration, and background worker management — all in one class. |
| `ExtZipFsIO` | Static utility that dispatches ZIP extraction, TAR extraction, single-file decompression, ZIP writing, TAR writing, and single-file compression all in one class. Adding a new format requires touching this file. |

### 2.2 Open/Closed Principle (OCP)

| Class | Problem |
|-------|---------|
| `ArchiveFormatDetector` | Uses a hardcoded chain of `if (name.endsWith(".zip")) … else if (name.endsWith(".tar")) …` branches. Adding a new format requires modifying the detector. |
| `CompressionStrategyFactory` | Uses a `switch` on a string key. Adding a new compression algorithm requires editing the factory. |
| `FileTypeDetector` | Hardcodes sets of file extensions per type (text, image, audio, etc.). New extensions require code changes. |
| `TarArchiveFormat` | `supports()` only matches `.tar`; compressed variants (`.tar.gz`, `.tar.bz2`, etc.) are silently ignored by the strategy registry even though they are valid TAR-family formats. |

### 2.3 Liskov Substitution Principle (LSP)

| Class | Problem |
|-------|---------|
| `NfsPath` | Three `Path` interface methods throw `UnsupportedOperationException`: `relativize()`, `toUri()`, and `toFile()`. Any code that uses a `Path` polymorphically (e.g., Java NIO utilities, stream operations) will break at runtime when handed an `NfsPath`. |
| `ExtZipPath` | `isAbsolute()` always returns `true` regardless of the actual path string, violating the `Path` contract. `hashCode()` uses `System.identityHashCode(fileSystem)` which is not stable across JVM runs and breaks `HashMap`/`HashSet` semantics. |

### 2.4 Dependency Inversion Principle (DIP)

| Site | Problem |
|------|---------|
| `ArchiveModel` | Directly instantiates `ExtZipFsProvider` (a concrete class). There is no `FileSystemProvider` interface abstraction; the model is tightly coupled to the ZIP/TAR implementation and cannot be tested with a mock provider. |
| `ArchiveController` | Directly instantiates `FileOperations` and `NfsFileOperations` inside the constructor. No injection point exists; the controller cannot be tested without real file I/O. |

### 2.5 Interface Segregation Principle (ISP)

| Site | Problem |
|------|---------|
| `INfsController.isNfsMounted()` | Returns `boolean` but forces any non-NFS controller to implement NFS-awareness. The NFS concern leaks into the general controller interface. |
| `MenuBarFactory` / `ToolBarFactory` | Accept `IArchiveController` but cast it to `INfsController` — the factory effectively requires a *wider* interface than it declares. Clients who pass a minimal `IArchiveController` implementation crash at runtime. |

---

## 3. DRY Violations

| # | Duplicated Code | Locations |
|---|-----------------|-----------|
| 1 | `deleteRecursively(Path)` — recursive directory deletion logic | `ExtZipFsIO`, `NfsIO`, `FileOperations` — three nearly-identical implementations |
| 2 | `showError(String)` — `JOptionPane.showMessageDialog` error helper | `FileOperations`, `NfsFileOperations` |
| 3 | Read-only guard | Every method in `NfsFileOperations` starts with `if (config.isReadOnly()) return false` |
| 4 | "none" compression key | Hardcoded in both `ZipArchiveFormat` and `TarArchiveFormat`; format-to-default-compression mapping is not centralized |
| 5 | `toLowerCase()` locale inconsistency | `ZipArchiveFormat.supports()` calls `toLowerCase()` without `Locale.ROOT`; `TarArchiveFormat` uses `Locale.ROOT`; `ArchiveFormatDetector` uses `Locale.ROOT` — three different conventions for the same operation |

---

## 4. Anti-Patterns

### 4.1 Singleton — `WeeFsApp`
`WeeFsApp` uses a manual double-checked locking singleton but the `instance` field is **not `volatile`**, allowing a partially-constructed object to be visible to other threads. Beyond the thread-safety bug, the singleton pattern makes unit testing impossible (cannot substitute a mock launcher).

### 4.2 Magic String — `ArchiveTreePanel`
`"Loading..."` is used as a sentinel placeholder child node to mark lazy-load tree nodes. Any refactor that renames the string, or any file that happens to be named `"Loading..."`, silently breaks the lazy-load detection.

### 4.3 God Object — `ArchiveController`
502 lines, two interfaces, six collaborators, background threading, dialog management, and file I/O dispatch — far beyond a single responsibility. Adding any new feature requires understanding the entire class.

### 4.4 Duplicate State — `ArchiveController.currentNfsConfig`
The controller maintains its own `currentNfsConfig` field that mirrors `model.getNfsConfig()`. These can drift out of sync: if the model updates its config through another code path, the controller's cached copy becomes stale, causing NFS operations to use a wrong/outdated configuration.

### 4.5 Feature Envy — `ArchiveTreePanel`
`ArchiveTreePanel.loadChildrenIfNeeded()` directly accesses `model.getNfsConfig()` to determine display logic (whether to list NFS children vs. archive children). This is domain logic that belongs in the model or controller, not in the view.

### 4.6 Primitive Obsession — `NfsConnectionConfig`
Host/port/path are stored as raw `String`/`int` primitives with no encapsulation of the connection URI. Callers must manually assemble URIs from parts, leading to scattered string concatenation across `NfsFsProvider`, `NfsIO`, and `NfsPath`.

---

## 5. Maintainability Issues

| Issue | Impact |
|-------|--------|
| `ArchiveController` god class | Any feature touches the same 500-line file, causing merge conflicts and regression risk |
| No injection in `ArchiveModel` / `ArchiveController` | Unit tests cannot run without actual file system and NFS server access |
| `NfsPath` throws `UnsupportedOperationException` | Third-party NIO utilities (e.g., `Files.copy`, stream collectors) silently fail with misleading stack traces |
| `ExtZipPath.hashCode()` instability | Collections (`HashMap`, `HashSet`) containing `ExtZipPath` objects exhibit non-deterministic behavior |
| `ExtZipPath.isAbsolute()` always `true` | `Path.resolve()` behaves incorrectly for any path that should be relative |
| Three copies of `deleteRecursively` | Bug fixes must be applied in three places; one copy may silently diverge |
| Hardcoded format detection | Each new archive format (e.g., `.7z`, `.rar`) requires changes in multiple classes |

---

## Summary

The `bad-design` branch contains **5 logical errors** (fixed), **10+ SOLID violations**
across all five principles, **5 DRY violations**, **6 anti-patterns**, and several
maintainability hazards. The most impactful issues are the god-class `ArchiveController`,
the SRP violation in `ArchiveModel`, the three copies of `deleteRecursively`, and the LSP
violations in `NfsPath` / `ExtZipPath` that break standard Java NIO contracts.

All of these are addressed in the `good-design` branch.
