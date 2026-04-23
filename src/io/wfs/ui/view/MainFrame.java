package io.wfs.ui.view;

import io.wfs.ui.controller.ArchiveController;
import io.wfs.ui.controller.IArchiveController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.MountSession;
import io.wfs.ui.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Main application window. Composes all view panels and wires them
 * to the shared ArchiveModel and ArchiveController (Mediator pattern).
 */
public final class MainFrame extends JFrame {

    private static final String TITLE = "weefs Archive Explorer";
    private static final int DEFAULT_WIDTH = 1100;
    private static final int DEFAULT_HEIGHT = 700;

    private final ArchiveModel model;
    private final IArchiveController controller;

    public MainFrame(ArchiveModel model, IArchiveController controller) {
        super(TITLE);
        this.model = model;
        this.controller = controller;

        controller.setParentComponent(this);
        initUI();
        registerCloseHandler();
        updateTitle();

        model.addPropertyChangeListener(ArchiveModel.PROP_ARCHIVE_PATH, evt -> updateTitle());
        model.addPropertyChangeListener(ArchiveModel.PROP_OPEN, evt -> updateTitle());
    }

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(600, 400));
        setLayout(new BorderLayout());

        // Menu bar
        setJMenuBar(MenuBarFactory.create(controller, model));

        // Toolbar
        JToolBar toolBar = ToolBarFactory.create(controller, model);
        add(toolBar, BorderLayout.NORTH);

        // Main content: tree on the left, content viewer on the right
        ArchiveTreePanel treePanel = new ArchiveTreePanel(model, controller);
        FileContentPanel contentPanel = new FileContentPanel(model, controller);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, contentPanel);
        splitPane.setDividerLocation(300);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);
        add(splitPane, BorderLayout.CENTER);

        // Status bar
        StatusBarPanel statusBar = new StatusBarPanel(model);
        add(statusBar, BorderLayout.SOUTH);

        SwingUtils.centerOnScreen(this);
    }

    private void registerCloseHandler() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });
    }

    private void handleExit() {
        // Only prompt for sessions that need explicit flushing (writable local archives).
        // Directories, NFS, and remote mounts are write-through; closing them is enough.
        boolean hasSaveable = model.isOpen() &&
                model.getSessions().stream().anyMatch(MountSession::isSaveable);
        if (hasSaveable) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Save changes to open archives before exiting?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (choice == JOptionPane.CANCEL_OPTION) return;
            // YES and NO both fall through to closeArchive() below.
            // closeSession() flushes the ZIP FileSystem to disk synchronously,
            // so no async race — saveSession is not needed here.
        }

        try {
            if (model.isOpen()) model.closeArchive();
        } catch (IOException ignored) {}

        dispose();
        System.exit(0);
    }

    private void updateTitle() {
        SwingUtils.onEdt(() -> {
            if (model.isOpen() && model.getArchivePath() != null) {
                String name = model.getArchivePath().getFileName().toString();
                String mode = model.isReadOnly() ? " [Read Only]" : "";
                setTitle(TITLE + " — " + name + mode);
            } else {
                setTitle(TITLE);
            }
        });
    }
}
