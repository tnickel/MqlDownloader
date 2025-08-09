package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import config.ConfigurationManager;

public class ButtonPanelManager {
    private final ConfigurationManager configManager;
    private JTextField mql4LimitField;
    private JTextField mql5LimitField;
    private JTextField downloadDaysField;
    private JLabel mql4CounterLabel;
    private JLabel mql5CounterLabel;
    private JButton mql4Button;
    private JButton mql5Button;
    private JButton stopButton;
    private JButton convertButton;
    private JButton doAllButton;
    private JProgressBar convertProgress;
    private JLabel convertStatusLabel;

    public ButtonPanelManager(ConfigurationManager configManager) {
        this.configManager = configManager;
        initializeComponents();
    }

    private void initializeComponents() {
        mql4Button = createStyledButton(
                "MQL4 Download",
                UIManager.getIcon("FileView.directoryIcon"),
                "Startet den Download der MQL4 Signale");
        mql5Button = createStyledButton(
                "MQL5 Download",
                UIManager.getIcon("FileView.directoryIcon"),
                "Startet den Download der MQL5 Signale");
        stopButton = createStopButton();
        convertButton = createConvertButton();
        doAllButton = createDoAllButton();
        convertProgress = new JProgressBar(0, 100);
        convertProgress.setStringPainted(true);
        convertProgress.setVisible(false);
        convertStatusLabel = new JLabel("");
        convertStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        convertStatusLabel.setVisible(false);
    }

    public JPanel createMql4Panel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        panel.add(mql4Button);
        
        JLabel limitLabel = new JLabel("Limit:");
        limitLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(limitLabel);
        
        mql4LimitField = new JTextField(5);
        mql4LimitField.setText(String.valueOf(configManager.getMql4Limit()));
        mql4LimitField.setFont(new Font("Arial", Font.PLAIN, 14));
        addLimitFieldListener(mql4LimitField, true);
        panel.add(mql4LimitField);
        
        mql4CounterLabel = createCounterLabel();
        panel.add(mql4CounterLabel);
        
        return panel;
    }

    public JPanel createMql5Panel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        panel.add(mql5Button);
        
        JLabel limitLabel = new JLabel("Limit:");
        limitLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(limitLabel);
        
        mql5LimitField = new JTextField(5);
        mql5LimitField.setText(String.valueOf(configManager.getMql5Limit()));
        mql5LimitField.setFont(new Font("Arial", Font.PLAIN, 14));
        addLimitFieldListener(mql5LimitField, false);
        panel.add(mql5LimitField);
        
        mql5CounterLabel = createCounterLabel();
        panel.add(mql5CounterLabel);
        
        return panel;
    }

    // Neues Panel für Download Days
    public JPanel createDownloadDaysPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        JLabel daysLabel = new JLabel("Download only if older than (days):");
        daysLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(daysLabel);
        
        downloadDaysField = new JTextField(5);
        downloadDaysField.setText(String.valueOf(configManager.getDownloadDays()));
        downloadDaysField.setFont(new Font("Arial", Font.PLAIN, 14));
        downloadDaysField.setToolTipText("<html>Legt fest, wie alt die Dateien sein müssen, bevor sie neu heruntergeladen werden.<br>"+
                                          "Dies ist eine Optimierungsmaßnahme, um den Download-Prozess zu beschleunigen.<br>"+
                                          "Bei einem Wert von 5 werden Dateien, die jünger als 5 Tage sind, nicht erneut heruntergeladen.<br>"+
                                          "Ein Wert von 0 bewirkt, dass alle Dateien bei jedem Durchlauf neu heruntergeladen werden.</html>");
        
        // Verbesserte Validierung: Füge NumericRangeFilter hinzu
        ((AbstractDocument)downloadDaysField.getDocument()).setDocumentFilter(
            new NumericRangeFilter(0, 20, downloadDaysField, "Download Tage"));
        
        // Fokus-Listener für Eingabevalidierung
        addDownloadDaysFieldListener(downloadDaysField);
        panel.add(downloadDaysField);
        
        return panel;
    }

    /**
     * Neues Panel für Convert-Button mit Hinweistext
     */
    public JPanel createConvertPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        // Convert Button zentriert
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(convertButton);
        panel.add(buttonPanel);
        
        // Hinweistext unter dem Button
        JLabel hinweisLabel = new JLabel("<html><i>Hinweis: Provider mit 3MPDD &lt; 0.5 werden automatisch gelöscht</i></html>");
        hinweisLabel.setFont(hinweisLabel.getFont().deriveFont(Font.ITALIC, 11f));
        hinweisLabel.setForeground(Color.GRAY);
        hinweisLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hinweisLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel hinweisPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        hinweisPanel.add(hinweisLabel);
        panel.add(hinweisPanel);
        
        return panel;
    }

    public JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(convertProgress, BorderLayout.CENTER);
        panel.add(convertStatusLabel, BorderLayout.SOUTH);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return panel;
    }

    private void addLimitFieldListener(JTextField field, boolean isMql4) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    int value = Integer.parseInt(field.getText());
                    if (value >= 1 && value <= 5000) {
                        if (isMql4) {
                            configManager.setMql4Limit(value);
                        } else {
                            configManager.setMql5Limit(value);
                        }
                    } else {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    field.setText(String.valueOf(isMql4 ? 
                        configManager.getMql4Limit() : 
                        configManager.getMql5Limit()));
                    JOptionPane.showMessageDialog(null,
                        "Bitte geben Sie eine Zahl zwischen 1 und 5000 ein.",
                        "Ungültige Eingabe",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
    
    // Verbesserter Listener für das Download Days Feld
    private void addDownloadDaysFieldListener(JTextField field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    int value = Integer.parseInt(field.getText());
                    // Der Wert wurde bereits durch den DocumentFilter validiert, 
                    // aber wir setzen ihn hier im configManager
                    configManager.setDownloadDays(value);
                } catch (NumberFormatException ex) {
                    // Sollte dank DocumentFilter nicht vorkommen, aber als Fallback
                    field.setText(String.valueOf(configManager.getDownloadDays()));
                    JOptionPane.showMessageDialog(null,
                        "Bitte geben Sie eine Zahl zwischen 0 und 20 ein.",
                        "Ungültige Eingabe",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private JButton createStyledButton(String text, Icon icon, String tooltip) {
        JButton button = new JButton(text, icon);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(240, 240, 240));
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        if (tooltip != null) {
            button.setToolTipText(tooltip);
        }
        return button;
    }

    private JButton createStyledButton(String text) {
        return createStyledButton(text, null, null);
    }

    private JButton createStopButton() {
        JButton button = new JButton(
                "Stop Download",
                UIManager.getIcon("OptionPane.errorIcon"));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(220, 53, 69));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setToolTipText("Stoppt laufende Downloads");
        return button;
    }

    private JButton createConvertButton() {
        JButton button = new JButton(
                "Convert",
                UIManager.getIcon("FileView.fileIcon"));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(65, 105, 225));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setToolTipText("Konvertiert heruntergeladene HTML Dateien");
        return button;
    }

    private JButton createDoAllButton() {
        JButton button = new JButton(
                "Do all at Once",
                UIManager.getIcon("OptionPane.informationIcon"));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(50, 205, 50));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setToolTipText("Führt alle Schritte nacheinander aus");
        return button;
    }

    private JLabel createCounterLabel() {
        JLabel label = new JLabel("0");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(new Color(255, 215, 0));
        label.setBackground(new Color(70, 70, 70));
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return label;
    }

    public void updateCounter(String version, int count) {
        SwingUtilities.invokeLater(() -> {
            JLabel label = version.equals("MQL4") ? mql4CounterLabel : mql5CounterLabel;
            label.setText(String.valueOf(count));
        });
    }

    public void resetButtons() {
        mql4Button.setEnabled(true);
        mql5Button.setEnabled(true);
        stopButton.setEnabled(false);
        mql4Button.setBackground(new Color(240, 240, 240));
        mql5Button.setBackground(new Color(240, 240, 240));
        mql4LimitField.setEnabled(true);
        mql5LimitField.setEnabled(true);
        downloadDaysField.setEnabled(true);
    }

    // Getter für alle Buttons und Felder
    public JButton getMql4Button() { return mql4Button; }
    public JButton getMql5Button() { return mql5Button; }
    public JButton getStopButton() { return stopButton; }
    public JButton getConvertButton() { return convertButton; }
    public JButton getDoAllButton() { return doAllButton; }
    public JTextField getMql4LimitField() { return mql4LimitField; }
    public JTextField getMql5LimitField() { return mql5LimitField; }
    public JTextField getDownloadDaysField() { return downloadDaysField; }
    public JProgressBar getConvertProgress() { return convertProgress; }
    public JLabel getConvertStatusLabel() { return convertStatusLabel; }
    
    // Neue DocumentFilter-Klasse für Zahlenvalidierung
    private class NumericRangeFilter extends DocumentFilter {
        private final int min;
        private final int max;
        private final JTextField field;
        private final String fieldName;
        
        public NumericRangeFilter(int min, int max, JTextField field, String fieldName) {
            this.min = min;
            this.max = max;
            this.field = field;
            this.fieldName = fieldName;
        }
        
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) 
                throws BadLocationException {
            // Kombiniere vorhandenen Text mit neuem Text
            String newValue = fb.getDocument().getText(0, fb.getDocument().getLength()) + string;
            if (isValid(newValue)) {
                super.insertString(fb, offset, string, attr);
            } else {
                // Warnung anzeigen, wenn nicht gültig
                showWarning();
            }
        }
        
        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) 
                throws BadLocationException {
            // Berechne den neuen Wert nach dem Ersetzen
            String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
            String beforeOffset = currentText.substring(0, offset);
            String afterOffset = currentText.substring(offset + length);
            String newValue = beforeOffset + text + afterOffset;
            
            // Wenn leer oder nur ein Minus, erlauben (temporär)
            if (newValue.isEmpty() || (newValue.equals("-") && min < 0)) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }
            
            if (isValid(newValue)) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                // Warnung anzeigen, wenn nicht gültig
                showWarning();
            }
        }
        
        private boolean isValid(String value) {
            if (value.isEmpty()) return true; // Leere Eingabe erlauben (temporär)
            
            try {
                int intValue = Integer.parseInt(value);
                return intValue >= min && intValue <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        private void showWarning() {
            SwingUtilities.invokeLater(() -> {
                Toolkit.getDefaultToolkit().beep();
                JOptionPane.showMessageDialog(field,
                    fieldName + " muss zwischen " + min + " und " + max + " liegen.",
                    "Ungültiger Wert",
                    JOptionPane.WARNING_MESSAGE);
                field.setText(String.valueOf(configManager.getDownloadDays()));
            });
        }
    }
}