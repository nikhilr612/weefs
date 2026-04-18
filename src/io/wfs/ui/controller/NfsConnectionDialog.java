package io.wfs.ui.controller;

import io.wfs.core.nfs.NfsConnectionConfig;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for gathering NFS connection parameters from the user.
 * Prompts for host, port, export path, mount path, and read-only mode.
 * Factory pattern - creates NfsConnectionConfig objects.
 */
public final class NfsConnectionDialog extends JDialog {

    private final JTextField hostField = new JTextField("localhost", 15);
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(2049, 1, 65535, 1));
    private final JTextField exportPathField = new JTextField("/export", 20);
    private final JTextField mountPathField = new JTextField("/export", 20);
    private final JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 3600, 1));
    private final JCheckBox readOnlyCheckbox = new JCheckBox("Read-Only");
    private NfsConnectionConfig result;

    public NfsConnectionDialog(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent), "Mount NFS", ModalityType.APPLICATION_MODAL);
        initUI();
        setSize(400, 280);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Host
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(hostField, gbc);

        // Port
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(portSpinner, gbc);

        // Export Path
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Export Path:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(exportPathField, gbc);

        // Mount Path
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Mount Path:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(mountPathField, gbc);

        // Timeout
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Timeout (sec):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(timeoutSpinner, gbc);

        // Read-Only
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        panel.add(readOnlyCheckbox, gbc);

        // Buttons
        JButton okButton = new JButton("Mount");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            try {
                result = new NfsConnectionConfig(
                        hostField.getText().trim(),
                        (Integer) portSpinner.getValue(),
                        exportPathField.getText().trim(),
                        mountPathField.getText().trim(),
                        (Integer) timeoutSpinner.getValue(),
                        readOnlyCheckbox.isSelected()
                );
                dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            result = null;
            dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 5, 5, 5);
        panel.add(buttonPanel, gbc);

        add(panel);
    }

    /**
     * Shows the dialog and returns the user's NFS configuration.
     * @return NfsConnectionConfig if user clicked Mount, null if cancelled
     */
    public NfsConnectionConfig showDialog() {
        result = null;
        setVisible(true);
        return result;
    }
}
