package io.wfs.ui.view.dialog;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for viewing file properties (path, size, type, etc.).
 */
public final class PropertiesDialog extends JDialog {

    public PropertiesDialog(Frame owner, String fileName, String path,
            String type, String size, boolean isDirectory) {
        super(owner, "Properties — " + fileName, true);
        initUI(fileName, path, type, size, isDirectory);
    }

    private void initUI(String fileName, String path, String type, String size, boolean isDirectory) {
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints lbl = new GridBagConstraints();
        lbl.anchor = GridBagConstraints.WEST;
        lbl.insets = new Insets(4, 4, 4, 10);
        lbl.gridx = 0;

        GridBagConstraints val = new GridBagConstraints();
        val.anchor = GridBagConstraints.WEST;
        val.insets = new Insets(4, 0, 4, 4);
        val.gridx = 1;
        val.fill = GridBagConstraints.HORIZONTAL;
        val.weightx = 1.0;

        int row = 0;
        addRow(grid, lbl, val, row++, "Name:", fileName);
        addRow(grid, lbl, val, row++, "Path:", path);
        addRow(grid, lbl, val, row++, "Type:", isDirectory ? "Directory" : type);
        if (!isDirectory) {
            addRow(grid, lbl, val, row++, "Size:", size);
        }

        JButton closeBtn = new JButton("OK");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);

        add(grid, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(350, 200));
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void addRow(JPanel panel, GridBagConstraints lbl,
            GridBagConstraints val, int row, String label, String value) {
        lbl.gridy = row;
        val.gridy = row;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        panel.add(l, lbl);
        panel.add(new JLabel(value), val);
    }
}
