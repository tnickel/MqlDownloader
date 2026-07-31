package gui;

import java.awt.Color;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManager;
import config.ConfigurationManager;
import downloader.SignalDownloader;
import utils.MqlDownloadProtokoll;

public class DownloadManager {
    private static final Logger logger = LogManager.getLogger(DownloadManager.class);
    private final ConfigurationManager configManager;
    private final LogHandler logHandler;
    private final ButtonPanelManager buttonManager;
    private final MqlDownloadProtokoll downloadProtokoll;
    private final MqlDownloaderGui gui;
    private final database.DatabaseManager databaseManager;
    private WebDriver currentDriver;
    private volatile SignalDownloader activeDownloader; // Fix #1: Stop-Signal-Propagation
    private volatile boolean stopRequested;
    private Thread downloadThread;
    private boolean limitReachedLogged = false;
    private int mql4SubscribersDownloadedCount = 0;
    private int mql5SubscribersDownloadedCount = 0;
    private boolean isDoAllAtOnce = false;

    public DownloadManager(ConfigurationManager configManager, LogHandler logHandler,
                           ButtonPanelManager buttonManager, MqlDownloaderGui gui) {
        this.configManager = configManager;
        this.logHandler = logHandler;
        this.buttonManager = buttonManager;
        this.gui = gui;
        this.downloadProtokoll = new MqlDownloadProtokoll(configManager.getRootDirPath() + "\\download");
        this.databaseManager = new database.DatabaseManager(configManager.getRootDirPath());
    }

    public database.DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void startDownload(String version) {
        logHandler.log("=== STARTE " + version + " DOWNLOAD-PROZESS ===");
        stopRequested = false;
        limitReachedLogged = false; // Reset bei neuem Download
        buttonManager.updateCounter(version, 0);
        setupUIForDownload(version);

        String downloadPath = configManager.getRootDirPath() + "\\download\\" + version.toLowerCase();
        logHandler.log("Download-Verzeichnis: " + downloadPath);
        configManager.setDownloadPath(downloadPath);
        
        // Protokoll f\u00fcr die aktuelle MQL-Version zur\u00fccksetzen
        String mqlVersionProtokoll = version.toLowerCase();
        downloadProtokoll.resetProtokoll(mqlVersionProtokoll);
        downloadProtokoll.log(mqlVersionProtokoll, "=== DOWNLOAD-PROZESS GESTARTET ===");
        
        // MQL-Version auf mt4 oder mt5 setzen
        String mqlVersion = version.equals("MQL4") ? "mt4" : "mt5";
        int limit = version.equals("MQL4") ? configManager.getMql4Limit() : configManager.getMql5Limit();
        
        try {
            configManager.setMqlVersion(mqlVersion);
            logHandler.log("MQL-Version: " + mqlVersion + " | Limit: " + limit + " Provider | URL: " + configManager.getBaseUrl());
        } catch (IOException e) {
            logHandler.logError("Fehler beim Setzen der MQL-Version: " + e.getMessage(), e);
        }
        
        downloadThread = new Thread(() -> {
            try {
                logging.LoggerManager.safeLog("Initialisiere Verzeichnisse...");
                configManager.initializeDirectories();
                
                logging.LoggerManager.safeLog("Setze WebDriver auf...");
                WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
                currentDriver = webDriverManager.initializeDriver();

                logging.LoggerManager.safeLog("Starte Download Prozess...");
                SignalDownloader downloader = new SignalDownloader(currentDriver, configManager, configManager.getCredentials());
                downloader.setStopFlag(stopRequested);
                downloader.setDownloadProtokoll(downloadProtokoll);
                downloader.setDatabaseManager(databaseManager);
                downloader.setSubscriberChangeCallback(change -> gui.addSubscriberChange(change));
                activeDownloader = downloader; // Fix #1: Referenz halten fuer Stop-Propagation
                
                // Fortschritt der Schutzwartezeit an GUI-Progressbar \u00fcbermitteln
                downloader.setWaitCallback(new downloader.WaitCallback() {
                    @Override
                    public void onWait(int elapsedMs, int totalMs) {
                        SwingUtilities.invokeLater(() -> {
                            JProgressBar progressBar = buttonManager.getConvertProgress();
                            JLabel statusLabel = buttonManager.getConvertStatusLabel();
                            
                            if (!progressBar.isVisible()) {
                                progressBar.setVisible(true);
                                statusLabel.setVisible(true);
                            }
                            
                            int percent = (int) ((elapsedMs / (double) totalMs) * 100);
                            progressBar.setValue(percent);
                            
                            double elapsedSec = elapsedMs / 1000.0;
                            double totalSec = totalMs / 1000.0;
                            String text = String.format("Wartezeit wegen MQL-Server-Schutz: %.1fs / %.1fs (%.0f%%)", 
                                                        elapsedSec, totalSec, (double)percent);
                            progressBar.setString(text);
                            statusLabel.setText("Anti-Overload-Schutz aktiv...");
                        });
                    }

                    @Override
                    public void onWaitFinished() {
                        SwingUtilities.invokeLater(() -> {
                            buttonManager.getConvertProgress().setVisible(false);
                            buttonManager.getConvertStatusLabel().setVisible(false);
                        });
                    }

                    @Override
                    public void onStatusUpdate(String status) {
                        SwingUtilities.invokeLater(() -> {
                            buttonManager.getCurrentFileField().setText(status);
                        });
                    }
                });
                
                // VERBESSERTE ProgressCallback mit thread-sicherem Logging
                downloader.setProgressCallback(count -> {
                    SwingUtilities.invokeLater(() -> {
                        buttonManager.updateCounter(version, count);
                        
                        // Detailliertes Progress-Logging (nur f\u00fcr wichtige Meilensteine)
                        if (count % 5 == 0 || count <= 3) { // Alle 5 Provider oder die ersten 3
                            logHandler.log(String.format("Fortschritt %s: %d/%d Provider verarbeitet", 
                                         version, count, limit));
                        }
                        
                        // Limit-Check nur einmal loggen
                        if (count >= limit && !limitReachedLogged) {
                            limitReachedLogged = true;
                            stopRequested = true;
                            
                            logHandler.log("=== " + version + " DOWNLOAD ABGESCHLOSSEN ===");
                            logHandler.log("SUCCESS - LIMIT ERREICHT: " + count + " von " + limit + " Providern erfolgreich verarbeitet");
                            downloadProtokoll.log(mqlVersionProtokoll, 
                                "=== DOWNLOAD ERFOLGREICH ABGESCHLOSSEN === Limit erreicht: " + count + "/" + limit + " Provider");
                            
                            // Trigger cleanup in separate thread um UI nicht zu blockieren
                            new Thread(this::cleanupDownload).start();
                        }
                    });
                });
                
                downloader.startDownloadProcess();

                // Subscriber-Zaehler je Version aufaddieren
                int subCount = downloader.getSubscribersDownloadedCount();
                if (version.equals("MQL4")) {
                    mql4SubscribersDownloadedCount += subCount;
                } else {
                    mql5SubscribersDownloadedCount += subCount;
                }

            } catch (Exception e) {
                if (!stopRequested) {
                    String errorMsg = "Fehler w\u00e4hrend " + version + " Download: " + e.getMessage();
                    logHandler.logError(errorMsg, e);
                    logging.LoggerManager.safeLogError(errorMsg, e);
                    downloadProtokoll.log(mqlVersionProtokoll, "=== DOWNLOAD MIT FEHLER BEENDET === " + e.getMessage());
                    
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                            "Download-Fehler bei " + version + ":\n" + e.getMessage(),
                            "Download Fehler",
                            JOptionPane.ERROR_MESSAGE);
                        cleanupDownload();
                    });
                }
            } finally {
                if (!limitReachedLogged) { // Nur loggen wenn nicht bereits durch Limit-Erreichen geloggt
                    SwingUtilities.invokeLater(() -> {
                        if (stopRequested) {
                            logHandler.log("=== " + version + " DOWNLOAD GESTOPPT ===");
                            downloadProtokoll.log(mqlVersionProtokoll, "=== DOWNLOAD MANUELL GESTOPPT ===");
                        } else {
                            logHandler.log("=== " + version + " DOWNLOAD BEENDET ===");
                            downloadProtokoll.log(mqlVersionProtokoll, "=== DOWNLOAD REGUL\u00c4R BEENDET ===");
                        }
                    });
                }
                
                // Cleanup falls noch nicht durch Limit-Erreichen ausgel\u00f6st
                if (!limitReachedLogged) {
                    cleanupDownload();
                }
            }
        });
        downloadThread.start();
    }

    public void stopDownload() {
        logHandler.log("STOPPE Download-Prozess...");
        stopRequested = true;
        // Fix #1: Stop-Signal auch an aktiven Downloader propagieren
        SignalDownloader currentDownloader = activeDownloader;
        if (currentDownloader != null) {
            currentDownloader.setStopFlag(true);
        }
        buttonManager.getStopButton().setEnabled(false);
        
        new Thread(() -> {
            try {
                // Bestimme die aktuelle MQL-Version
                String mqlVersionProtokoll = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                
                // Protokolliere den manuellen Stop
                downloadProtokoll.log(mqlVersionProtokoll, "=== DOWNLOAD MANUELL GESTOPPT ===");
                
                SwingUtilities.invokeLater(() -> {
                    logHandler.log("=== DOWNLOAD-PROZESS MANUELL GESTOPPT ===");
                });
                
                cleanupDownload();
            } catch (Exception e) {
                logHandler.logError("Fehler beim Stoppen des Downloads: " + e.getMessage(), e);
                logging.LoggerManager.safeLogError("Fehler beim Stoppen des Downloads", e);
            }
        }).start();
    }

    private void setupUIForDownload(String version) {
        JButton activeButton = version.equals("MQL4") ? 
            buttonManager.getMql4Button() : buttonManager.getMql5Button();
        JButton inactiveButton = version.equals("MQL4") ? 
            buttonManager.getMql5Button() : buttonManager.getMql4Button();
        
        buttonManager.getMql4LimitField().setEnabled(false);
        buttonManager.getMql5LimitField().setEnabled(false);
        buttonManager.getDownloadDaysField().setEnabled(false);
        
        activeButton.setBackground(new Color(144, 238, 144)); // Hellgr\u00fcn f\u00fcr aktiven Download
        activeButton.setEnabled(false);
        inactiveButton.setEnabled(false);
        inactiveButton.setBackground(new Color(200, 200, 200)); // Grau f\u00fcr inaktiven Button
        buttonManager.getStopButton().setEnabled(true);
        
        // Update Button Text to show active state
        activeButton.setText(version + " (L\u00c4UFT...)");
    }

    /**
     * VERBESSERTE cleanupDownload Methode mit thread-sicherem Logging
     */
    private void cleanupDownload() {
        activeDownloader = null; // Fix #1: Referenz freigeben
        if (currentDriver != null) {
            try {
                logging.LoggerManager.safeLog("Schlie\u00dfe WebDriver...");
                currentDriver.quit();
                logging.LoggerManager.safeLog("WebDriver erfolgreich geschlossen");
            } catch (Exception e) {
                logging.LoggerManager.safeLogError("Fehler beim Schlie\u00dfen des WebDrivers: " + e.getMessage(), e);
            } finally {
                currentDriver = null;
            }
        }
        
        SwingUtilities.invokeLater(() -> {
            // Reset UI state
            buttonManager.resetButtons();
            buttonManager.getMql4LimitField().setEnabled(true);
            buttonManager.getMql5LimitField().setEnabled(true);
            buttonManager.getDownloadDaysField().setEnabled(true);
            buttonManager.getConvertProgress().setVisible(false);
            buttonManager.getConvertStatusLabel().setVisible(false);
            buttonManager.getCurrentFileField().setText("Inaktiv / Bereit");
            
            // Reset button texts
            buttonManager.getMql4Button().setText("MQL4 Download");
            buttonManager.getMql5Button().setText("MQL5 Download");
            
            logHandler.log("UI-Status zur\u00fcckgesetzt - Bereit f\u00fcr neue Downloads");
        });
    }

    public boolean isDownloadRunning() {
        return downloadThread != null && downloadThread.isAlive();
    }

    public void waitForDownloadCompletion() {
        try {
            while (downloadThread != null && downloadThread.isAlive()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void resetSubscribersCounters() {
        mql4SubscribersDownloadedCount = 0;
        mql5SubscribersDownloadedCount = 0;
    }

    public void setDoAllAtOnce(boolean value) {
        this.isDoAllAtOnce = value;
    }

    public int getMql4SubscribersDownloadedCount() {
        return mql4SubscribersDownloadedCount;
    }

    public int getMql5SubscribersDownloadedCount() {
        return mql5SubscribersDownloadedCount;
    }

    /**
     * Gibt detaillierte Informationen ueber den aktuellen Download-Status
     */
    public String getDownloadStatus() {
        if (isDownloadRunning()) {
            String version = configManager.getMqlVersion().startsWith("mt4") ? "MQL4" : "MQL5";
            int limit = version.equals("MQL4") ? configManager.getMql4Limit() : configManager.getMql5Limit();
            return String.format("%s Download laeuft (Limit: %d Provider)", version, limit);
        } else {
            return "Kein Download aktiv";
        }
    }
}