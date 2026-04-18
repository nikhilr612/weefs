# WeEFS UI - Quick Start (5 Minutes)

## 🚀 Launch the App

```bash
java -jar bin/artifact.jar gui
```

**Expected:** A window opens with:
- Menu bar at top (File, NFS, Edit, View, Help)
- Toolbar with buttons
- Empty tree on left (no archives open yet)
- Empty content area on right
- Status bar at bottom saying "No archive open"

---

## 📖 Try These 5 Activities

### 1. Create a Test Archive (2 min)

**Click:** `File` → `New Archive...`

```
Dialog appears:
Where to save?
[Select location] e.g., ~/Desktop/my-archive.zip
[Create]
```

**Result:**
- Tree shows: 📦 my-archive.zip
- Status bar: "my-archive.zip open (Read/Write)"
- Buttons are now ENABLED ✅

---

### 2. Create a File in Archive (1 min)

**Right-click** in the tree → `New File...`

```
Input dialog:
Enter file name: test.txt
[OK]
```

**Result:**
```
Tree shows:
📦 my-archive.zip
  └─ 📄 test.txt  ← NEW!
```

---

### 3. Edit the File (1 min)

**Double-click** `test.txt`

```
Right panel shows:
[Text editor box]
_________________
Enter some text!
_________________

[Save] [Cancel]
```

Type something, click `[Save]`

**Result:**
- Status: "File saved successfully"
- File stored in archive

---

### 4. Extract the File (1 min)

**Select** `test.txt` → Click `[Extract]` button (or `Edit` → `Extract To...`)

```
Save dialog:
Where to save?
~/Desktop/test.txt
[Save]
```

**Result:**
- Status: "File extracted successfully"
- File now on your disk: `~/Desktop/test.txt`

---

### 5. Close Archive (30 sec)

**Click:** `File` → `Close Archive`

```
Dialog (if unsaved changes):
Save changes to "my-archive.zip"?
[Yes] [No] [Cancel]
```

Choose `[Yes]` to save, then archive closes.

**Result:**
- Tree becomes empty
- Buttons disabled (grayed out)
- Status: "No archive open"

---

## 🌐 Try NFS (Requires NFS Server)

### Mount NFS

**Click:** `NFS` → `Mount NFS...`

```
Dialog:
Host:        [192.168.1.100]
Port:        [2049]
Export:      [/exports/data]
Mount Path:  [/mnt/nfs]
Timeout:     [30]
[✓] Read-Only

[Mount] [Cancel]
```

Fill in your NFS server details, click `[Mount]`

**Result:**
- Archive auto-closes (can use one protocol at a time)
- Tree shows NFS structure:
  ```
  📂 /mnt/nfs (NFS)
    ├─ 📁 folder1/
    ├─ 📁 folder2/
    └─ 📄 file.txt
  ```
- NFS menus now enabled

---

## 🎮 Common Tasks

### Create a Directory

Select parent folder → `[New Dir]` button (or `Edit` → `New Directory...`)

```
Input: Enter directory name: [reports]
Result: Folder created in tree
```

---

### Rename Any Item

Select item → `F2` key (or `Edit` → `Rename...`)

```
Input: Enter new name: [old_name → new_name]
Result: Item renamed immediately
```

---

### Delete Item

Select item → `Delete` key (or `[Delete]` button)

```
Confirm: Delete "filename"?
[Yes] [No]
Result: Item removed from archive/NFS
```

---

### Copy File to Your Computer

Select file → `[Extract]` button

```
Choose location: ~/Downloads/filename
Result: File saved locally
```

---

## 🎯 Key Features to Try

✅ **Read-Only Mode**
- Open archive as "Read Only"
- Notice buttons disabled
- Can only View and Extract

✅ **Properties Dialog**
- Right-click file → `Properties`
- Shows: Size, Type, Modified Date
- Help menu → `About` for app info

✅ **Toolbar Shortcuts**
- Hover over buttons → See what they do
- Click to execute instantly
- Buttons gray out when not applicable

✅ **Split Pane Resize**
- Drag | divider (between tree and content)
- Make tree wider/narrower as needed
- Position saved between sessions

---

## 📋 What You Can Do

### With Archives (ZIP/TAR)
- ✅ Create new archive
- ✅ Open existing archive
- ✅ View files in tree structure
- ✅ Create files/directories
- ✅ Edit text file content
- ✅ Rename files/directories
- ✅ Delete files/directories
- ✅ Extract files to disk
- ✅ Save archives (persist changes)
- ✅ Make read-only to protect

### With NFS
- ✅ Mount NFS shares
- ✅ Browse files on network
- ✅ Create files/directories
- ✅ Edit text files
- ✅ Rename files
- ✅ Delete files
- ✅ Extract files to local disk
- ✅ Set read-only permissions
- ✅ Unmount when done

---

## 🎨 UI Color Guide

| Color | Meaning |
|-------|---------|
| 📁 Blue Folder | Directory |
| 📄 Gray Document | Text File |
| 🖼️ Orange Image | Image File |
| 📦 Brown Box | Archive |
| Blue Highlight | Selected Item |
| Gray Button | Action Not Available |
| Green Button | Action Available |

---

## ⌨️ Essential Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Ctrl+O | Open Archive |
| Ctrl+Shift+N | New Archive |
| Ctrl+W | Close Archive |
| Ctrl+S | Save Archive |
| Ctrl+N | New File |
| Ctrl+Shift+D | New Dir |
| Ctrl+Shift+M | Mount NFS |
| Ctrl+Shift+U | Unmount NFS |
| F2 | Rename |
| Delete | Delete |
| F5 | Refresh |

---

## 🆘 Troubleshooting

### Buttons Gray Out / Disabled?
- ✅ Open an archive first OR mount NFS
- ✅ Some buttons only work on files (not folders)
- ✅ Some only work on editable archives

### Can't Edit Files?
- ✅ Make sure archive is "Read/Write" (not Read-Only)
- ✅ Some file types are preview-only (Images, PDFs)

### File Not Showing Changes?
- ✅ Click `[Refresh]` button or press F5
- ✅ Check Status bar - should say "saved"

### NFS Won't Mount?
- ✅ Check host/port are correct
- ✅ Verify NFS server is running
- ✅ Check network connectivity
- ✅ Timeout: increase if server slow

---

## 💯 Next Steps

1. **Create an archive** and add some files
2. **Edit some content** in the files
3. **Extract files** to your desk and verify they work
4. **Try NFS** if you have an NFS server available
5. **Explore menus** - everything is intuitive!

---

## 📚 Reference Docs

For more details, see:
- `UI_GUIDE.md` - Complete UI documentation
- `HOW_IT_WORKS.md` - Architecture and design
- `NFS_VERIFICATION_REPORT.md` - Compilation verification

Enjoy using WeEFS! 🎉
