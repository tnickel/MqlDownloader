package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import config.ConfigurationManager;

public class ButtonPanelManager {
    private final ConfigurationManager configManager;
    private JTextField mql4LimitField;
    private JTextField mql5LimitField;
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
        mql4Button = createStyledButton("MQL4 Download");
        mql5Button = createStyledButton("MQL5 Download");
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

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(240, 240, 240));
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        return button;
    }

    private JButton createStopButton() {
        JButton button = new JButton("Stop Download");
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(220, 53, 69));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        return button;
    }

    private JButton createConvertButton() {
        JButton button = new JButton("Convert");
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(65, 105, 225));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        return button;
    }

    private JButton createDoAllButton() {
        JButton button = new JButton("Do all at Once");
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(50, 205, 50));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
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
    }

    // Getter für alle Buttons und Felder
    public JButton getMql4Button() { return mql4Button; }
    public JButton getMql5Button() { return mql5Button; }
    public JButton getStopButton() { return stopButton; }
    public JButton getConvertButton() { return convertButton; }
    public JButton getDoAllButton() { return doAllButton; }
    public JTextField getMql4LimitField() { return mql4LimitField; }
    public JTextField getMql5LimitField() { return mql5LimitField; }
    public JProgressBar getConvertProgress() { return convertProgress; }
    public JLabel getConvertStatusLabel() { return convertStatusLabel; }
}