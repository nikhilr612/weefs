package io.wfs.ui.util;

import javax.swing.*;
import java.awt.*;

/**
 * Common Swing helper methods.
 */
public final class SwingUtils {

    private SwingUtils() {
    }

    /**
     * Sets the system look-and-feel, falling back to cross-platform if unavailable.
     */
    public static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fallback to default
        }
    }

    /**
     * Formats a file size into human-readable form.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Centers a window on the screen.
     */
    public static void centerOnScreen(Window window) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screen.width - window.getWidth()) / 2;
        int y = (screen.height - window.getHeight()) / 2;
        window.setLocation(x, y);
    }

    /**
     * Creates a JPanel with a FlowLayout aligned left with no vertical gap.
     */
    public static JPanel flowPanel(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component c : components) {
            panel.add(c);
        }
        return panel;
    }

    /**
     * Runs the given action on the EDT, immediately if already on it.
     */
    public static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
