package io.wfs.ui.view;

import io.wfs.ui.controller.IArchiveController;
import io.wfs.ui.controller.INfsController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.util.IconFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Factory for creating the application toolbar.
 * Uses the Factory pattern to centralize toolbar construction.
 * Includes both archive and NFS operation buttons.
 */
public final class ToolBarFactory {

    private ToolBarFactory() {
    }

    public static JToolBar create(IArchiveController controller, ArchiveModel model) {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);
        toolBar.setBorderPainted(true);

        final INfsController nfsController = (controller instanceof INfsController)
                ? (INfsController) controller : null;

        // Archive operations
        JButton openBtn = makeButton("Open", IconFactory.archiveIcon(), "Open an archive (Ctrl+O)");
        openBtn.addActionListener(e -> controller.openArchive());

        JButton newArchiveBtn = makeButton("New", IconFactory.archiveIcon(), "Create a new archive (Ctrl+Shift+N)");
        newArchiveBtn.addActionListener(e -> controller.createArchive());

        JButton closeBtn = makeButton("Close", null, "Close the current archive");
        closeBtn.addActionListener(e -> controller.closeArchive());
        closeBtn.setEnabled(false);

        toolBar.add(openBtn);
        toolBar.add(newArchiveBtn);
        toolBar.add(closeBtn);
        toolBar.addSeparator();

        // NFS operations
        JButton mountNfsBtn = makeButton("Mount NFS", IconFactory.folderIcon(), "Mount NFS share (Ctrl+Shift+M)");
        mountNfsBtn.addActionListener(e -> { if (nfsController != null) nfsController.mountNfs(); });
        mountNfsBtn.setEnabled(nfsController != null);

        JButton unmountNfsBtn = makeButton("Unmount NFS", null, "Unmount NFS share (Ctrl+Shift+U)");
        unmountNfsBtn.addActionListener(e -> { if (nfsController != null) nfsController.unmountNfs(); });
        unmountNfsBtn.setEnabled(false);

        toolBar.add(mountNfsBtn);
        toolBar.add(unmountNfsBtn);
        toolBar.addSeparator();

        // File operations
        JButton newFileBtn = makeButton("New File", IconFactory.textFileIcon(), "Create a new file");
        newFileBtn.addActionListener(e -> controller.newFile());
        newFileBtn.setEnabled(false);

        JButton newDirBtn = makeButton("New Dir", IconFactory.folderIcon(), "Create a new directory");
        newDirBtn.addActionListener(e -> controller.newDirectory());
        newDirBtn.setEnabled(false);

        JButton deleteBtn = makeButton("Delete", null, "Delete selected item");
        deleteBtn.addActionListener(e -> controller.deleteSelected());
        deleteBtn.setEnabled(false);

        JButton extractBtn = makeButton("Extract", null, "Extract selected file to disk");
        extractBtn.addActionListener(e -> controller.extractSelected());
        extractBtn.setEnabled(false);

        JButton extractNfsBtn = makeButton("Extract NFS", null, "Extract NFS file to disk");
        extractNfsBtn.addActionListener(e -> { if (nfsController != null) nfsController.extractNfsSelected(); });
        extractNfsBtn.setEnabled(false);

        toolBar.add(newFileBtn);
        toolBar.add(newDirBtn);
        toolBar.addSeparator();
        toolBar.add(deleteBtn);
        toolBar.add(extractBtn);
        toolBar.add(extractNfsBtn);
        toolBar.addSeparator();

        // Refresh
        JButton refreshBtn = makeButton("Refresh", null, "Refresh the tree (F5)");
        refreshBtn.addActionListener(e -> model.fireTreeRefresh());
        refreshBtn.setEnabled(false);
        toolBar.add(refreshBtn);

        // Update enablement based on model state
        model.addPropertyChangeListener(evt -> SwingUtilities.invokeLater(() -> {
            boolean isOpen = model.isOpen();
            boolean canEdit = isOpen && !model.isReadOnly();
            var selectedFile = model.getSelectedFile();
            boolean hasSel = selectedFile != null;
            boolean isDirectory = hasSel && selectedFile.isDirectory();
            boolean nfsMounted = nfsController != null && nfsController.isNfsMounted();

            closeBtn.setEnabled(isOpen);
            mountNfsBtn.setEnabled(nfsController != null && !nfsMounted);
            unmountNfsBtn.setEnabled(nfsMounted);
            newFileBtn.setEnabled(canEdit);
            newDirBtn.setEnabled(canEdit);
            deleteBtn.setEnabled(canEdit && hasSel);
            extractBtn.setEnabled(isOpen && hasSel && !isDirectory);
            extractNfsBtn.setEnabled(nfsMounted && hasSel && !isDirectory);
            refreshBtn.setEnabled(isOpen);
        }));

        return toolBar;
    }

    private static JButton makeButton(String text, Icon icon, String tooltip) {
        JButton btn = new JButton(text);
        if (icon != null) {
            btn.setIcon(icon);
        }
        btn.setToolTipText(tooltip);
        btn.putClientProperty("JButton.buttonType", "toolBarButton");
        return btn;
    }
}
