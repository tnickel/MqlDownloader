package gui;

import java.awt.Color;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JOptionPane;
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
    private WebDriver currentDriver;
    private volatile boolean stopRequested;
    private Thread downloadThread;
    private boolean limitReachedLogged = false; // Flag um mehrfaches Loggen zu verhindern

    public DownloadManager(ConfigurationManager configManager, LogHandler logHandler, ButtonPanelManager buttonManager) {
        this.configManager = configManager;
        this.logHandler = logHandler;
        this.buttonManager = buttonManager;
        this.downloadProtokoll = new MqlDownloadProtokoll(configManager.getRootDirPath() + "\\download");
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
        
        // Protokoll für die aktuelle MQL-Version zurücksetzen
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
                
                // VERBESSERTE ProgressCallback mit thread-sicherem Logging
                downloader.setProgressCallback(count -> {
                    SwingUtilities.invokeLater(() -> {
                        buttonManager.updateCounter(version, count);
                        
                        // Detailliertes Progress-Logging (nur für wichtige Meilensteine)
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

            } catch (Exception e) {
                if (!stopRequested) {
                    String errorMsg = "Fehler während " + version + " Download: " + e.getMessage();
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
                            downloadProtokoll.log(mqlVersionProtokoll, "=== DOWNLOAD REGULÄR BEENDET ===");
                        }
                    });
                }
                
                // Cleanup falls noch nicht durch Limit-Erreichen ausgelöst
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
        
        activeButton.setBackground(new Color(144, 238, 144)); // Hellgrün für aktiven Download
        activeButton.setEnabled(false);
        inactiveButton.setEnabled(false);
        inactiveButton.setBackground(new Color(200, 200, 200)); // Grau für inaktiven Button
        buttonManager.getStopButton().setEnabled(true);
        
        // Update Button Text to show active state
        activeButton.setText(version + " (LÄUFT...)");
    }

    /**
     * VERBESSERTE cleanupDownload Methode mit thread-sicherem Logging
     */
    private void cleanupDownload() {
        if (currentDriver != null) {
            try {
                logging.LoggerManager.safeLog("Schließe WebDriver...");
                currentDriver.quit();
                logging.LoggerManager.safeLog("WebDriver erfolgreich geschlossen");
            } catch (Exception e) {
                logging.LoggerManager.safeLogError("Fehler beim Schließen des WebDrivers: " + e.getMessage(), e);
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
            
            // Reset button texts
            buttonManager.getMql4Button().setText("MQL4 Download");
            buttonManager.getMql5Button().setText("MQL5 Download");
            
            logHandler.log("UI-Status zurückgesetzt - Bereit für neue Downloads");
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
    
    /**
     * Gibt detaillierte Informationen über den aktuellen Download-Status
     */
    public String getDownloadStatus() {
        if (isDownloadRunning()) {
            String version = configManager.getMqlVersion().startsWith("mt4") ? "MQL4" : "MQL5";
            int limit = version.equals("MQL4") ? configManager.getMql4Limit() : configManager.getMql5Limit();
            
            return String.format("%s Download läuft (Limit: %d Provider)", version, limit);
        } else {
            return "Kein Download aktiv";
        }
    }
}