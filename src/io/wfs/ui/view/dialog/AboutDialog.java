package io.wfs.ui.view.dialog;

import javax.swing.*;
import java.awt.*;

/**
 * About dialog showing application information.
 */
public final class AboutDialog extends JDialog {

    public AboutDialog(Frame owner) {
        super(owner, "About weefs", true);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel title = new JLabel("weefs Archive Explorer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel version = new JLabel("Version 1.0");
        version.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel("<html><center>A Java NIO FileSystem-backed<br>"
                + "archive browser and editor.<br><br>"
                + "Supports ZIP and TAR archives.</center></html>");
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel license = new JLabel("MIT License");
        license.setForeground(Color.GRAY);
        license.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(version);
        content.add(Box.createVerticalStrut(12));
        content.add(desc);
        content.add(Box.createVerticalStrut(12));
        content.add(license);

        JButton closeBtn = new JButton("OK");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);

        add(content, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getOwner());
    }
}
