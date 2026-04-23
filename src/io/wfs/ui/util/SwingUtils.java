package io.wfs.ui.util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Common Swing helper methods.
 */
public final class SwingUtils {

    private SwingUtils() {
    }

    /**
     * Installs FlatLaf Light as the application look-and-feel with polished
     * UIManager defaults. Falls back to the system L&amp;F if FlatLaf is unavailable.
     */
    public static void setSystemLookAndFeel() {
        if (!FlatLightLaf.setup()) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
        applyUiCustomisations();
    }

    /**
     * Returns {@code true} when the current L&amp;F is a FlatLaf dark variant.
     */
    public static boolean isDarkTheme() {
        return UIManager.getLookAndFeel() instanceof FlatDarkLaf;
    }

    /**
     * Toggles between FlatLaf Light and FlatLaf Dark and repaints all open windows.
     */
    public static void toggleTheme() {
        if (isDarkTheme()) {
            FlatLightLaf.setup();
        } else {
            FlatDarkLaf.setup();
        }
        applyUiCustomisations();
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }

    private static void applyUiCustomisations() {
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.trackInsets", new Insets(2, 4, 2, 4));
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("TitlePane.unifiedBackground", Boolean.TRUE);
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("Tree.rowHeight", 24);
        UIManager.put("Tree.paintLines", Boolean.FALSE);
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
