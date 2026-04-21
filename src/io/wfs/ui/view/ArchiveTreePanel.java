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
import javax.swing.plaf.LayerUI;
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
 * Session nodes show a hover-close (✕) overlay button and a right-click "Unmount" item.
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

    /** Row currently under the mouse (-1 = none). Used by the close-button overlay. */
    private int hoveredRow = -1;

    /** JLayer that paints the x close button as an overlay on the tree. */
    private JLayer<JTree> treeOverlay;

    public ArchiveTreePanel(ArchiveModel model, IArchiveController controller) {
        this.model = model;
        this.controller = controller;

        setLayout(new BorderLayout());

        JLabel sectionHeader = new JLabel("Mounted");
        sectionHeader.setFont(sectionHeader.getFont().deriveFont(Font.BOLD));
        sectionHeader.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

        hiddenRoot = new DefaultMutableTreeNode("__root__");
        treeModel = new DefaultTreeModel(hiddenRoot);
        tree = new JTree(treeModel);
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

        // Mouse: hover tracking, close-button click, double-click, popup
        tree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row != hoveredRow) {
                    hoveredRow = row;
                    if (treeOverlay != null) treeOverlay.repaint();
                }
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                if (treeOverlay != null) treeOverlay.repaint();
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

        // JLayer paints the close button as an overlay — never affects row preferred sizes
        treeOverlay = new JLayer<>(tree, new CloseButtonOverlay());

        JScrollPane scrollPane = new JScrollPane(treeOverlay);
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
        if (node != null) {
            treeModel.removeNodeFromParent(node);
        }
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
        // Close button: left-click in the rightmost CLOSE_BTN_WIDTH px of the tree row
        if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
            int row = tree.getRowForLocation(e.getX(), e.getY());
            if (row >= 0 && e.getX() >= tree.getWidth() - CLOSE_BTN_WIDTH) {
                TreePath path = tree.getPathForRow(row);
                if (path != null) {
                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof SessionNodeData sd) {
                        controller.closeSession(sd.sessionId());
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
            unmount.addActionListener(ev -> controller.closeSession(sd.sessionId()));
            menu.add(unmount);

            // Show "Save" only for writable archive sessions (zip/tar etc.)
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

        menu.show(tree, e.getX(), e.getY());
    }

    // -- Helpers ----------------------------------------------------------------

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
     * Paints the hover-close button as a JLayer overlay on the tree.
     * Using an overlay avoids stretching row preferred sizes (which caused the
     * close button to render off-screen when the renderer panel was made full-width).
     */
    private final class CloseButtonOverlay extends LayerUI<JTree> {
        @Override
        public void paint(Graphics g, JComponent c) {
            super.paint(g, c);

            if (hoveredRow < 0) return;
            TreePath path = tree.getPathForRow(hoveredRow);
            if (path == null) return;
            DefaultMutableTreeNode nd =
                    (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!(nd.getUserObject() instanceof SessionNodeData)) return;

            Rectangle rb = tree.getRowBounds(hoveredRow);
            if (rb == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(tree.getFont());
            FontMetrics fm = g2.getFontMetrics();
            String txt = "\u2715"; // ✕
            int tx = c.getWidth() - fm.stringWidth(txt) - 6;
            int ty = rb.y + (rb.height + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(Color.GRAY);
            g2.drawString(txt, tx, ty);
            g2.dispose();
        }
    }

    /**
     * Cell renderer that:
     * - Shows session nodes with a bold label and archive icon
     * - Delegates file nodes to {@link FileTreeCellRenderer}
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
