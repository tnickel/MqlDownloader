package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import config.ConfigurationManager;

public class MqlDownloaderGui extends JFrame {
    private static final Logger logger = LogManager.getLogger(MqlDownloaderGui.class);
    private final ConfigurationManager configManager;
    private final LogHandler logHandler;
    private final ButtonPanelManager buttonManager;
    private final DownloadManager downloadManager;
    private final ConversionManager conversionManager;

    public MqlDownloaderGui() {
        configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        logHandler = new LogHandler();
        buttonManager = new ButtonPanelManager(configManager);
        downloadManager = new DownloadManager(configManager, logHandler, buttonManager);
        conversionManager = new ConversionManager(configManager, logHandler, buttonManager);
        
        initializeGui();
        setupEventHandlers();
    }

    private void initializeGui() {
        setTitle("MQL Signal Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);

        // Top panel für Buttons
        JPanel topPanel = new JPanel(new GridLayout(6, 1, 5, 5)); // Erhöht auf 6 Reihen für das neue Feld
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panels zum Top Panel hinzufügen
        topPanel.add(buttonManager.createMql4Panel());
        topPanel.add(buttonManager.createMql5Panel());
        topPanel.add(buttonManager.createDownloadDaysPanel()); // Neues Panel für Download Days
        topPanel.add(createCenteredPanel(buttonManager.getConvertButton()));
        topPanel.add(createCenteredPanel(buttonManager.getStopButton()));
        topPanel.add(createCenteredPanel(buttonManager.getDoAllButton()));

        // Main Panel zusammenbauen
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(logHandler.getScrollPane(), BorderLayout.CENTER);
        mainPanel.add(buttonManager.createProgressPanel(), BorderLayout.SOUTH);

        // Zum Frame hinzufügen
        add(mainPanel);

        // Menu Bar initialisieren
        setJMenuBar(createMenuBar());

        // Buttons initial deaktivieren
        buttonManager.getStopButton().setEnabled(false);

        // Initiale Log Nachricht
        logHandler.log("Anwendung gestartet. Bereit für Operationen.");
    }

    private JPanel createCenteredPanel(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.add(component);
        return panel;
    }

    private void setupEventHandlers() {
        buttonManager.getMql4Button().addActionListener(e -> downloadManager.startDownload("MQL4"));
        buttonManager.getMql5Button().addActionListener(e -> downloadManager.startDownload("MQL5"));
        buttonManager.getStopButton().addActionListener(e -> downloadManager.stopDownload());
        buttonManager.getConvertButton().addActionListener(e -> conversionManager.startConversion());
        buttonManager.getDoAllButton().addActionListener(e -> handleDoAllButton());
    }

    private void handleDoAllButton() {
        logHandler.log("Starte automatisierten Gesamtprozess...");
        disableAllButtons();
        
        Thread allProcessesThread = new Thread(() -> {
            try {
                // Start des Gesamtprozesses - ohne vorheriges Löschen der Dateien
                
                logHandler.log("Starte MQL4 Download...");
                downloadManager.startDownload("MQL4");
                downloadManager.waitForDownloadCompletion();
                
                logHandler.log("Starte MQL5 Download...");
                downloadManager.startDownload("MQL5");
                downloadManager.waitForDownloadCompletion();
                
                logHandler.log("Starte Konvertierung...");
                conversionManager.startConversion();
                conversionManager.waitForConversionCompletion();
                
                SwingUtilities.invokeLater(() -> {
                    logHandler.log("Gesamtprozess erfolgreich abgeschlossen!");
                    enableAllButtons();
                });
            } catch (Exception e) {
                logHandler.logError("Fehler im Gesamtprozess: " + e.getMessage(), e);
                SwingUtilities.invokeLater(this::enableAllButtons);
            }
        });
        
        allProcessesThread.start();
    }

    private void disableAllButtons() {
        buttonManager.getDoAllButton().setEnabled(false);
        buttonManager.getMql4Button().setEnabled(false);
        buttonManager.getMql5Button().setEnabled(false);
        buttonManager.getConvertButton().setEnabled(false);
        buttonManager.getMql4LimitField().setEnabled(false);
        buttonManager.getMql5LimitField().setEnabled(false);
        buttonManager.getDownloadDaysField().setEnabled(false);
    }

    private void enableAllButtons() {
        buttonManager.resetButtons();
        buttonManager.getDoAllButton().setEnabled(true);
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