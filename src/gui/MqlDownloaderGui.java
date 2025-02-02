package gui;

import javax.swing.*;
import java.awt.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManager;
import config.ConfigurationManager;
import downloader.SignalDownloader;

public class MqlDownloaderGui extends JFrame {
	  private static final Logger logger = LogManager.getLogger(MqlDownloaderGui.class);
	    private final ConfigurationManager configManager;
	    private JButton mql4Button;
	    private JButton mql5Button;
	    private JButton stopButton;
	    private JLabel mql4CounterLabel;
	    private JLabel mql5CounterLabel;
	    private WebDriver currentDriver;
	    private volatile boolean stopRequested;
	    private Thread downloadThread;

    public MqlDownloaderGui() {
        configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        initializeGui();
    }

    private void initializeGui() {
        setTitle("MQL Signal Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(3, 1, 10, 10));
        setSize(400, 200); // Breiter f�r die Counter

        // Create panels with FlowLayout for button and counter
        JPanel mql4Panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        JPanel mql5Panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        JPanel stopPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        // Initialize buttons
        mql4Button = createStyledButton("MQL4 Download");
        mql5Button = createStyledButton("MQL5 Download");
        stopButton = createStopButton();

        // Initialize counter labels
        mql4CounterLabel = createCounterLabel();
        mql5CounterLabel = createCounterLabel();

        // Add action listeners
        mql4Button.addActionListener(e -> handleDownloadButton("MQL4"));
        mql5Button.addActionListener(e -> handleDownloadButton("MQL5"));
        stopButton.addActionListener(e -> handleStopButton());

        // Add components to panels
        mql4Panel.add(mql4Button);
        mql4Panel.add(mql4CounterLabel);
        
        mql5Panel.add(mql5Button);
        mql5Panel.add(mql5CounterLabel);
        
        stopPanel.add(stopButton);

        // Add panels to frame
        add(mql4Panel);
        add(mql5Panel);
        add(stopPanel);

        // Initialize menu
        setJMenuBar(createMenuBar());

        // Stop Button initial deaktivieren
        stopButton.setEnabled(false);
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
        button.setBackground(new Color(220, 53, 69)); // Bootstrap Danger Red
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        return button;
    }

    private void handleStopButton() {
        stopRequested = true;
        stopButton.setEnabled(false);
        
        // Zeige "Stoppe Download..." Dialog
        JDialog stopDialog = new JDialog(this, "Stoppe Download...", true);
        stopDialog.setLayout(new FlowLayout());
        stopDialog.add(new JLabel("Stoppe Download Prozess..."));
        stopDialog.setSize(200, 100);
        stopDialog.setLocationRelativeTo(this);
        
        // Starte einen Thread f�r das Beenden
        new Thread(() -> {
            try {
                if (currentDriver != null) {
                    currentDriver.quit();
                    currentDriver = null;
                }
                if (downloadThread != null && downloadThread.isAlive()) {
                    downloadThread.interrupt();
                }
                
                SwingUtilities.invokeLater(() -> {
                    stopDialog.dispose();
                    resetButtons();
                    JOptionPane.showMessageDialog(this,
                        "Download wurde gestoppt",
                        "Download Status",
                        JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                logger.error("Fehler beim Stoppen des Downloads:", e);
            }
        }).start();
        
        // Zeige den Dialog
        stopDialog.setVisible(true);
    }
    private JLabel createCounterLabel() {
        JLabel label = new JLabel("0");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(new Color(255, 215, 0)); // Gold color
        label.setBackground(new Color(70, 70, 70));  // Dark background
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return label;
    }

    private void updateCounter(String version, int count) {
        SwingUtilities.invokeLater(() -> {
            JLabel label = version.equals("MQL4") ? mql4CounterLabel : mql5CounterLabel;
            label.setText(String.valueOf(count));
        });
    }
    private void handleDownloadButton(String version) {
        // Reset stop flag and counter
        stopRequested = false;
        updateCounter(version, 0);
        
        // Bestimme welcher Button gedr�ckt wurde und welcher der andere ist
        JButton activeButton = version.equals("MQL4") ? mql4Button : mql5Button;
        JButton inactiveButton = version.equals("MQL4") ? mql5Button : mql4Button;
        
        // Konfiguriere die Buttons
        activeButton.setBackground(new Color(144, 238, 144)); // Hellgr�n
        activeButton.setEnabled(false);
        inactiveButton.setEnabled(false);
        inactiveButton.setBackground(new Color(200, 200, 200)); // Grau
        stopButton.setEnabled(true);

        String downloadPath = configManager.getRootDirPath() + "\\download\\" + version.toLowerCase();
        configManager.setDownloadPath(downloadPath);
        String mqlVersion = version.equals("MQL4") ? "mt4" : "mt5";
        configManager.setBaseUrl("https://www.mql5.com/en/signals/" + mqlVersion + "/list");

        downloadThread = new Thread(() -> {
            try {
                configManager.initializeDirectories();
                WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
                currentDriver = webDriverManager.initializeDriver();

                SignalDownloader downloader = new SignalDownloader(currentDriver, configManager, configManager.getCredentials());
                downloader.setStopFlag(stopRequested);
                
                // Set up progress callback
                downloader.setProgressCallback(count -> updateCounter(version, count));
                
                downloader.startDownloadProcess();

                if (!stopRequested) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            version + " Download erfolgreich beendet",
                            "Download Status",
                            JOptionPane.INFORMATION_MESSAGE);
                        resetButtons();
                    });
                }

            } catch (Exception e) {
                if (!stopRequested) {
                    logger.error("Fehler beim " + version + " Download:", e);
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
                    currentDriver.quit();
                    currentDriver = null;
                }
                SwingUtilities.invokeLater(this::resetButtons);
            }
        });
        downloadThread.start();
       
    }

    private void resetButtons() {
        // Setze beide Buttons zur�ck auf ihren urspr�nglichen Zustand
        mql4Button.setEnabled(true);
        mql5Button.setEnabled(true);
        stopButton.setEnabled(false);
        mql4Button.setBackground(new Color(240, 240, 240));
        mql5Button.setBackground(new Color(240, 240, 240));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MqlDownloaderGui gui = new MqlDownloaderGui();
            gui.setVisible(true);
        });
    }
}