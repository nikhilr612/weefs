package io.wfs.ui.view;

import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;
import io.wfs.ui.util.FileTypeDetector;
import io.wfs.ui.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;

/**
 * Status bar at the bottom of the main frame.
 * Shows archive state, selected file info, and read-only indicator.
 */
public final class StatusBarPanel extends JPanel {

    private final JLabel archiveLabel;
    private final JLabel fileInfoLabel;
    private final JLabel modeLabel;
    private boolean currentReadOnly;

    public StatusBarPanel(ArchiveModel model) {
        setLayout(new BorderLayout());
        Color sepColor = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, sepColor != null ? sepColor : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        archiveLabel = new JLabel("No archive open");

        fileInfoLabel = new JLabel(" ");
        fileInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        modeLabel = new JLabel(" ");
        modeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        add(archiveLabel, BorderLayout.WEST);
        add(fileInfoLabel, BorderLayout.CENTER);
        add(modeLabel, BorderLayout.EAST);

        model.addPropertyChangeListener(this::onModelChange);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (modeLabel != null) {
            updateModeLabel(currentReadOnly);
        }
    }

    /** Applies the mode text and a theme-aware foreground to {@code modeLabel}. */
    private void updateModeLabel(boolean ro) {
        currentReadOnly = ro;
        modeLabel.setText(ro ? "READ ONLY" : "READ/WRITE");
        Color roColor = UIManager.getColor("Component.error.focusedBorderColor");
        Color rwColor = UIManager.getColor("Component.focusedBorderColor");
        modeLabel.setForeground(ro
                ? (roColor != null ? roColor : new Color(180, 60, 60))
                : (rwColor != null ? rwColor : new Color(60, 130, 60)));
    }

    private void onModelChange(PropertyChangeEvent evt) {
        SwingUtils.onEdt(() -> {
            switch (evt.getPropertyName()) {
                case ArchiveModel.PROP_ARCHIVE_PATH -> {
                    Object newVal = evt.getNewValue();
                    if (newVal != null) {
                        archiveLabel.setText("Archive: " + newVal);
                    } else {
                        archiveLabel.setText("No archive open");
                    }
                }
                case ArchiveModel.PROP_READ_ONLY -> {
                    boolean ro = Boolean.TRUE.equals(evt.getNewValue());
                    updateModeLabel(ro);
                }
                case ArchiveModel.PROP_SELECTED_FILE -> {
                    Object sel = evt.getNewValue();
                    if (sel instanceof FileNode fn) {
                        String info = fn.getDisplayName();
                        if (!fn.isDirectory()) {
                            info += "  ·  " + SwingUtils.formatFileSize(fn.getSize())
                                    + "  ·  " + FileTypeDetector.getMimeDescription(fn.getExtension());
                        } else {
                            info += "  ·  Directory";
                        }
                        fileInfoLabel.setText(info);
                    } else {
                        fileInfoLabel.setText(" ");
                    }
                }
                case ArchiveModel.PROP_OPEN -> {
                    if (Boolean.FALSE.equals(evt.getNewValue())) {
                        archiveLabel.setText("No archive open");
                        fileInfoLabel.setText(" ");
                        modeLabel.setText(" ");
                    }
                }
            }
        });
    }
}
