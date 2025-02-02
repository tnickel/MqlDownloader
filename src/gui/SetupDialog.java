package gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import config.ConfigurationManager;

public class SetupDialog extends JDialog {
    private final ConfigurationManager configManager;
    private JSpinner minWaitSpinner;
    private JSpinner maxWaitSpinner;

    public SetupDialog(JFrame parent, ConfigurationManager configManager) {
        super(parent, "Setup", true);
        this.configManager = configManager;
        initializeComponents();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Main panel with spacing
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Download Speed Configuration Panel
        JPanel speedPanel = new JPanel(new GridBagLayout());
        speedPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Download Speed Configuration",
            TitledBorder.LEFT,
            TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Min Wait Time
        gbc.gridx = 0;
        gbc.gridy = 0;
        speedPanel.add(new JLabel("Min Wait (seconds):"), gbc);

        SpinnerNumberModel minModel = new SpinnerNumberModel(
            configManager.getMinWaitTime() / 1000, // current
            2,                                     // minimum
            120,                                   // maximum
            1                                      // step
        );
        minWaitSpinner = new JSpinner(minModel);
        gbc.gridx = 1;
        speedPanel.add(minWaitSpinner, gbc);

        // Max Wait Time
        gbc.gridx = 0;
        gbc.gridy = 1;
        speedPanel.add(new JLabel("Max Wait (seconds):"), gbc);

        SpinnerNumberModel maxModel = new SpinnerNumberModel(
            configManager.getMaxWaitTime() / 1000, // current
            2,                                     // minimum
            120,                                   // maximum
            1                                      // step
        );
        maxWaitSpinner = new JSpinner(maxModel);
        gbc.gridx = 1;
        speedPanel.add(maxWaitSpinner, gbc);

        mainPanel.add(speedPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> saveAndClose());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Configure spinners to maintain min/max relationship
        minWaitSpinner.addChangeListener(e -> {
            int minVal = (Integer) minWaitSpinner.getValue();
            int maxVal = (Integer) maxWaitSpinner.getValue();
            if (maxVal < minVal) {
                maxWaitSpinner.setValue(minVal);
            }
        });

        maxWaitSpinner.addChangeListener(e -> {
            int minVal = (Integer) minWaitSpinner.getValue();
            int maxVal = (Integer) maxWaitSpinner.getValue();
            if (maxVal < minVal) {
                minWaitSpinner.setValue(maxVal);
            }
        });

        pack();
        setResizable(false);
        setLocationRelativeTo(getParent());
    }

    private void saveAndClose() {
        int minWait = (Integer) minWaitSpinner.getValue() * 1000; // Convert to milliseconds
        int maxWait = (Integer) maxWaitSpinner.getValue() * 1000;
        configManager.setWaitTimes(minWait, maxWait);
        dispose();
    }
}