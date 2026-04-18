# WeEFS GUI User Guide

## Quick Start

### Run the Application
```bash
cd /Users/prajwalnaganagoudar/Desktop/weefs
java -jar bin/artifact.jar gui
```

This opens the WeEFS desktop application.

---

## UI Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ WeEFS - Archive & NFS Browser     _  □  x                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ File      NFS      Edit      View      Help                  ← Menu Bar     │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Open] [New] [Close] | [Mount NFS] [Unmount] | [Refresh] | [New] [Del] ... ← Toolbar
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────┐  ┌──────────────────────────────────────────┐ │
│  │  FILE TREE              │  │  FILE CONTENT VIEWER                     │ │
│  │  • documents.zip        │  │                                          │ │
│  │    ├─ reports/          │  │  📄 File: report.pdf                     │ │
│  │    │  ├─ q1.pdf         │  │                                          │ │
│  │    │  └─ q2.pdf    ─────┼──│ Size: 2.5 MB                            │ │
│  │    ├─ notes.txt         │  │ Type: PDF Document                       │ │
│  │    └─ images/           │  │ Modified: 2026-04-10                     │ │
│  │       ├─ chart1.png     │  │                                          │ │
│  │       └─ chart2.png     │  │ [Edit] [Save] [Download]                 │ │
│  │                         │  │                                          │ │
│  │  (Tree with icons)      │  │ (Content preview or properties)          │ │
│  │                         │  │                                          │ │
│  │                         │  │                                          │ │
│  │                         │  │                                          │ │
│  └─────────────────────────┘  └──────────────────────────────────────────┘ │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ Status: "documents.zip open (Read/Write)" | Items: 5 | Size: 12.3 MB       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Menu Bar Guide

### FILE Menu
```
File
├─ New Archive...             (Ctrl+Shift+N)  → Create new ZIP/TAR
├─ Open Archive...            (Ctrl+O)        → Browse and open archive
├─ Close Archive              (Ctrl+W)        → Unmount current archive
├─ Save Archive               (Ctrl+S)        → Persist changes
├─ ─────────────────────────────────────────
└─ Exit                       (Ctrl+Q)        → Close application
```

### NFS Menu (NEW!)
```
NFS
├─ Mount NFS...               (Ctrl+Shift+M)  → Connect to NFS server
├─ Unmount NFS                (Ctrl+Shift+U)  → Disconnect NFS
├─ ─────────────────────────────────────────
└─ Extract NFS File...        → Save file to local disk
```

### EDIT Menu
```
Edit
├─ New File...                (Ctrl+N)        → Create file in archive/NFS
├─ New Directory...           (Ctrl+Shift+D)  → Create folder
├─ ─────────────────────────────────────────
├─ Rename...                  (F2)            → Rename file/folder
├─ Delete                     (Delete)        → Remove file/folder
├─ ─────────────────────────────────────────
└─ Extract To...              → Export file to local disk
```

### VIEW Menu
```
View
└─ Refresh Tree               (F5)            → Reload file listing
```

### HELP Menu
```
Help
└─ About weefs                → Show application info
```

---

## Toolbar Guide

```
Buttons (Left to Right):

Archive Operations:
┌──────────────────────────────────┐
│ [Open] [New Archive] [Close]     │  Open/Create/Close archives
└──────────────────────────────────┘

NFS Operations:
┌──────────────────────────────────┐
│ [Mount NFS] [Unmount NFS]        │  Connect/Disconnect to NFS
└──────────────────────────────────┘

File Operations:
┌──────────────────────────────────┐
│ [New File] [New Dir] [Delete]    │  Create/delete files/folders
│ [Extract] [Refresh]              │  Extract files, refresh view
└──────────────────────────────────┘
```

Button states:
- ✅ Green/Normal: Enabled (can click)
- ❌ Gray/Disabled: Not applicable now

---

## Step-by-Step Usage

### Opening an Archive

**Step 1: Click File → Open Archive**
```
File menu appears with "Open Archive..." option
```

**Step 2: Choose Archive File**
```
File browser dialog opens
Navigate to your ZIP/TAR file
Example: ~/Downloads/documents.zip
Click "Open"
```

**Step 3: Select Mode**
```
Dialog: "Open archive in which mode?"
Options: [Read/Write] [Read Only]
Choose "Read/Write" to edit files
Choose "Read Only" to only view
```

**Step 4: View Archive Contents**
```
Tree view shows:
📦 documents.zip
├─ 📁 reports/
│  ├─ 📄 report1.pdf
│  └─ 📄 report2.pdf
├─ 📄 notes.txt
└─ 📁 images/
   ├─ 🖼️ chart.png
   └─ 🖼️ diagram.jpg

Status bar shows: "documents.zip open (Read/Write)"
                  "Items: 5 | Size: 12.3 MB"
```

---

### Creating a New File in Archive

**Step 1: Right-click in Tree**
```
Context menu appears:
├─ New File...
├─ New Directory...
├─ Rename
├─ Delete
└─ Extract To...
```

**Step 2: Click "New File..."**
```
Input dialog: "Enter file name:"
User types: "untitled.txt"
Click "OK"
```

**Step 3: File Created**
```
Tree updates:
📁 New Folder/
   ├─ 📄 existing_file.txt
   └─ 📄 untitled.txt  ← NEW!

File appears in archive immediately
```

---

### Editing File Content

**Step 1: Double-click File**
```
Right panel shows file content:

📄 notes.txt
─────────────────────────────
This is my note.

It has multiple lines.
And can be edited.

[Edit Mode On] [Save] [Cancel]
```

**Step 2: Edit Text**
```
User modifies content in editor
─────────────────────────────
This is my UPDATED note.

It has multiple lines.
And can be edited RIGHT HERE!
```

**Step 3: Click Save**
```
File written back to archive
Status: "File saved successfully"
```

---

### Extracting a File

**Step 1: Select File**
```
Click on file in tree:
📄 report.pdf
```

**Step 2: Click Extract Button (or Edit → Extract To...)**
```
Save dialog appears:
Where to save?  [~/Downloads/report.pdf]
                [Browse...]  [Save]
```

**Step 3: Choose Location**
```
User selects: ~/Desktop/
Click "Save"
```

**Step 4: File Saved**
```
Status: "File extracted successfully"
~/Desktop/report.pdf now exists locally
```

---

### Mounting NFS

**Step 1: Click NFS → Mount NFS...**
```
NFS Connection Dialog appears:

┌─────────────────────────────────────┐
│ Mount NFS                      x    │
├─────────────────────────────────────┤
│ Host:              [192.168.1.100] │
│ Port:              [2049]          │
│ Export Path:       [/exports/data] │
│ Mount Path:        [/exports/data] │
│ Timeout (sec):     [30]            │
│ [✓] Read-Only                      │
├─────────────────────────────────────┤
│              [Mount] [Cancel]       │
└─────────────────────────────────────┘
```

**Step 2: Enter NFS Details**
```
Host: 192.168.1.100      (Your NFS server IP)
Port: 2049               (Standard NFS port)
Export Path: /exports    (NFS export path)
Timeout: 30              (Connection timeout)
Read-Only: OFF           (Allow write access)

Click [Mount]
```

**Step 3: NFS Connected**
```
Tree updates to show NFS contents:
📂 /exports/data (NFS)
├─ 📁 annual_reports/
│  ├─ 📄 2024-report.pdf
│  └─ 📄 2023-report.pdf
├─ 📁 current/
│  ├─ 📄 draft.docx
│  └─ 📄 draft.xlsx
└─ 📄 shared_data.csv

Menu bar: "NFS" → "Unmount NFS" (enabled)
Status: "[NFS] Connected: 192.168.1.100"
```

---

### File Operations on NFS

**Creating File on NFS:**
```
Same as archive - Right-click → New File...
File is created directly on NFS server
```

**Deleting File on NFS:**
```
Select file → Click [Delete] button
Confirmation dialog: "Delete 'file.txt'?"
Click [Yes]
File removed from NFS
```

**Renaming File on NFS:**
```
Select file → Edit menu → Rename...
Input dialog: "Enter new name:"
User types: "renamed.txt"
Click OK
File renamed on NFS
```

**Extracting File from NFS to Local Disk:**
```
Select file → NFS menu → Extract NFS File...
Save dialog appears
User chooses destination (e.g., ~/Downloads/)
Click Save
File downloaded locally
```

---

### Switching Between Archive and NFS

**Before:**
```
documents.zip is open
Tree shows archive contents
```

**User clicks: NFS → Mount NFS... and connects**
```
Archive automatically closes
NFS mounts
Tree switches to show NFS structure
All buttons/menus update
```

**User clicks: File → Open Archive... and opens new file**
```
NFS automatically unmounts
Archive mounts
Tree switches back to archive contents
```

---

## UI Component Guide

### Tree View (Left Panel)
- **Shows:** File hierarchy with icons
- **Icons:** 
  - 📁 Blue folder (directory)
  - 📄 White document (text file)
  - 🖼️ Image icon (image file)
  - 📦 Archive icon (archive root)
- **Actions:**
  - Click: Select file/folder
  - Double-click: Open file content
  - Right-click: Context menu

### File Content Panel (Right Panel)
- **Shows:** File preview or properties
- **For text files:** Editable text area
- **For images:** Image preview
- **For other files:** Properties (size, type, date)
- **Buttons:**
  - [Edit] - Enable edit mode
  - [Save] - Save changes
  - [Download] - Extract to local disk

### Status Bar (Bottom)
- **Left:** Current file/archive status
- **Center:** Item count and total size
- **Right:** Mode (Read/Write or Read-Only)

**Examples:**
```
"documents.zip open (Read/Write)" | Items: 8 | Size: 25.5 MB
"NFS: 192.168.1.100:2049" | Items: 42 | Size: 5.2 GB
```

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+O | Open Archive |
| Ctrl+Shift+N | New Archive |
| Ctrl+W | Close Archive |
| Ctrl+S | Save Archive |
| Ctrl+Q | Exit |
| Ctrl+N | New File |
| Ctrl+Shift+D | New Directory |
| Ctrl+Shift+M | Mount NFS |
| Ctrl+Shift+U | Unmount NFS |
| F2 | Rename |
| Delete | Delete |
| F5 | Refresh |

---

## Color Coding & Visual Indicators

### Button States
- **Enabled:** Normal blue/white color → Can click
- **Disabled:** Gray color → Cannot use now

### File Types (By Icon Color)
- 📁 Blue → Folder/Directory
- 📄 Gray → Text file
- 🖼️ Orange → Image file
- 📦 Brown → Archive file
- 📋 Green → Data file

### Selection
- **Highlighted blue:** Currently selected item
- **White background:** File content visible on right panel

---

## Error Handling

### Common Dialogs

**Cannot Open File**
```
┌─────────────────────┐
│ Error               │
├─────────────────────┤
│ Open Archive        │
│ failed:             │
│ File not found      │
│         [OK]        │
└─────────────────────┘
```

**Confirm Delete**
```
┌──────────────────────────────┐
│ Confirm Delete               │
├──────────────────────────────┤
│ Delete "report.pdf"?         │
│                              │
│ [Yes] [No] [Cancel]          │
└──────────────────────────────┘
```

**Read-Only Mode**
```
Archive selected as "Read Only"
├─ File operation buttons: DISABLED (grayed out)
├─ Edit menu items: DISABLED
├─ Status bar shows: "(Read Only)"
└─ Can only: View, Extract, Properties
```

---

## Tips & Tricks

### ✅ Smart Features
1. **Auto-refresh** - Tree updates when files change
2. **Icon detection** - File types shown by icon
3. **Syntax highlighting** - Text files show with colors
4. **Quick extract** - Toolbar button for one-click export
5. **Modal dialogs** - Prevents accidental actions

### ⚡ Efficiency Tips
1. **Double-click file** → Quick view instead of menu
2. **Right-click** → Context menu with relevant options
3. **Keyboard shortcuts** → Faster than menu navigation
4. **Drag & drop** → Not supported (use Extract)

### 🔒 Safety Features
1. **Read-only mode** → Protect archives from accidental edits
2. **Confirmation dialogs** → Confirm destructive operations
3. **Error messages** → Clear feedback on what went wrong
4. **Status bar** → Shows current archive/NFS state

---

## Session Example

```
1. START
   Application launches with empty state
   All buttons DISABLED
   Menu shows "Open Archive", "Mount NFS" enabled only

2. OPEN ARCHIVE
   Click File → Open Archive
   Select ~/Documents/project.zip
   Choose "Read/Write" mode
   Tree shows: 15 files in archive
   Buttons ENABLED

3. CREATE FILE
   Right-click in tree → New File
   Name: "README.md"
   File created in archive
   Tree updates immediately

4. EDIT FILE
   Double-click "README.md"
   Right panel shows text editor
   Add content: "# My Project"
   Click Save
   File updated in archive

5. MOUNT NFS
   Click NFS → Mount NFS
   Host: 192.168.1.50
   Port: 2049
   Archive auto-closes
   NFS structure displayed
   NFS buttons now ENABLED

6. COPY FILE
   Select file on NFS: "data.csv"
   Click Extract
   Choose ~/Downloads/
   File saved locally

7. UNMOUNT & CLOSE
   Click File → Close Archive
   Application returns to empty state
   All buttons DISABLED
```

---

## Running the Application

### From Terminal
```bash
cd /Users/prajwalnaganagoudar/Desktop/weefs
java -jar bin/artifact.jar gui
```

### Expected Startup
```
1. Window appears (takes 1-2 seconds)
2. Menu bar loads
3. Toolbar appears
4. Empty tree view shown
5. Status bar shows: "No archive open"
6. Ready for user input
```

### System Requirements
- Java 11+ (OpenJDK or Oracle)
- ~100 MB RAM
- Display: 1024x768 minimum (recomm 1280x960+)
- Mouse + keyboard

---

## Layout Customization

The UI is built with Swing and uses standard layouts:
- **Menu bar** → Fixed at top (not movable)
- **Toolbar** → Fixed below menu (not movable)
- **Split pane** → Tree and content panels resizable
  - Drag divider between panels to resize
- **Status bar** → Fixed at bottom

Try dragging the | divider between tree and content panels to adjust sizes.

---

This is your interactive guide! Now you can:
1. Run the application
2. Try opening archives
3. Create and edit files
4. Mount NFS and browse network files
5. Use all the UI features described above

Enjoy exploring WeEFS! 🎉
