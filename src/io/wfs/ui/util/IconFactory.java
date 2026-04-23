package io.wfs.ui.util;

import javax.swing.*;
import java.awt.*;

/**
 * Utility factory for generating simple icons used in the tree view
 * and toolbar. Draws icons programmatically to avoid external resource
 * dependencies (Factory pattern).
 */
public final class IconFactory {

    private static final int ICON_SIZE = 18;

    private IconFactory() {
    }

    public static Icon folderIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(new Color(255, 200, 60));
            // Folder tab
            g.fillRoundRect(1, 2, 6, 3, 2, 2);
            // Folder body
            g.fillRoundRect(1, 4, w - 2, h - 6, 3, 3);
            g.setColor(new Color(210, 160, 30));
            g.drawRoundRect(1, 4, w - 2, h - 6, 3, 3);
        });
    }

    public static Icon folderOpenIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(new Color(255, 220, 100));
            g.fillRoundRect(1, 2, 6, 3, 2, 2);
            g.fillRoundRect(1, 4, w - 2, h - 6, 3, 3);
            g.setColor(new Color(210, 160, 30));
            g.drawRoundRect(1, 4, w - 2, h - 6, 3, 3);
            // Open flap
            g.setColor(new Color(255, 230, 140));
            g.fillRect(3, 5, w - 5, 3);
        });
    }

    public static Icon fileIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(Color.WHITE);
            g.fillRect(3, 1, w - 6, h - 2);
            g.setColor(new Color(100, 100, 100));
            g.drawRect(3, 1, w - 6, h - 2);
            // Lines
            g.setColor(new Color(180, 180, 180));
            g.drawLine(5, 5, w - 5, 5);
            g.drawLine(5, 8, w - 5, 8);
            g.drawLine(5, 11, w - 7, 11);
        });
    }

    public static Icon textFileIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(Color.WHITE);
            g.fillRect(3, 1, w - 6, h - 2);
            g.setColor(new Color(70, 130, 180));
            g.drawRect(3, 1, w - 6, h - 2);
            g.setColor(new Color(70, 130, 180));
            g.drawLine(5, 5, w - 5, 5);
            g.drawLine(5, 8, w - 5, 8);
            g.drawLine(5, 11, w - 7, 11);
        });
    }

    public static Icon imageFileIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(Color.WHITE);
            g.fillRect(3, 1, w - 6, h - 2);
            g.setColor(new Color(60, 179, 113));
            g.drawRect(3, 1, w - 6, h - 2);
            // Mountain icon
            g.fillPolygon(new int[] { 5, 8, 11 }, new int[] { 12, 6, 12 }, 3);
        });
    }

    public static Icon binaryFileIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(new Color(220, 220, 220));
            g.fillRect(3, 1, w - 6, h - 2);
            g.setColor(new Color(150, 80, 80));
            g.drawRect(3, 1, w - 6, h - 2);
            // Binary dots
            g.fillOval(5, 5, 3, 3);
            g.fillOval(9, 9, 3, 3);
        });
    }

    public static Icon archiveIcon() {
        return new PaintedIcon(ICON_SIZE, ICON_SIZE, (g, w, h) -> {
            g.setColor(new Color(180, 140, 100));
            g.fillRoundRect(2, 1, w - 4, h - 2, 3, 3);
            g.setColor(new Color(140, 100, 60));
            g.drawRoundRect(2, 1, w - 4, h - 2, 3, 3);
            // Zipper
            g.setColor(new Color(200, 200, 200));
            g.drawLine(w / 2, 2, w / 2, h - 2);
        });
    }

    /** Reusable painted icon backed by a lambda renderer. */
    @FunctionalInterface
    interface IconPainter {
        void paint(Graphics2D g, int width, int height);
    }

    private static final class PaintedIcon implements Icon {
        private final int width;
        private final int height;
        private final IconPainter painter;

        PaintedIcon(int width, int height, IconPainter painter) {
            this.width = width;
            this.height = height;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create(x, y, width, height);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.paint(g2, width, height);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
