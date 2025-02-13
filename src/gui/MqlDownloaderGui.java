package gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
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
    private JLabel mql4CounterLabel;
    private JLabel mql5CounterLabel;
    private WebDriver currentDriver;
    private volatile boolean stopRequested;
    private Thread downloadThread;
    private JTextArea logArea;
    private JScrollPane scrollPane;

    public MqlDownloaderGui() {
        configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        initializeGui();
    }

    private void initializeGui() {
        setTitle("MQL Signal Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);  // Made window bigger

        // Top panel for buttons
        JPanel topPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create button panels
        JPanel mql4Panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        JPanel mql5Panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        JPanel convertPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel stopPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        // Initialize buttons
        mql4Button = createStyledButton("MQL4 Download");
        mql5Button = createStyledButton("MQL5 Download");
        stopButton = createStopButton();
        convertButton = createConvertButton();

        // Initialize counter labels
        mql4CounterLabel = createCounterLabel();
        mql5CounterLabel = createCounterLabel();

        // Add action listeners
        mql4Button.addActionListener(e -> handleDownloadButton("MQL4"));
        mql5Button.addActionListener(e -> handleDownloadButton("MQL5"));
        stopButton.addActionListener(e -> handleStopButton());
        convertButton.addActionListener(e -> handleConvertButton());

        // Add components to panels
        mql4Panel.add(mql4Button);
        mql4Panel.add(mql4CounterLabel);
        mql5Panel.add(mql5Button);
        mql5Panel.add(mql5CounterLabel);
        convertPanel.add(convertButton);
        stopPanel.add(stopButton);

        // Add panels to top panel
        topPanel.add(mql4Panel);
        topPanel.add(mql5Panel);
        topPanel.add(convertPanel);
        topPanel.add(stopPanel);

        // Initialize log area
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

        // Main panel to hold everything
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Add main panel to frame
        add(mainPanel);

        // Initialize menu
        setJMenuBar(createMenuBar());

        // Stop Button initial deactivate
        stopButton.setEnabled(false);

        // Initial log message
        log("Application started. Ready for operations.");
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

    private JLabel createCounterLabel() {
        JLabel label = new JLabel("0");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(new Color(255, 215, 0));
        label.setBackground(new Color(70, 70, 70));
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return label;
    }

    private void handleConvertButton() {
        log("Starting conversion process...");
        convertButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        Thread conversionThread = new Thread(() -> {
            try {
                String basePath = configManager.getRootDirPath() + "\\download";
                log("Base conversion path: " + basePath);
                
                HtmlConverter converter = new HtmlConverter(basePath);
                converter.convertAllHtmlFiles();
                
                SwingUtilities.invokeLater(() -> {
                    log("Conversion completed successfully!");
                    JOptionPane.showMessageDialog(this,
                        "HTML files have been successfully converted to TXT format.",
                        "Conversion Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                log("Error during conversion: " + e.getMessage());
                logger.error("Conversion error", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Error during conversion: " + e.getMessage(),
                        "Conversion Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    convertButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                });
            }
        });
        
        conversionThread.start();
    }

    private void handleDownloadButton(String version) {
        log("Starting " + version + " download process...");
        stopRequested = false;
        updateCounter(version, 0);
        
        JButton activeButton = version.equals("MQL4") ? mql4Button : mql5Button;
        JButton inactiveButton = version.equals("MQL4") ? mql5Button : mql4Button;
        
        activeButton.setBackground(new Color(144, 238, 144));
        activeButton.setEnabled(false);
        inactiveButton.setEnabled(false);
        inactiveButton.setBackground(new Color(200, 200, 200));
        stopButton.setEnabled(true);

        String downloadPath = configManager.getRootDirPath() + "\\download\\" + version.toLowerCase();
        log("Download path: " + downloadPath);
        configManager.setDownloadPath(downloadPath);
        
        downloadThread = new Thread(() -> {
            try {
                log("Initializing directories...");
                configManager.initializeDirectories();
                
                log("Setting up WebDriver...");
                WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
                currentDriver = webDriverManager.initializeDriver();

                log("Starting download process...");
                SignalDownloader downloader = new SignalDownloader(currentDriver, configManager, configManager.getCredentials());
                downloader.setStopFlag(stopRequested);
                downloader.setProgressCallback(count -> updateCounter(version, count));
                
                downloader.startDownloadProcess();

                if (!stopRequested) {
                    SwingUtilities.invokeLater(() -> {
                        log(version + " download completed successfully");
                        JOptionPane.showMessageDialog(this,
                            version + " Download erfolgreich beendet",
                            "Download Status",
                            JOptionPane.INFORMATION_MESSAGE);
                        resetButtons();
                    });
                }
            } catch (Exception e) {
                if (!stopRequested) {
                    log("Error during " + version + " download: " + e.getMessage());
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
                    log("Closing WebDriver...");
                    currentDriver.quit();
                    currentDriver = null;
                }
                SwingUtilities.invokeLater(this::resetButtons);
            }
        });
        downloadThread.start();
    }

    private void handleStopButton() {
        log("Stopping download process...");
        stopRequested = true;
        stopButton.setEnabled(false);
        
        JDialog stopDialog = new JDialog(this, "Stoppe Download...", true);
        stopDialog.setLayout(new FlowLayout());
        stopDialog.add(new JLabel("Stoppe Download Prozess..."));
        stopDialog.setSize(200, 100);
        stopDialog.setLocationRelativeTo(this);
        
        new Thread(() -> {
            try {
                if (currentDriver != null) {
                    log("Closing WebDriver...");
                    currentDriver.quit();
                    currentDriver = null;
                }
                if (downloadThread != null && downloadThread.isAlive()) {
                    log("Interrupting download thread...");
                    downloadThread.interrupt();
                }
                
                SwingUtilities.invokeLater(() -> {
                    stopDialog.dispose();
                    resetButtons();
                    log("Download process stopped");
                    JOptionPane.showMessageDialog(this,
                        "Download wurde gestoppt",
                        "Download Status",
                        JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                log("Error while stopping download: " + e.getMessage());
                logger.error("Stop error", e);
            }
        }).start();
        
        stopDialog.setVisible(true);
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