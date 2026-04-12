package io.wfs.ui.view;

import io.wfs.ui.model.FileNode;
import io.wfs.ui.util.FileTypeDetector;
import io.wfs.ui.util.IconFactory;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Custom tree cell renderer that displays file/folder icons
 * based on the entry type. Follows the Strategy pattern — icon
 * selection is based on file type detection.
 */
public final class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    private final Icon folderIcon = IconFactory.folderIcon();
    private final Icon folderOpenIcon = IconFactory.folderOpenIcon();
    private final Icon textIcon = IconFactory.textFileIcon();
    private final Icon imageIcon = IconFactory.imageFileIcon();
    private final Icon binaryIcon = IconFactory.binaryFileIcon();
    private final Icon genericFileIcon = IconFactory.fileIcon();
    private final Icon archiveRootIcon = IconFactory.archiveIcon();

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode node) {
            Object userObj = node.getUserObject();

            if (userObj instanceof FileNode fileNode) {
                setText(fileNode.getDisplayName());
                setIcon(chooseIcon(fileNode, expanded));
            } else if (userObj instanceof String label) {
                setText(label);
                setIcon(archiveRootIcon);
            }
        }

        return this;
    }

    private Icon chooseIcon(FileNode fileNode, boolean expanded) {
        if (fileNode.isDirectory()) {
            return expanded ? folderOpenIcon : folderIcon;
        }
        String ext = fileNode.getExtension();
        FileTypeDetector.FileType type = FileTypeDetector.detect(ext);
        return switch (type) {
            case TEXT -> textIcon;
            case IMAGE -> imageIcon;
            case BINARY -> binaryIcon;
            case UNKNOWN -> genericFileIcon;
        };
    }
}
