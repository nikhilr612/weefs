package io.wfs.ui.view;

import io.wfs.ui.controller.IArchiveController;
import io.wfs.ui.controller.INfsController;
import io.wfs.ui.model.ArchiveModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Factory for creating the application menu bar.
 * Uses the Factory pattern to centralize menu construction.
 * Supports both archive and NFS operations.
 */
public final class MenuBarFactory {

    private MenuBarFactory() {
    }

    public static JMenuBar create(IArchiveController controller, ArchiveModel model) {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(createFileMenu(controller, model));
        menuBar.add(createNfsMenu(controller, model));
        menuBar.add(createEditMenu(controller, model));
        menuBar.add(createViewMenu(model));
        menuBar.add(createHelpMenu(controller));

        return menuBar;
    }

    private static JMenu createFileMenu(IArchiveController controller, ArchiveModel model) {
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newArchive = new JMenuItem("New Archive...");
        newArchive.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        newArchive.addActionListener(e -> controller.createArchive());

        JMenuItem open = new JMenuItem("Open Archive...");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        open.addActionListener(e -> controller.openArchive());

        JMenuItem mountNfs = new JMenuItem("Mount NFS...");
        mountNfs.setAccelerator(
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        mountNfs.addActionListener(e -> controller.mountNfs());

        JMenuItem close = new JMenuItem("Close Archive");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
        close.addActionListener(e -> controller.closeArchive());

        JMenuItem save = new JMenuItem("Save Archive");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        save.addActionListener(e -> controller.saveArchive());

        JMenuItem exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exit.addActionListener(e -> {
            if (model.isOpen() && !model.isReadOnly()) {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                java.awt.Component parent = null;
                for (java.awt.Window w : windows) {
                    if (w instanceof JFrame) {
                        parent = w;
                        break;
                    }
                }
                int choice = JOptionPane.showConfirmDialog(parent,
                        "Save changes before exiting?",
                        "Unsaved Changes",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (choice == JOptionPane.CANCEL_OPTION) {
                    return;
                }
            }
            try {
                if (model.isOpen()) {
                    model.closeArchive();
                }
            } catch (Exception ignored) {
            }
            System.exit(0);
        });

        menu.add(newArchive);
        menu.add(open);
        menu.add(mountNfs);
        menu.addSeparator();
        menu.add(close);
        menu.add(save);
        menu.addSeparator();
        menu.add(exit);

        // Enable/disable based on open state
        model.addPropertyChangeListener(ArchiveModel.PROP_OPEN, evt -> {
            boolean isOpen = Boolean.TRUE.equals(evt.getNewValue());
            SwingUtilities.invokeLater(() -> {
                close.setEnabled(isOpen);
                save.setEnabled(isOpen && !model.isReadOnly());
            });
        });
        close.setEnabled(false);
        save.setEnabled(false);

        return menu;
    }

    private static JMenu createNfsMenu(IArchiveController controller, ArchiveModel model) {
        JMenu menu = new JMenu("NFS");
        menu.setMnemonic(KeyEvent.VK_N);

        INfsController nfsController = (INfsController) controller;

        JMenuItem mountNfs = new JMenuItem("Mount NFS...");
        mountNfs.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        mountNfs.addActionListener(e -> nfsController.mountNfs());

        JMenuItem unmountNfs = new JMenuItem("Unmount NFS");
        unmountNfs.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        unmountNfs.addActionListener(e -> nfsController.unmountNfs());
        unmountNfs.setEnabled(false);

        JMenuItem extractNfs = new JMenuItem("Extract NFS File...");
        extractNfs.addActionListener(e -> nfsController.extractNfsSelected());
        extractNfs.setEnabled(false);

        menu.add(mountNfs);
        menu.add(unmountNfs);
        menu.addSeparator();
        menu.add(extractNfs);

        // Update enablement based on NFS mount state
        model.addPropertyChangeListener(ArchiveModel.PROP_NFS_CONFIG, evt -> {
            boolean isMounted = nfsController.isNfsMounted();
            unmountNfs.setEnabled(isMounted);
            extractNfs
                    .setEnabled(isMounted && model.getSelectedFile() != null && !model.getSelectedFile().isDirectory());
        });

        model.addPropertyChangeListener(ArchiveModel.PROP_SELECTED_FILE, evt -> {
            boolean canExtract = nfsController.isNfsMounted() && model.getSelectedFile() != null
                    && !model.getSelectedFile().isDirectory();
            extractNfs.setEnabled(canExtract);
        });

        return menu;
    }

    private static JMenu createEditMenu(IArchiveController controller, ArchiveModel model) {
        JMenu menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        JMenuItem newFile = new JMenuItem("New File...");
        newFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newFile.addActionListener(e -> controller.newFile());

        JMenuItem newDir = new JMenuItem("New Directory...");
        newDir.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        newDir.addActionListener(e -> controller.newDirectory());

        JMenuItem rename = new JMenuItem("Rename...");
        rename.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        rename.addActionListener(e -> controller.renameSelected());

        JMenuItem delete = new JMenuItem("Delete");
        delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        delete.addActionListener(e -> controller.deleteSelected());

        JMenuItem extract = new JMenuItem("Extract To...");
        extract.addActionListener(e -> controller.extractSelected());

        menu.add(newFile);
        menu.add(newDir);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
        menu.addSeparator();
        menu.add(extract);

        // Enable/disable
        Runnable updateEditItems = () -> {
            boolean canEdit = model.isOpen() && !model.isReadOnly();
            newFile.setEnabled(canEdit);
            newDir.setEnabled(canEdit);
            rename.setEnabled(canEdit && model.getSelectedFile() != null);
            delete.setEnabled(canEdit && model.getSelectedFile() != null);
            extract.setEnabled(model.isOpen() && model.getSelectedFile() != null
                    && !model.getSelectedFile().isDirectory());
        };
        model.addPropertyChangeListener(evt -> SwingUtilities.invokeLater(updateEditItems));
        updateEditItems.run();

        return menu;
    }

    private static JMenu createViewMenu(ArchiveModel model) {
        JMenu menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);

        JMenuItem refresh = new JMenuItem("Refresh Tree");
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refresh.addActionListener(e -> model.fireTreeRefresh());

        menu.add(refresh);

        return menu;
    }

    private static JMenu createHelpMenu(IArchiveController controller) {
        JMenu menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);

        JMenuItem about = new JMenuItem("About weefs");
        about.addActionListener(e -> {
            java.awt.Component parent = null;
            if (controller.getModel() != null) {
                // Find the top-level frame
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof javax.swing.JFrame) {
                        parent = w;
                        break;
                    }
                }
            }
            new io.wfs.ui.view.dialog.AboutDialog(
                    parent instanceof java.awt.Frame ? (java.awt.Frame) parent : null).setVisible(true);
        });

        menu.add(about);

        return menu;
    }
}
