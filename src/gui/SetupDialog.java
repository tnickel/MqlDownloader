package gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import config.ConfigurationManager;

public class SetupDialog extends JDialog {
    private final ConfigurationManager configManager;
    private JSpinner minWaitSpinner;
    private JSpinner maxWaitSpinner;
    private JTextField analysePathField;
    private String analysePath;

    public SetupDialog(JFrame parent, ConfigurationManager configManager) {
        super(parent, "Einstellungen", true);
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
            "Download-Geschwindigkeit",
            TitledBorder.LEFT,
            TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Min Wait Time
        gbc.gridx = 0;
        gbc.gridy = 0;
        speedPanel.add(new JLabel("Min. Wartezeit (Sekunden):"), gbc);

        SpinnerNumberModel minModel = new SpinnerNumberModel(
            configManager.getMinWaitTime() / 1000, // current
            4,                                     // minimum
            120,                                   // maximum
            1                                      // step
        );
        minWaitSpinner = new JSpinner(minModel);
        gbc.gridx = 1;
        speedPanel.add(minWaitSpinner, gbc);

        // Max Wait Time
        gbc.gridx = 0;
        gbc.gridy = 1;
        speedPanel.add(new JLabel("Max. Wartezeit (Sekunden):"), gbc);

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

        // Path Information Panel
        JPanel pathPanel = new JPanel(new GridBagLayout());
        pathPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Download-Pfad",
            TitledBorder.LEFT,
            TitledBorder.TOP));

        GridBagConstraints gbcPath = new GridBagConstraints();
        gbcPath.insets = new Insets(5, 5, 5, 5);
        gbcPath.anchor = GridBagConstraints.WEST;
        gbcPath.fill = GridBagConstraints.HORIZONTAL;

        gbcPath.gridx = 0;
        gbcPath.gridy = 0;
        gbcPath.weightx = 1.0;

        JTextField pathField = new JTextField(configManager.getDownloadPath());
        pathField.setEditable(false);
        pathField.setColumns(25); // Set width to fit the path nicely
        pathPanel.add(pathField, gbcPath);

        mainPanel.add(pathPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Analyse Directory Panel (Testreport-PDFs)
        JPanel analysePanel = new JPanel(new GridBagLayout());
        analysePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Analyse-Verzeichnis (Testreport-PDFs)",
            TitledBorder.LEFT,
            TitledBorder.TOP));

        GridBagConstraints gbcAnalyse = new GridBagConstraints();
        gbcAnalyse.insets = new Insets(5, 5, 5, 5);
        gbcAnalyse.anchor = GridBagConstraints.WEST;
        gbcAnalyse.fill = GridBagConstraints.HORIZONTAL;

        analysePath = configManager.getAnalysePath();
        analysePathField = new JTextField(analysePath);
        analysePathField.setEditable(false);
        analysePathField.setColumns(25);
        analysePathField.setToolTipText("Verzeichnis mit den Testreport-PDFs. Die Signal-ID muss im Dateinamen enthalten sein.");
        gbcAnalyse.gridx = 0;
        gbcAnalyse.gridy = 0;
        gbcAnalyse.weightx = 1.0;
        analysePanel.add(analysePathField, gbcAnalyse);

        JButton browseButton = new JButton("Ausw\u00e4hlen...");
        browseButton.setToolTipText("Analyse-Verzeichnis \u00fcber Dateiauswahl w\u00e4hlen");
        browseButton.addActionListener(e -> chooseAnalyseDirectory());
        gbcAnalyse.gridx = 1;
        gbcAnalyse.weightx = 0.0;
        gbcAnalyse.fill = GridBagConstraints.NONE;
        analysePanel.add(browseButton, gbcAnalyse);

        mainPanel.add(analysePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Speichern");
        JButton cancelButton = new JButton("Abbrechen");

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

    /** Dateiauswahl-Dialog f\u00fcr das Analyse-Verzeichnis (auch Netzwerkpfade). */
    private void chooseAnalyseDirectory() {
        JFileChooser chooser = new JFileChooser(analyseStartDir());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Analyse-Verzeichnis ausw\u00e4hlen");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            analysePath = selected.getAbsolutePath();
            analysePathField.setText(analysePath);
        }
    }

    private File analyseStartDir() {
        if (analysePath != null && !analysePath.isEmpty()) {
            File dir = new File(analysePath);
            if (dir.isDirectory()) {
                return dir;
            }
            File parent = dir.getParentFile();
            if (parent != null && parent.isDirectory()) {
                return parent;
            }
        }
        return null;
    }

    private void saveAndClose() {
        int minWait = (Integer) minWaitSpinner.getValue() * 1000; // Convert to milliseconds
        int maxWait = (Integer) maxWaitSpinner.getValue() * 1000;
        configManager.setWaitTimes(minWait, maxWait);
        configManager.setAnalysePath(analysePath);
        dispose();
    }
}
