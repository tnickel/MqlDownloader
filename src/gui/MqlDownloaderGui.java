package gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManager;
import config.ConfigurationManager;
import downloader.SignalDownloader;
import converter.HtmlConverter;

public class MqlDownloaderGui extends JFrame {
    private static final Logger logger = LogManager.getLogger(MqlDownloaderGui.class);
    private final ConfigurationManager configManager;
    private JButton mql4Button;
    private JButton mql5Button;
    private JButton stopButton;
    private JButton convertButton;
    private JButton doAllButton;
    private JLabel mql4CounterLabel;
    private JLabel mql5CounterLabel;
    private JTextField mql4LimitField;
    private JTextField mql5LimitField;
    private WebDriver currentDriver;
    private volatile boolean stopRequested;
    private Thread downloadThread;
    private JTextArea logArea;
    private JScrollPane scrollPane;
    private JProgressBar convertProgress;
    private JLabel convertStatusLabel;

    public MqlDownloaderGui() {
        configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        initializeGui();
    }

    private void initializeGui() {
        setTitle("MQL Signal Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);

        // Top panel für Buttons
        JPanel topPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Button Panels erstellen
        JPanel mql4Panel = createMql4Panel();
        JPanel mql5Panel = createMql5Panel();
        JPanel convertPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel stopPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel doAllPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        // Buttons initialisieren
        convertButton = createConvertButton();
        stopButton = createStopButton();
        doAllButton = createDoAllButton();

        // Action Listener hinzufügen
        mql4Button.addActionListener(e -> handleDownloadButton("MQL4"));
        mql5Button.addActionListener(e -> handleDownloadButton("MQL5"));
        stopButton.addActionListener(e -> handleStopButton());
        convertButton.addActionListener(e -> handleConvertButton());
        doAllButton.addActionListener(e -> handleDoAllButton());

        // Komponenten zu Panels hinzufügen
        convertPanel.add(convertButton);
        stopPanel.add(stopButton);
        doAllPanel.add(doAllButton);

        // Panels zum Top Panel hinzufügen
        topPanel.add(mql4Panel);
        topPanel.add(mql5Panel);
        topPanel.add(convertPanel);
        topPanel.add(stopPanel);
        topPanel.add(doAllPanel);

        // Log Area initialisieren
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setMargin(new Insets(5,5,5,5));

        scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Log Output", 
            TitledBorder.LEFT, 
            TitledBorder.TOP));

        // Progress Bar initialisieren
        convertProgress = new JProgressBar(0, 100);
        convertProgress.setStringPainted(true);
        convertProgress.setVisible(false);

        convertStatusLabel = new JLabel("");
        convertStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        convertStatusLabel.setVisible(false);

        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.add(convertProgress, BorderLayout.CENTER);
        progressPanel.add(convertStatusLabel, BorderLayout.SOUTH);
        progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Main Panel für alles
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(progressPanel, BorderLayout.SOUTH);

        // Zum Frame hinzufügen
        add(mainPanel);

        // Menu Bar initialisieren
        setJMenuBar(createMenuBar());

        // Buttons initial deaktivieren
        stopButton.setEnabled(false);

        // Initiale Log Nachricht
        log("Anwendung gestartet. Bereit für Operationen.");
    }

    private JPanel createMql4Panel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        mql4Button = createStyledButton("MQL4 Download");
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

    private JPanel createMql5Panel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        mql5Button = createStyledButton("MQL5 Download");
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
                    JOptionPane.showMessageDialog(MqlDownloaderGui.this,
                        "Bitte geben Sie eine Zahl zwischen 1 und 5000 ein.",
                        "Ungültige Eingabe",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().toString() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
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

    private void handleDoAllButton() {
        log("Starte automatisierten Gesamtprozess...");
        doAllButton.setEnabled(false);
        mql4Button.setEnabled(false);
        mql5Button.setEnabled(false);
        convertButton.setEnabled(false);
        mql4LimitField.setEnabled(false);
        mql5LimitField.setEnabled(false);
        
        Thread allProcessesThread = new Thread(() -> {
            try {
                // MQL4 Download
                log("Starte MQL4 Download...");
                handleDownloadButton("MQL4");
                waitForDownloadCompletion();
                
                // MQL5 Download
                log("Starte MQL5 Download...");
                handleDownloadButton("MQL5");
                waitForDownloadCompletion();
                
                // Konvertierung
                log("Starte Konvertierung...");
                handleConvertButton();
                
                SwingUtilities.invokeLater(() -> {
                    log("Gesamtprozess erfolgreich abgeschlossen!");
                    doAllButton.setEnabled(true);
                    resetButtons();
                });
            } catch (Exception e) {
                log("Fehler im Gesamtprozess: " + e.getMessage());
                logger.error("Fehler im Gesamtprozess", e);
                SwingUtilities.invokeLater(() -> {
                    doAllButton.setEnabled(true);
                    resetButtons();
                });
            }
        });
        
        allProcessesThread.start();
    }

    private void waitForDownloadCompletion() {
        try {
            while (downloadThread != null && downloadThread.isAlive()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleDownloadButton(String version) {
        log("Starte " + version + " Download Prozess...");
        stopRequested = false;
        updateCounter(version, 0);
        
        JButton activeButton = version.equals("MQL4") ? mql4Button : mql5Button;
        JButton inactiveButton = version.equals("MQL4") ? mql5Button : mql4Button;
        
        mql4LimitField.setEnabled(false);
        mql5LimitField.setEnabled(false);
        
        activeButton.setBackground(new Color(144, 238, 144));
        activeButton.setEnabled(false);
        inactiveButton.setEnabled(false);
        inactiveButton.setBackground(new Color(200, 200, 200));
        stopButton.setEnabled(true);

        String downloadPath = configManager.getRootDirPath() + "\\download\\" + version.toLowerCase();
        log("Download Pfad: " + downloadPath);
        configManager.setDownloadPath(downloadPath);
        
        downloadThread = new Thread(() -> {
            try {
                logger.info("Initialisiere Verzeichnisse...");
                configManager.initializeDirectories();
                
                logger.info("Setze WebDriver auf...");
                WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
                currentDriver = webDriverManager.initializeDriver();

                logger.info("Starte Download Prozess...");
                SignalDownloader downloader = new SignalDownloader(currentDriver, configManager, configManager.getCredentials());
                downloader.setStopFlag(stopRequested);
                downloader.setProgressCallback(count -> {
                    updateCounter(version, count);
                    int limit = version.equals("MQL4") ? 
                        configManager.getMql4Limit() : 
                        configManager.getMql5Limit();
                    if (count >= limit) {
                        stopRequested = true;
                        SwingUtilities.invokeLater(() -> {
                            log(version + " Download erfolgreich beendet. Limit von " + limit + " erreicht.");
                            if (currentDriver != null) {
                                logger.info("Schließe WebDriver...");
                                currentDriver.quit();
                                currentDriver = null;
                            }
                            resetButtons();
                        });
                    }
                });
                
                downloader.startDownloadProcess();

            } catch (Exception e) {
                if (!stopRequested) {
                    log("Fehler während " + version + " Download: " + e.getMessage());
                    logger.error("Download error", e);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "Fehler beim Download: " + e.getMessage(),
                            "Fehler",
                            JOptionPane.ERROR_MESSAGE);
                        resetButtons();
                    });
                }
            } finally {
                if (currentDriver != null) {
                    logger.info("Schließe WebDriver...");
                    currentDriver.quit();
                    currentDriver = null;
                }
                SwingUtilities.invokeLater(() -> {
                    resetButtons();
                    mql4LimitField.setEnabled(true);
                    mql5LimitField.setEnabled(true);
                });
            }
        });
        downloadThread.start();
    }

    private void handleStopButton() {
        log("Stoppe Download Prozess...");
        stopRequested = true;
        stopButton.setEnabled(false);
        
        new Thread(() -> {
            try {
                if (currentDriver != null) {
                    logger.info("Schließe WebDriver...");
                    currentDriver.quit();
                    currentDriver = null;
                }
                if (downloadThread != null && downloadThread.isAlive()) {
                    logger.info("Unterbreche Download Thread...");
                    downloadThread.interrupt();
                }
                
                SwingUtilities.invokeLater(() -> {
                    resetButtons();
                    log("Download Prozess manuell gestoppt");
                });
            } catch (Exception e) {
                log("Fehler beim Stoppen des Downloads: " + e.getMessage());
                logger.error("Stop error", e);
            }
        }).start();
    }

    private void handleConvertButton() {
        log("Starte Konvertierungsprozess...");
        convertButton.setEnabled(false);
        convertProgress.setValue(0);
        convertProgress.setVisible(true);
        convertStatusLabel.setVisible(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        Thread conversionThread = new Thread(() -> {
            try {
                String basePath = configManager.getRootDirPath() + "\\download";
                HtmlConverter converter = new HtmlConverter(basePath);
                
                converter.setProgressCallback((progress, status) -> {
                    SwingUtilities.invokeLater(() -> {
                        convertProgress.setValue(progress);
                        convertStatusLabel.setText(status);
                        log(status);
                    });
                });
                
                converter.convertAllHtmlFiles();
                
                SwingUtilities.invokeLater(() -> {
                    log("Konvertierung erfolgreich abgeschlossen!");
                });
            } catch (Exception e) {
                log("Fehler während der Konvertierung: " + e.getMessage());
                logger.error("Konvertierungsfehler", e);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    convertButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    convertProgress.setVisible(false);
                    convertStatusLabel.setVisible(false);
                });
            }
        });
        
        conversionThread.start();
    }

    private void updateCounter(String version, int count) {
        SwingUtilities.invokeLater(() -> {
            JLabel label = version.equals("MQL4") ? mql4CounterLabel : mql5CounterLabel;
            label.setText(String.valueOf(count));
        });
    }

    private void resetButtons() {
        mql4Button.setEnabled(true);
        mql5Button.setEnabled(true);
        stopButton.setEnabled(false);
        mql4Button.setBackground(new Color(240, 240, 240));
        mql5Button.setBackground(new Color(240, 240, 240));
        mql4LimitField.setEnabled(true);
        mql5LimitField.setEnabled(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem setupItem = new JMenuItem("Setup");
        setupItem.addActionListener(e -> showSetupDialog());
        
        fileMenu.add(setupItem);
        menuBar.add(fileMenu);
        
        return menuBar;
    }

    private void showSetupDialog() {
        SetupDialog dialog = new SetupDialog(this, configManager);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.error("Error setting Look and Feel", e);
            }
            MqlDownloaderGui gui = new MqlDownloaderGui();
            gui.setVisible(true);
        });
    }
}