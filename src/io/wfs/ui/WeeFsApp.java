package io.wfs.ui;

import io.wfs.ui.controller.ArchiveController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.util.SwingUtils;
import io.wfs.ui.view.MainFrame;

import javax.swing.*;

/**
 * Application entry point for the Swing UI.
 * Bootstraps the MVC triad and launches the main frame.
 * Singleton — only one instance per JVM.
 */
public final class WeeFsApp {

    private static WeeFsApp instance;

    private final ArchiveModel model;
    private final ArchiveController controller;
    private MainFrame mainFrame;

    private WeeFsApp() {
        this.model = new ArchiveModel();
        this.controller = new ArchiveController(model);
    }

    public static synchronized WeeFsApp getInstance() {
        if (instance == null) {
            instance = new WeeFsApp();
        }
        return instance;
    }

    public ArchiveModel getModel() {
        return model;
    }

    public ArchiveController getController() {
        return controller;
    }

    /**
     * Launches the UI on the Event Dispatch Thread.
     */
    public void launch() {
        SwingUtils.setSystemLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            mainFrame = new MainFrame(model, controller);
            mainFrame.setVisible(true);
        });
    }

    /**
     * Static convenience method to start the application.
     */
    public static void start() {
        getInstance().launch();
    }
}
