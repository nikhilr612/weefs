package io.wfs.ui.util;

import javax.swing.*;
import java.awt.Component;

public final class UIUtils {
    private UIUtils() {}

    public static void showError(Component parent, String title, Exception ex) {
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(parent,
                title + " failed:\n" + ex.getMessage(),
                title, JOptionPane.ERROR_MESSAGE));
    }
}
