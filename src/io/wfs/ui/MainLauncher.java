package io.wfs.ui;

/**
 * Standalone launcher for the weefs Swing UI.
 * Run this class directly to start the archive explorer GUI.
 *
 * Usage:
 * java io.wfs.ui.MainLauncher
 *
 * Or via: java -jar artifact.jar gui (if wired through App.main)
 */
public final class MainLauncher {

    private MainLauncher() {
    }

    public static void main(String[] args) {
        WeeFsApp.start();
    }
}
