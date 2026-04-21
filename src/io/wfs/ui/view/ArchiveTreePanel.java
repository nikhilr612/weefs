package io.wfs.ui.view;

import io.wfs.ui.controller.IArchiveController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;
import io.wfs.ui.model.MountSession;
import io.wfs.ui.util.FileTypeDetector;
import io.wfs.ui.util.IconFactory;
import io.wfs.ui.util.SwingUtils;
import io.wfs.ui.view.dialog.PropertiesDialog;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Left-side panel showing all mounted sessions as top-level tree roots.
 * Each mount (archive, directory, NFS share) gets its own collapsible root node.
 * Session nodes show a hover-close (✕) button painted directly in paintComponent
 * and expose "Unmount" / "Save" via right-click.
 */
public final class ArchiveTreePanel extends JPanel {

    private static final int CLOSE_BTN_WIDTH = 22;

    private final ArchiveModel model;
    private final IArchiveController controller;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode hiddenRoot;

    /** Maps session ID -> the session's top-level tree node. */
    private final Map<String, DefaultMutableTreeNode> sessionNodes = new HashMap<>();

    /** Row currently under the mouse (-1 = none). Drives the hover-close button. */
    private int hoveredRow = -1;

    public ArchiveTreePanel(ArchiveModel model, IArchiveController controller) {
        this.model = model;
        this.controller = controller;

        setLayout(new BorderLayout());

        JLabel sectionHeader = new JLabel("Mounted");
        sectionHeader.setFont(sectionHeader.getFont().deriveFont(Font.BOLD));
        sectionHeader.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

        hiddenRoot = new DefaultMutableTreeNode("__root__");
        treeModel = new DefaultTreeModel(hiddenRoot);

        // Anonymous subclass so we can paint the hover-close button in the same
        // Graphics pass as the rows — avoids any clipping/layering issues.
        tree = new JTree(treeModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (hoveredRow < 0) return;
                TreePath p = getPathForRow(hoveredRow);
                if (p == null) return;
                DefaultMutableTreeNode nd = (DefaultMutableTreeNode) p.getLastPathComponent();
                if (!(nd.getUserObject() instanceof SessionNodeData)) return;
                Rectangle rb = getRowBounds(hoveredRow);
                if (rb == null) return;
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    String txt = "\u2715"; // ✕
                    // Anchor to the visible viewport edge so the button stays
                    // fixed relative to the scrollbar regardless of panel width.
                    Rectangle vis = getVisibleRect();
                    int tx = vis.x + vis.width - fm.stringWidth(txt) - 8;
                    int ty = rb.y + (rb.height + fm.getAscent() - fm.getDescent()) / 2;
                    g2.setColor(Color.GRAY);
                    g2.drawString(txt, tx, ty);
                } finally {
                    g2.dispose();
                }
            }
        };

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new SessionAwareRenderer());

        // Lazy-load children on expand
        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                loadChildrenIfNeeded(node);
            }
            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {}
        });

        // Selection -> update model selected file + active session
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selected =
                    (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selected == null) { model.setSelectedFile(null); return; }

            String sessionId = findSessionId(selected);
            if (sessionId != null) model.setActiveSession(sessionId);

            if (selected.getUserObject() instanceof FileNode fileNode) {
                model.setSelectedFile(fileNode);
            } else {
                model.setSelectedFile(null);
            }
        });

        // Track hovered row to show/hide the close button.
        // getRowForLocation only hits the icon+text cell bounds, so hovering in
        // the empty space to the right of the label returns -1 and hides the ✕.
        // getClosestRowForLocation + explicit y-bounds check covers the full row.
        tree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int newHovered = -1;
                int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                if (row >= 0) {
                    Rectangle rb = tree.getRowBounds(row);
                    if (rb != null && e.getY() >= rb.y && e.getY() < rb.y + rb.height) {
                        newHovered = row;
                    }
                }
                if (newHovered != hoveredRow) {
                    hoveredRow = newHovered;
                    tree.repaint();
                }
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                tree.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseEvent(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseEvent(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node =
                                (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof FileNode fn && !fn.isDirectory()) {
                            model.setSelectedFile(fn);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(sectionHeader, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        model.addPropertyChangeListener(this::onModelChange);
    }

    // -- Model listener ---------------------------------------------------------

    private void onModelChange(PropertyChangeEvent evt) {
        SwingUtils.onEdt(() -> {
            switch (evt.getPropertyName()) {
                case ArchiveModel.PROP_SESSION_ADDED -> {
                    if (evt.getNewValue() instanceof MountSession s) addSessionNode(s);
                }
                case ArchiveModel.PROP_SESSION_REMOVED -> {
                    if (evt.getNewValue() instanceof String id) removeSessionNode(id);
                }
                case ArchiveModel.PROP_TREE_REFRESH -> refreshAllSessions();
                case ArchiveModel.PROP_OPEN -> {
                    if (Boolean.FALSE.equals(evt.getNewValue())) clearAllNodes();
                }
            }
        });
    }

    // -- Session node management ------------------------------------------------

    private void addSessionNode(MountSession session) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new SessionNodeData(session.getId(), session.getLabel()));
        sessionNodes.put(session.getId(), node);
        hiddenRoot.add(node);
        treeModel.reload(hiddenRoot);

        loadChildren(node, session.getRootPath(), session.getId());
        treeModel.reload(node);

        TreePath tp = new TreePath(node.getPath());
        tree.expandPath(tp);
    }

    private void removeSessionNode(String sessionId) {
        DefaultMutableTreeNode node = sessionNodes.remove(sessionId);
        if (node != null) treeModel.removeNodeFromParent(node);
    }

    private void clearAllNodes() {
        sessionNodes.clear();
        hiddenRoot.removeAllChildren();
        treeModel.reload();
    }

    private void refreshAllSessions() {
        for (MountSession session : model.getSessions()) {
            DefaultMutableTreeNode node = sessionNodes.get(session.getId());
            if (node != null) {
                node.removeAllChildren();
                loadChildren(node, session.getRootPath(), session.getId());
                treeModel.reload(node);
            }
        }
    }

    // -- Child loading ----------------------------------------------------------

    private void loadChildren(DefaultMutableTreeNode parentNode, Path directory, String sessionId) {
        try {
            List<FileNode> children = model.listChildrenForSession(sessionId, directory);
            for (FileNode child : children) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                if (child.isDirectory()) {
                    childNode.add(new DefaultMutableTreeNode("Loading..."));
                }
                parentNode.add(childNode);
            }
        } catch (IOException ex) {
            parentNode.add(new DefaultMutableTreeNode("Error: " + ex.getMessage()));
        }
    }

    private void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
        if (node.getChildCount() != 1) return;
        DefaultMutableTreeNode first = (DefaultMutableTreeNode) node.getFirstChild();
        if (!(first.getUserObject() instanceof String s) || !s.startsWith("Loading")) return;

        node.removeAllChildren();
        String sessionId = findSessionId(node);
        if (node.getUserObject() instanceof FileNode fn && fn.isDirectory()) {
            loadChildren(node, fn.getPath(), sessionId);
        } else if (node.getUserObject() instanceof SessionNodeData sd) {
            MountSession session = findSession(sd.sessionId());
            if (session != null) loadChildren(node, session.getRootPath(), sd.sessionId());
        }
        treeModel.reload(node);
    }

    // -- Mouse event handling ---------------------------------------------------

    private void handleMouseEvent(MouseEvent e) {
        if (e.isPopupTrigger()) {
            TreePath path = tree.getPathForLocation(e.getX(), e.getY());
            if (path != null) tree.setSelectionPath(path);
            showContextMenu(e);
            return;
        }

        // Close-button: left-click in rightmost CLOSE_BTN_WIDTH px of ANY session row.
        // Use getClosestRowForLocation + explicit y-bounds check so that clicks in the
        // empty area to the right of the cell text (outside getRowForLocation's hit box)
        // are still detected.
        if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
            Rectangle vis = tree.getVisibleRect();
            if (e.getX() >= vis.x + vis.width - CLOSE_BTN_WIDTH) {
                int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                if (row >= 0) {
                    Rectangle rb = tree.getRowBounds(row);
                    if (rb != null && e.getY() >= rb.y && e.getY() < rb.y + rb.height) {
                        TreePath path = tree.getPathForRow(row);
                        if (path != null) {
                            DefaultMutableTreeNode node =
                                    (DefaultMutableTreeNode) path.getLastPathComponent();
                            if (node.getUserObject() instanceof SessionNodeData sd) {
                                confirmAndClose(sd.sessionId());
                            }
                        }
                    }
                }
            }
        }
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        DefaultMutableTreeNode node = (path != null)
                ? (DefaultMutableTreeNode) path.getLastPathComponent() : null;

        JPopupMenu menu = new JPopupMenu();
        boolean isSessionNode = node != null && node.getUserObject() instanceof SessionNodeData;

        if (isSessionNode) {
            SessionNodeData sd = (SessionNodeData) node.getUserObject();

            JMenuItem unmount = new JMenuItem("Unmount");
            unmount.addActionListener(ev -> confirmAndClose(sd.sessionId()));
            menu.add(unmount);

            MountSession session = model.getSession(sd.sessionId());
            if (session != null && session.isSaveable()) {
                JMenuItem save = new JMenuItem("Save");
                save.addActionListener(ev -> controller.saveSession(sd.sessionId()));
                menu.add(save);
            }
            menu.addSeparator();
        }

        JMenuItem newFile = new JMenuItem("New File...");
        newFile.addActionListener(ev -> controller.newFile());
        newFile.setEnabled(model.isOpen() && !model.isReadOnly());

        JMenuItem newDir = new JMenuItem("New Directory...");
        newDir.addActionListener(ev -> controller.newDirectory());
        newDir.setEnabled(model.isOpen() && !model.isReadOnly());

        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(ev -> controller.copySelected());
        copy.setEnabled(model.getSelectedFile() != null);

        JMenuItem cut = new JMenuItem("Cut");
        cut.addActionListener(ev -> controller.cutSelected());
        cut.setEnabled(model.getSelectedFile() != null && !model.isReadOnly());

        JMenuItem paste = new JMenuItem("Paste");
        paste.addActionListener(ev -> controller.pasteSelected());
        paste.setEnabled(controller.hasClipboard() && model.isOpen() && !model.isReadOnly());

        JMenuItem rename = new JMenuItem("Rename...");
        rename.addActionListener(ev -> controller.renameSelected());
        rename.setEnabled(model.getSelectedFile() != null && !model.isReadOnly());

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(ev -> controller.deleteSelected());
        delete.setEnabled(model.getSelectedFile() != null && !model.isReadOnly());

        FileNode sel = model.getSelectedFile();

        JMenuItem saveAs = new JMenuItem("Save As...");
        saveAs.addActionListener(ev -> controller.saveAs());
        saveAs.setEnabled(sel != null && !sel.isDirectory());

        JMenuItem extract = new JMenuItem("Extract To...");
        extract.addActionListener(ev -> controller.extractSelected());
        extract.setEnabled(sel != null && !sel.isDirectory());

        JMenuItem properties = new JMenuItem("Properties...");
        properties.addActionListener(ev -> showProperties());
        properties.setEnabled(model.getSelectedFile() != null);

        menu.add(newFile);
        menu.add(newDir);
        menu.addSeparator();
        menu.add(copy);
        menu.add(cut);
        menu.add(paste);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
        menu.addSeparator();
        menu.add(saveAs);
        menu.add(extract);
        menu.addSeparator();
        menu.add(properties);

        // For non-session nodes, append an "Unmount" option so users can unmount
        // the containing session from anywhere in the tree (not just the root node).
        if (!isSessionNode && node != null) {
            String sessId = findSessionId(node);
            if (sessId != null) {
                MountSession sess = model.getSession(sessId);
                String label = sess != null ? "Unmount '" + sess.getLabel() + "'" : "Unmount";
                menu.addSeparator();
                JMenuItem unmount = new JMenuItem(label);
                unmount.addActionListener(ev -> confirmAndClose(sessId));
                menu.add(unmount);
            }
        }

        menu.show(tree, e.getX(), e.getY());
    }

    // -- Helpers ----------------------------------------------------------------

    /**
     * Prompts the user to save unsaved changes before unmounting a writable archive session.
     * For read-only, directories, or NFS sessions (write-through) it closes immediately.
     */
    private void confirmAndClose(String sessionId) {
        MountSession session = model.getSession(sessionId);
        if (session != null && session.isSaveable()) {
            Window ancestor = SwingUtilities.getWindowAncestor(this);
            int choice = JOptionPane.showConfirmDialog(
                    ancestor,
                    "Save changes to '" + session.getLabel() + "' before unmounting?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION) return;
            if (choice == JOptionPane.YES_OPTION) controller.saveSession(sessionId);
        }
        controller.closeSession(sessionId);
    }

    private String findSessionId(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode current = node;
        while (current != null) {
            if (current.getUserObject() instanceof SessionNodeData sd) return sd.sessionId();
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return null;
    }

    private MountSession findSession(String id) {
        for (MountSession s : model.getSessions()) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }

    private void showProperties() {
        FileNode sel = model.getSelectedFile();
        if (sel == null) return;
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        Frame frame = (ancestor instanceof Frame f) ? f : null;
        new PropertiesDialog(frame,
                sel.getDisplayName(),
                sel.getPath().toString(),
                FileTypeDetector.getMimeDescription(sel.getExtension()),
                SwingUtils.formatFileSize(sel.getSize()),
                sel.isDirectory()).setVisible(true);
    }

    // -- Inner types ------------------------------------------------------------

    /** User-object placed on session (root-level) tree nodes. */
    private record SessionNodeData(String sessionId, String label) {}

    /**
     * Cell renderer that:
     * - Shows session nodes with a bold label and archive icon
     * - Delegates file nodes to {@link FileTreeCellRenderer}
     * The hover-close button is NOT drawn here; it is drawn in paintComponent
     * of the tree itself to avoid row-sizing and clipping complications.
     */
    private final class SessionAwareRenderer extends DefaultTreeCellRenderer {

        private final FileTreeCellRenderer fileRenderer = new FileTreeCellRenderer();
        private final Icon archiveIcon = IconFactory.archiveIcon();

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {

            if (!(value instanceof DefaultMutableTreeNode node)) {
                return super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, focused);
            }

            Object userObj = node.getUserObject();

            if (userObj instanceof SessionNodeData sd) {
                JPanel panel = new JPanel(new BorderLayout(4, 0));

                super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, focused);
                if (selected) panel.setBackground(getBackgroundSelectionColor());
                else          panel.setBackground(getBackgroundNonSelectionColor());
                panel.setOpaque(true);

                JLabel lbl = new JLabel(sd.label(), archiveIcon, SwingConstants.LEFT);
                lbl.setForeground(selected ? getTextSelectionColor() : getTextNonSelectionColor());
                lbl.setFont(getFont().deriveFont(Font.BOLD));
                panel.add(lbl, BorderLayout.CENTER);

                return panel;
            }

            return fileRenderer.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, focused);
        }
    }
}
