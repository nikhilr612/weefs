package io.wfs.ui.view;

import io.wfs.ui.controller.IArchiveController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;
import io.wfs.ui.util.FileTypeDetector;
import io.wfs.ui.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Right-side panel for viewing and editing file contents.
 * Swaps between text editor, hex viewer, image preview, and
 * empty placeholder based on the selected file type (Strategy pattern).
 */
public final class FileContentPanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_TEXT = "text";
    private static final String CARD_HEX = "hex";
    private static final String CARD_IMAGE = "image";
    private static final String CARD_DIR = "directory";

    private final ArchiveModel model;
    private final IArchiveController controller;

    private final CardLayout cardLayout;
    private final JTextArea textArea;
    private final JTextArea hexArea;
    private final JLabel imageLabel;
    private final JLabel directoryLabel;
    private final JLabel emptyLabel;
    private final JButton saveButton;
    private final JLabel fileNameLabel;

    private Path currentFilePath;

    public FileContentPanel(ArchiveModel model, IArchiveController controller) {
        this.model = model;
        this.controller = controller;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("File Viewer"));

        // ── Header bar ──
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        fileNameLabel = new JLabel(" ");
        fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD, 13f));
        header.add(fileNameLabel, BorderLayout.CENTER);

        saveButton = new JButton("Save");
        saveButton.setEnabled(false);
        saveButton.setToolTipText("Save changes to file");
        saveButton.addActionListener(e -> saveCurrentFile());
        header.add(saveButton, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // ── Card panel for different view modes ──
        cardLayout = new CardLayout();
        JPanel cards = new JPanel(cardLayout);

        // Empty placeholder
        emptyLabel = new JLabel("Select a file to view its contents", SwingConstants.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        cards.add(emptyLabel, CARD_EMPTY);

        // Text editor
        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setTabSize(4);
        textArea.setLineWrap(false);
        // Ctrl+S in editor should save the current file content, not close/reopen the whole archive.
        KeyStroke saveKey = KeyStroke.getKeyStroke("control S");
        textArea.getInputMap(JComponent.WHEN_FOCUSED).put(saveKey, "saveCurrentFile");
        textArea.getActionMap().put("saveCurrentFile", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveCurrentFile();
            }
        });
        JScrollPane textScroll = new JScrollPane(textArea);
        // Add line numbers
        textScroll.setRowHeaderView(new LineNumberView(textArea));
        cards.add(textScroll, CARD_TEXT);

        // Hex viewer
        hexArea = new JTextArea();
        hexArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        hexArea.setEditable(false);
        hexArea.setBackground(new Color(245, 245, 245));
        cards.add(new JScrollPane(hexArea), CARD_HEX);

        // Image preview
        imageLabel = new JLabel("", SwingConstants.CENTER);
        JScrollPane imageScroll = new JScrollPane(imageLabel);
        cards.add(imageScroll, CARD_IMAGE);

        // Directory info
        directoryLabel = new JLabel("", SwingConstants.CENTER);
        directoryLabel.setForeground(Color.GRAY);
        cards.add(directoryLabel, CARD_DIR);

        add(cards, BorderLayout.CENTER);

        cardLayout.show(cards, CARD_EMPTY);

        // Listen for file selection changes
        model.addPropertyChangeListener(this::onModelChange);
    }

    private void onModelChange(PropertyChangeEvent evt) {
        SwingUtils.onEdt(() -> {
            switch (evt.getPropertyName()) {
                case ArchiveModel.PROP_SELECTED_FILE -> displayFile((FileNode) evt.getNewValue());
                case ArchiveModel.PROP_OPEN -> {
                    if (Boolean.FALSE.equals(evt.getNewValue())) {
                        clearView();
                    }
                }
            }
        });
    }

    private void displayFile(FileNode fileNode) {
        if (fileNode == null) {
            clearView();
            return;
        }

        currentFilePath = fileNode.getPath();
        fileNameLabel.setText(fileNode.getDisplayName());

        if (fileNode.isDirectory()) {
            showDirectoryInfo(fileNode);
            return;
        }

        String ext = fileNode.getExtension();
        FileTypeDetector.FileType type = FileTypeDetector.detect(ext);

        switch (type) {
            case TEXT, UNKNOWN -> showTextContent(fileNode);
            case IMAGE -> showImageContent(fileNode);
            case BINARY -> showHexContent(fileNode);
        }
    }

    private void showTextContent(FileNode fileNode) {
        try {
            String content = model.readFileContent(fileNode.getPath());
            textArea.setText(content);
            textArea.setCaretPosition(0);
            textArea.setEditable(!model.isReadOnly());
            saveButton.setEnabled(!model.isReadOnly());
            showCard(CARD_TEXT);
        } catch (IOException ex) {
            textArea.setText("Error reading file: " + ex.getMessage());
            textArea.setEditable(false);
            saveButton.setEnabled(false);
            showCard(CARD_TEXT);
        }
    }

    private void showHexContent(FileNode fileNode) {
        try {
            byte[] data = model.readFileBytes(fileNode.getPath());
            hexArea.setText(formatHex(data));
            hexArea.setCaretPosition(0);
            saveButton.setEnabled(false);
            showCard(CARD_HEX);
        } catch (IOException ex) {
            hexArea.setText("Error reading file: " + ex.getMessage());
            showCard(CARD_HEX);
        }
    }

    private void showImageContent(FileNode fileNode) {
        try {
            byte[] data = model.readFileBytes(fileNode.getPath());
            ImageIcon icon = new ImageIcon(data);
            // Scale down large images
            if (icon.getIconWidth() > 600 || icon.getIconHeight() > 600) {
                Image scaled = icon.getImage().getScaledInstance(600, -1, Image.SCALE_SMOOTH);
                icon = new ImageIcon(scaled);
            }
            imageLabel.setIcon(icon);
            imageLabel.setText(null);
            saveButton.setEnabled(false);
            showCard(CARD_IMAGE);
        } catch (IOException ex) {
            imageLabel.setIcon(null);
            imageLabel.setText("Error loading image: " + ex.getMessage());
            showCard(CARD_IMAGE);
        }
    }

    private void showDirectoryInfo(FileNode fileNode) {
        try {
            int count = model.listChildren(fileNode.getPath()).size();
            directoryLabel.setText("<html><center><b>" + fileNode.getDisplayName()
                    + "</b><br>Directory<br>" + count + " item(s)</center></html>");
        } catch (IOException ex) {
            directoryLabel.setText("Directory: " + fileNode.getDisplayName());
        }
        saveButton.setEnabled(false);
        showCard(CARD_DIR);
    }

    private void clearView() {
        currentFilePath = null;
        fileNameLabel.setText(" ");
        textArea.setText("");
        hexArea.setText("");
        imageLabel.setIcon(null);
        imageLabel.setText(null);
        saveButton.setEnabled(false);
        showCard(CARD_EMPTY);
    }

    private void showCard(String card) {
        Container parent = emptyLabel.getParent();
        cardLayout.show(parent, card);
    }

    private void saveCurrentFile() {
        if (currentFilePath == null || model.isReadOnly())
            return;
        controller.saveFileContent(currentFilePath, textArea.getText());
    }

    private String formatHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(data.length, 16 * 512); // Show up to 8KB
        for (int i = 0; i < limit; i += 16) {
            sb.append(String.format("%08X  ", i));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    byte b = data[i + j];
                    sb.append(String.format("%02X ", b));
                    ascii.append((b >= 32 && b < 127) ? (char) b : '.');
                } else {
                    sb.append("   ");
                    ascii.append(' ');
                }
                if (j == 7)
                    sb.append(' ');
            }
            sb.append(" |").append(ascii).append("|\n");
        }
        if (data.length > limit) {
            sb.append(String.format("\n... (%,d more bytes not shown)", data.length - limit));
        }
        return sb.toString();
    }

    /**
     * Simple line-number gutter for the text editor.
     */
    private static final class LineNumberView extends JComponent {
        private final JTextArea textArea;

        LineNumberView(JTextArea textArea) {
            this.textArea = textArea;
            setPreferredSize(new Dimension(50, 0));
            setFont(textArea.getFont());
            textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(240, 240, 240));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(160, 160, 160));
            g2.setFont(getFont());

            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();
            int ascent = fm.getAscent();
            int lines = textArea.getLineCount();

            Rectangle clip = g2.getClipBounds();
            int startLine = Math.max(0, (clip.y / lineHeight));
            int endLine = Math.min(lines, ((clip.y + clip.height) / lineHeight) + 1);

            for (int i = startLine; i < endLine; i++) {
                String num = String.valueOf(i + 1);
                int x = getWidth() - fm.stringWidth(num) - 6;
                int y = (i * lineHeight) + ascent;
                g2.drawString(num, x, y);
            }
        }
    }
}
