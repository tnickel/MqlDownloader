package gui;

import browser.WebDriverManager;
import config.ConfigurationManager;
import downloader.SignalDownloader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import javax.swing.*;
import java.awt.*;

public class DownloadManager {
    private static final Logger logger = LogManager.getLogger(DownloadManager.class);
    private final ConfigurationManager configManager;
    private final LogHandler logHandler;
    private final ButtonPanelManager buttonManager;
    private WebDriver currentDriver;
    private volatile boolean stopRequested;
    private Thread downloadThread;

    public DownloadManager(ConfigurationManager configManager, LogHandler logHandler, ButtonPanelManager buttonManager) {
        this.configManager = configManager;
        this.logHandler = logHandler;
        this.buttonManager = buttonManager;
    }

    public void startDownload(String version) {
        logHandler.log("Starte " + version + " Download Prozess...");
        stopRequested = false;
        buttonManager.updateCounter(version, 0);
        setupUIForDownload(version);

        String downloadPath = configManager.getRootDirPath() + "\\download\\" + version.toLowerCase();
        logHandler.log("Download Pfad: " + downloadPath);
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
                    buttonManager.updateCounter(version, count);
                    int limit = version.equals("MQL4") ? 
                        configManager.getMql4Limit() : 
                        configManager.getMql5Limit();
                    if (count >= limit) {
                        stopRequested = true;
                        SwingUtilities.invokeLater(() -> {
                            logHandler.log(version + " Download erfolgreich beendet. Limit von " + limit + " erreicht.");
                            cleanupDownload();
                        });
                    }
                });
                
                downloader.startDownloadProcess();

            } catch (Exception e) {
                if (!stopRequested) {
                    logHandler.logError("Fehler während " + version + " Download: " + e.getMessage(), e);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                            "Fehler beim Download: " + e.getMessage(),
                            "Fehler",
                            JOptionPane.ERROR_MESSAGE);
                        cleanupDownload();
                    });
                }
            } finally {
                cleanupDownload();
            }
        });
        downloadThread.start();
    }

    public void stopDownload() {
        logHandler.log("Stoppe Download Prozess...");
        stopRequested = true;
        buttonManager.getStopButton().setEnabled(false);
        
        new Thread(() -> {
            try {
                cleanupDownload();
                SwingUtilities.invokeLater(() -> {
                    logHandler.log("Download Prozess manuell gestoppt");
                });
            } catch (Exception e) {
                logHandler.logError("Fehler beim Stoppen des Downloads: " + e.getMessage(), e);
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
        
        activeButton.setBackground(new Color(144, 238, 144));
        activeButton.setEnabled(false);
        inactiveButton.setEnabled(false);
        inactiveButton.setBackground(new Color(200, 200, 200));
        buttonManager.getStopButton().setEnabled(true);
    }

    private void cleanupDownload() {
        if (currentDriver != null) {
            try {
                logger.info("Schließe WebDriver...");
                currentDriver.quit();
            } catch (Exception e) {
                logger.warn("Fehler beim Schließen des WebDrivers", e);
            } finally {
                currentDriver = null;
            }
        }
        
        SwingUtilities.invokeLater(() -> {
            buttonManager.resetButtons();
            buttonManager.getMql4LimitField().setEnabled(true);
            buttonManager.getMql5LimitField().setEnabled(true);
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
}