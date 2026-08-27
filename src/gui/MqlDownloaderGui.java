package gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Color;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.DefaultListCellRenderer;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import config.ConfigurationManager;
import utils.AutoSchedulerManager;

public class MqlDownloaderGui extends JFrame {
    private static final Logger logger = LogManager.getLogger(MqlDownloaderGui.class);
    private final ConfigurationManager configManager;
    private final LogHandler logHandler;
    private final ButtonPanelManager buttonManager;
    private final DownloadManager downloadManager;
    private final ConversionManager conversionManager;
    private final AutoSchedulerManager autoScheduler;
    private JButton statisticsButton;
    private JButton abonnentenStatistikButton;
    private JToggleButton autoModeToggleButton;
    private JLabel autoModeInfoLabel;
    private DefaultListModel<String> subscriberChangesListModel;
    private JList<String> subscriberChangesList;
    private boolean overallProcessRunning;

    public MqlDownloaderGui() {
        this("C:\\Forex\\MqlAnalyzer");
    }

    MqlDownloaderGui(String rootDirectory) {
        configManager = new ConfigurationManager(rootDirectory);
        logHandler = new LogHandler();
        buttonManager = new ButtonPanelManager(configManager);
        downloadManager = new DownloadManager(configManager, logHandler, buttonManager, this);
        conversionManager = new ConversionManager(configManager, logHandler, buttonManager);
        autoScheduler = new AutoSchedulerManager(this::handleDoAllButton);
        
        initializeGui();
        setupEventHandlers();
        
        // Restore saved AutoMode state on startup
        if (configManager.isAutoMode()) {
            autoModeToggleButton.setSelected(true);
            toggleAutoMode(true);
        }
    }

    private void initializeGui() {
        setTitle("MQL Signal Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(1020, 680);

        // Top panel fuer Buttons - kompakte Zeilen mit ihrer natuerlichen Hoehe
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Erste Zeile: MQL4 und MQL5 horizontal nebeneinander
        JPanel downloadPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        downloadPanel.add(buttonManager.createMql4Panel());
        downloadPanel.add(buttonManager.createMql5Panel());
        topPanel.add(downloadPanel);
        topPanel.add(Box.createVerticalStrut(5));

        // Zweite Zeile: Download Days und Convert Panel horizontal nebeneinander
        JPanel configPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        configPanel.add(buttonManager.createDownloadDaysPanel());
        configPanel.add(buttonManager.createConvertPanel());
        topPanel.add(configPanel);
        topPanel.add(Box.createVerticalStrut(5));

        // Dritte Zeile: Stop und Do All Buttons horizontal nebeneinander
        JPanel actionPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        actionPanel.add(createCenteredPanel(buttonManager.getStopButton()));
        actionPanel.add(createCenteredPanel(buttonManager.getDoAllButton()));
        topPanel.add(actionPanel);
        topPanel.add(Box.createVerticalStrut(5));

        // Vierte Zeile: Abonnenten Checkbox
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        checkboxPanel.add(buttonManager.getSubscribersOnlyCheckbox());
        topPanel.add(checkboxPanel);
        topPanel.add(Box.createVerticalStrut(5));

        // Fuenfte Zeile: Automatikmodus (Freitag 18:00 Uhr) Panel
        autoModeToggleButton = new JToggleButton("Automatikmodus: AUS");
        autoModeToggleButton.setFont(autoModeToggleButton.getFont().deriveFont(Font.BOLD));
        autoModeToggleButton.setFocusPainted(false);

        autoModeInfoLabel = new JLabel("(Einmal pro Woche am Freitag um 18:00 Uhr automatisch ausf\u00fchren)");
        autoModeInfoLabel.setFont(autoModeInfoLabel.getFont().deriveFont(Font.ITALIC));
        autoModeInfoLabel.setForeground(Color.DARK_GRAY);

        JPanel autoModePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        autoModePanel.add(autoModeToggleButton);
        autoModePanel.add(autoModeInfoLabel);
        topPanel.add(autoModePanel);

        // Statistik-Buttons (rechts neben dem Log-Panel)
        statisticsButton = createStatisticsButton();
        abonnentenStatistikButton = createAbonnentenStatistikButton();
        JPanel statisticsButtonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        statisticsButtonPanel.add(statisticsButton);
        statisticsButtonPanel.add(abonnentenStatistikButton);

        // Sidebar fuer Abonnenten-Aenderungen
        subscriberChangesListModel = new DefaultListModel<>();
        subscriberChangesList = new JList<>(subscriberChangesListModel);
        subscriberChangesList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        subscriberChangesList.setCellRenderer(new SubscriberChangeCellRenderer());

        JScrollPane subscriberChangesScrollPane = new JScrollPane(subscriberChangesList);
        subscriberChangesScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Abonnenten-\u00c4nderungen",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP));
        subscriberChangesScrollPane.setPreferredSize(new Dimension(380, 260));

        JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
        sidePanel.add(statisticsButtonPanel, BorderLayout.NORTH);
        sidePanel.add(subscriberChangesScrollPane, BorderLayout.CENTER);

        // Main Panel zusammenbauen
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(logHandler.getScrollPane(), BorderLayout.CENTER);
        mainPanel.add(sidePanel, BorderLayout.EAST);
        mainPanel.add(buttonManager.createProgressPanel(), BorderLayout.SOUTH);

        // Zum Frame hinzufuegen
        add(mainPanel);

        // Menu Bar initialisieren
        setJMenuBar(createMenuBar());

        // Buttons initial deaktivieren
        buttonManager.getStopButton().setEnabled(false);

        // Initiale Log Nachricht
        logHandler.log("Anwendung gestartet. Bereit f\u00fcr Operationen.");
    }

    private JButton createStatisticsButton() {
        JButton button = new JButton("Download-Statistik", UIManager.getIcon("FileView.fileIcon"));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setBackground(new Color(255, 215, 0));
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setToolTipText("Zeigt eine Statistik \u00fcber das Alter der heruntergeladenen Dateien an");
        return button;
    }

    private JButton createAbonnentenStatistikButton() {
        JButton button = new JButton("Abonnenten-Statistik", UIManager.getIcon("FileView.computerIcon"));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setBackground(new Color(34, 139, 34));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setToolTipText("Zeigt eine detaillierte, sortierbare \u00dcbersicht der Abonnenten-Zug\u00e4nge und Abg\u00e4nge mit Verlaufs-Chart an");
        return button;
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
        
        statisticsButton.addActionListener(e -> showStatisticsDialog());
        abonnentenStatistikButton.addActionListener(e -> showSubscriberStatisticsDialog());
        autoModeToggleButton.addActionListener(e -> toggleAutoMode(autoModeToggleButton.isSelected()));
    }

    private void toggleAutoMode(boolean active) {
        configManager.setAutoMode(active);
        if (active) {
            autoModeToggleButton.setText("Automatikmodus: AN");
            autoModeToggleButton.setBackground(new Color(34, 139, 34));
            autoModeToggleButton.setForeground(Color.WHITE);
            autoScheduler.start();
            String nextRun = AutoSchedulerManager.getNextRunDateFormatted();
            autoModeInfoLabel.setText("<html><b>Automatikmodus AKTIV</b> (Jeden Fr. 18:00 Uhr) | N\u00e4chste Ausf\u00fchrung: " + nextRun + "</html>");
            logHandler.log("Automatikmodus AKTIVIERT: Geplant fuer jeden Freitag um 18:00 Uhr. Naechster Durchlauf: " + nextRun);
        } else {
            autoModeToggleButton.setText("Automatikmodus: AUS");
            autoModeToggleButton.setBackground(new Color(220, 220, 220));
            autoModeToggleButton.setForeground(Color.BLACK);
            autoScheduler.stop();
            autoModeInfoLabel.setText("(Einmal pro Woche am Freitag um 18:00 Uhr automatisch ausf\u00fchren)");
            logHandler.log("Automatikmodus DEAKTIVIERT.");
        }
    }
    
    private void showStatisticsDialog() {
        logHandler.log("Zeige Download-Statistik an...");
        StatisticsDialog dialog = new StatisticsDialog(this, configManager);
        dialog.setVisible(true);
    }

    private void showSubscriberStatisticsDialog() {
        logHandler.log("Zeige Abonnenten-Statistik...");
        SubscriberStatisticsDialog dialog = new SubscriberStatisticsDialog(this, downloadManager.getDatabaseManager());
        dialog.setVisible(true);
    }

    private void handleDoAllButton() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::handleDoAllButton);
            return;
        }

        if (overallProcessRunning || downloadManager.isDownloadRunning() || conversionManager.isConversionRunning()) {
            logHandler.log("Gesamtprozess nicht gestartet: Es l\u00e4uft bereits eine Operation.");
            return;
        }

        overallProcessRunning = true;
        logHandler.log("Starte automatisierten Gesamtprozess...");
        disableAllButtons();
        downloadManager.resetSubscribersCounters();
        downloadManager.setDoAllAtOnce(true);

        Thread allProcessesThread = new Thread(() -> {
            try {
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
                    overallProcessRunning = false;
                    logHandler.log("Gesamtprozess erfolgreich abgeschlossen!");
                    downloadManager.setDoAllAtOnce(false);
                    enableAllButtons();
                    int mql4Count = downloadManager.getMql4SubscribersDownloadedCount();
                    int mql5Count = downloadManager.getMql5SubscribersDownloadedCount();
                    JOptionPane.showMessageDialog(this,
                        "Gesamtprozess abgeschlossen:\n" + mql5Count + " MQL5-Signale mit Abonnenten und " + mql4Count + " MQL4-Signale mit Abonnenten geladen.",
                        "Ergebnis Gesamtprozess",
                        JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                logHandler.logError("Fehler im Gesamtprozess: " + e.getMessage(), e);
                downloadManager.setDoAllAtOnce(false);
                SwingUtilities.invokeLater(() -> {
                    overallProcessRunning = false;
                    enableAllButtons();
                });
            }
        }, "mql-gesamtprozess");

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
        buttonManager.getSubscribersOnlyCheckbox().setEnabled(false);
        statisticsButton.setEnabled(false);
        abonnentenStatistikButton.setEnabled(false);
        autoModeToggleButton.setEnabled(false);
    }

    private void enableAllButtons() {
        buttonManager.resetButtons();
        buttonManager.getDoAllButton().setEnabled(true);
        statisticsButton.setEnabled(true);
        abonnentenStatistikButton.setEnabled(true);
        autoModeToggleButton.setEnabled(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Datei");
        
        JMenuItem setupItem = new JMenuItem("Einstellungen");
        setupItem.addActionListener(e -> showSetupDialog());
        
        JMenuItem statsItem = new JMenuItem("Download-Statistik");
        statsItem.addActionListener(e -> showStatisticsDialog());

        JMenuItem subStatsItem = new JMenuItem("Abonnenten-Statistik");
        subStatsItem.addActionListener(e -> showSubscriberStatisticsDialog());
        
        fileMenu.add(setupItem);
        fileMenu.add(statsItem);
        fileMenu.add(subStatsItem);
        menuBar.add(fileMenu);
        
        return menuBar;
    }

    private void showSetupDialog() {
        SetupDialog dialog = new SetupDialog(this, configManager);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setPreferredLookAndFeel();
            MqlDownloaderGui gui = new MqlDownloaderGui();
            gui.setVisible(true);
        });
    }

    private static void setPreferredLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            logger.error("Look-and-Feel konnte nicht gesetzt werden", e);
        }
    }

    public void addSubscriberChange(String change) {
        SwingUtilities.invokeLater(() -> {
            subscriberChangesListModel.addElement(change);
            int lastIndex = subscriberChangesListModel.getSize() - 1;
            if (lastIndex >= 0) {
                subscriberChangesList.ensureIndexIsVisible(lastIndex);
            }
        });
    }

    public void clearSubscriberChanges() {
        SwingUtilities.invokeLater(() -> {
            subscriberChangesListModel.clear();
        });
    }

    private static class SubscriberChangeCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text = String.valueOf(value);
            label.setFont(list.getFont());
            
            if (text.startsWith("[NEW]")) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    label.setForeground(new Color(0, 120, 215));
                }
            } else if (text.contains(":+") || text.contains(": +")) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    label.setForeground(new Color(40, 140, 69));
                }
            } else if (text.contains(":-") || text.contains(": -")) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    label.setForeground(new Color(200, 45, 60));
                }
            } else if (!isSelected) {
                label.setForeground(list.getForeground());
            }
            
            label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            return label;
        }
    }
}
