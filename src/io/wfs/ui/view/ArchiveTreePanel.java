package io.wfs.ui.view;

import io.wfs.ui.controller.IArchiveController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;
import io.wfs.ui.util.SwingUtils;
import io.wfs.ui.util.FileTypeDetector;
import io.wfs.ui.view.dialog.PropertiesDialog;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Left-side panel showing the archive contents as a tree.
 * Lazily loads child nodes on expansion (virtual tree pattern).
 * Listens to model property changes to keep itself synchronized.
 */
public final class ArchiveTreePanel extends JPanel {

    private final ArchiveModel model;
    private final IArchiveController controller;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    public ArchiveTreePanel(ArchiveModel model, IArchiveController controller) {
        this.model = model;
        this.controller = controller;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Archive Contents"));

        rootNode = new DefaultMutableTreeNode("(No archive open)");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setCellRenderer(new FileTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Lazy-load children when a node is expanded
        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                TreePath path = event.getPath();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                loadChildrenIfNeeded(node);
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {
                // No action needed
            }
        });

        // Selection listener — notify model of selected file
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selected = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selected != null && selected.getUserObject() instanceof FileNode fileNode) {
                model.setSelectedFile(fileNode);
            } else {
                model.setSelectedFile(null);
            }
        });

        // Context menu on right-click
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            // Double-click to expand/open
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof FileNode fn && !fn.isDirectory()) {
                            model.setSelectedFile(fn);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(280, 400));
        add(scrollPane, BorderLayout.CENTER);

        // Listen to model changes
        model.addPropertyChangeListener(this::onModelChange);
    }

    private void onModelChange(PropertyChangeEvent evt) {
        SwingUtils.onEdt(() -> {
            switch (evt.getPropertyName()) {
                case ArchiveModel.PROP_OPEN -> {
                    if (Boolean.TRUE.equals(evt.getNewValue())) {
                        rebuildTree();
                    } else {
                        clearTree();
                    }
                }
                case ArchiveModel.PROP_NFS_CONFIG -> {
                    if (evt.getNewValue() != null) {
                        rebuildTree();
                    } else {
                        clearTree();
                    }
                }
                case ArchiveModel.PROP_TREE_REFRESH -> rebuildTree();
            }
        });
    }

    private void rebuildTree() {
        rootNode.removeAllChildren();

        // Handle NFS case
        if (model.isNfsMounted()) {
            io.wfs.core.nfs.NfsConnectionConfig config = model.getNfsConfig();
            String label = config.getHost() + ":" + config.getPort() + config.getExportPath();
            if (model.isReadOnly()) {
                label += " [Read Only]";
            }
            rootNode.setUserObject(label);
            loadChildren(rootNode, Path.of("/"));
            treeModel.reload();
            tree.expandRow(0);
            return;
        }

        // Handle Archive case
        Path root = model.getRootPath();
        if (root == null) {
            rootNode.setUserObject("(No archive open)");
            treeModel.reload();
            return;
        }

        Path archivePath = model.getArchivePath();
        String label = (archivePath != null)
                ? archivePath.getFileName().toString()
                : "Archive";
        if (model.isReadOnly()) {
            label += " [Read Only]";
        }
        rootNode.setUserObject(label);

        loadChildren(rootNode, root);
        treeModel.reload();

        // Expand root
        tree.expandRow(0);
    }

    private void clearTree() {
        rootNode.removeAllChildren();
        rootNode.setUserObject("(No archive open)");
        treeModel.reload();
    }

    private void loadChildren(DefaultMutableTreeNode parentNode, Path directory) {
        try {
            List<FileNode> children = model.listChildren(directory);
            for (FileNode child : children) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                if (child.isDirectory()) {
                    // Add a placeholder so the expand icon appears
                    childNode.add(new DefaultMutableTreeNode("Loading..."));
                }
                parentNode.add(childNode);
            }
        } catch (IOException ex) {
            parentNode.add(new DefaultMutableTreeNode("Error: " + ex.getMessage()));
        }
    }

    private void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
        if (node.getChildCount() == 1) {
            DefaultMutableTreeNode firstChild = (DefaultMutableTreeNode) node.getFirstChild();
            if (firstChild.getUserObject() instanceof String s && s.startsWith("Loading")) {
                node.removeAllChildren();
                Object userObj = node.getUserObject();
                if (userObj instanceof FileNode fileNode && fileNode.isDirectory()) {
                    loadChildren(node, fileNode.getPath());
                }
                treeModel.reload(node);
            }
        }
    }

    private void handlePopup(MouseEvent e) {
        if (!e.isPopupTrigger())
            return;

        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            tree.setSelectionPath(path);
        }

        JPopupMenu popup = createContextMenu();
        popup.show(tree, e.getX(), e.getY());
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem newFile = new JMenuItem("New File...");
        newFile.addActionListener(e -> controller.newFile());
        newFile.setEnabled(model.isOpen() && !model.isReadOnly());

        JMenuItem newDir = new JMenuItem("New Directory...");
        newDir.addActionListener(e -> controller.newDirectory());
        newDir.setEnabled(model.isOpen() && !model.isReadOnly());

        JMenuItem rename = new JMenuItem("Rename...");
        rename.addActionListener(e -> controller.renameSelected());
        rename.setEnabled(model.getSelectedFile() != null && !model.isReadOnly());

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> controller.deleteSelected());
        delete.setEnabled(model.getSelectedFile() != null && !model.isReadOnly());

        JMenuItem extract = new JMenuItem("Extract To...");
        extract.addActionListener(e -> controller.extractSelected());
        FileNode sel = model.getSelectedFile();
        extract.setEnabled(sel != null && !sel.isDirectory());

        JMenuItem properties = new JMenuItem("Properties...");
        properties.addActionListener(e -> showProperties());
        properties.setEnabled(model.getSelectedFile() != null);

        menu.add(newFile);
        menu.add(newDir);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
        menu.addSeparator();
        menu.add(extract);
        menu.addSeparator();
        menu.add(properties);

        return menu;
    }

    private void showProperties() {
        FileNode sel = model.getSelectedFile();
        if (sel == null)
            return;

        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        new PropertiesDialog(frame,
                sel.getDisplayName(),
                sel.getPath().toString(),
                FileTypeDetector.getMimeDescription(sel.getExtension()),
                SwingUtils.formatFileSize(sel.getSize()),
                sel.isDirectory()).setVisible(true);
    }
}
