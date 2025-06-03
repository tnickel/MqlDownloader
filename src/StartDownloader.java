import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManager;
import config.ConfigurationManager;
import config.Credentials;
import downloader.SignalDownloader;
import logging.LoggerManager;
import java.io.IOException;

public class StartDownloader {
    private static final Logger logger = LogManager.getLogger(StartDownloader.class);
    private final ConfigurationManager configManager;
    private WebDriver driver;
    private WebDriverManager webDriverManager;
    private SignalDownloader downloader;

    public StartDownloader() {
        this.configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        configManager.initializeDirectories();
        
        // Logger initialisieren
        LoggerManager.initializeLogger(configManager.getLogConfigPath());
        
        // WebDriverManager initialisieren
        this.webDriverManager = new WebDriverManager(configManager.getDownloadPath());
        initializeWebDriver();
    }

    /**
     * Initialisiert den WebDriver mit robuster Fehlerbehandlung
     */
    private void initializeWebDriver() {
        try {
            logger.info("Initialisiere WebDriver...");
            this.driver = webDriverManager.initializeDriver();
            logger.info("WebDriver erfolgreich initialisiert");
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des WebDrivers: {}", e.getMessage(), e);
            throw new RuntimeException("WebDriver-Initialisierung fehlgeschlagen", e);
        }
    }

    /**
     * Initialisiert den SignalDownloader mit robuster Konfiguration
     * 
     * @throws IOException bei Konfigurationsfehlern
     */
    public void initializeDownloader() throws IOException {
        try {
            // Prüfe ob WebDriver noch gesund ist
            if (!webDriverManager.isDriverHealthy(driver)) {
                logger.warn("WebDriver ist nicht gesund, starte Neuinitialisierung...");
                closeDriver();
                initializeWebDriver();
            }
            
            Credentials credentials = configManager.getCredentials();
            
            // Verwende die echten Credentials falls verfügbar, sonst Platzhalter
            if (credentials.getUsername().isEmpty() || credentials.getPassword().isEmpty()) {
                logger.warn("Keine gültigen Credentials konfiguriert, verwende Standardwerte");
                credentials = new Credentials("username", "password");
            }
            
            this.downloader = new SignalDownloader(driver, configManager, credentials);
            logger.info("SignalDownloader erfolgreich initialisiert");
            
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des SignalDownloaders: {}", e.getMessage(), e);
            throw new IOException("SignalDownloader-Initialisierung fehlgeschlagen", e);
        }
    }

    /**
     * Wechselt die MQL-Version mit robuster Fehlerbehandlung
     * 
     * @param version Die gewünschte MQL-Version ("mt4" oder "mt5")
     * @throws IOException bei Konfigurationsfehlern
     */
    public void switchMqlVersion(String version) throws IOException {
        try {
            if (downloader != null) {
                downloader.setMqlVersion(version);
                logger.info("MQL Version erfolgreich gewechselt zu: {}", version);
            } else {
                logger.error("Downloader wurde noch nicht initialisiert");
                throw new IllegalStateException("Downloader muss erst initialisiert werden");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Wechseln der MQL-Version zu {}: {}", version, e.getMessage(), e);
            throw new IOException("MQL-Versionswechsel fehlgeschlagen", e);
        }
    }

    /**
     * Startet den Download-Prozess mit robuster Fehlerbehandlung
     */
    public void startDownload() {
        try {
            if (downloader == null) {
                logger.error("Downloader wurde nicht initialisiert");
                throw new IllegalStateException("Downloader muss erst initialisiert werden");
            }
            
            // Prüfe WebDriver-Gesundheit vor dem Start
            if (!webDriverManager.isDriverHealthy(driver)) {
                logger.warn("WebDriver ist nicht gesund vor Download-Start, versuche Recovery...");
                
                WebDriver newDriver = webDriverManager.recoverWebDriver(driver);
                if (newDriver != null) {
                    this.driver = newDriver;
                    logger.info("WebDriver erfolgreich wiederhergestellt");
                    
                    // Downloader mit neuem WebDriver neu initialisieren
                    initializeDownloader();
                } else {
                    throw new RuntimeException("WebDriver-Recovery fehlgeschlagen");
                }
            }
            
            logger.info("Starte Download-Prozess...");
            downloader.startDownloadProcess();
            logger.info("Download-Prozess abgeschlossen");
            
        } catch (Exception e) {
            logger.error("Fehler im Download-Prozess: {}", e.getMessage(), e);
            
            // Versuche herauszufinden, ob es ein kritischer Fehler war
            if (isCriticalDownloadError(e)) {
                logger.error("Kritischer Fehler erkannt - beende Anwendung");
                throw new RuntimeException("Kritischer Download-Fehler", e);
            } else {
                logger.warn("Nicht-kritischer Fehler - Download-Prozess wurde gestoppt");
                // Könnte hier Recovery versuchen oder Benutzer benachrichtigen
            }
        }
    }

    /**
     * Prüft, ob es sich um einen kritischen Download-Fehler handelt
     * 
     * @param e Die aufgetretene Exception
     * @return true wenn es ein kritischer Fehler ist
     */
    private boolean isCriticalDownloadError(Exception e) {
        String message = e.getMessage().toLowerCase();
        
        return message.contains("webdriver") && message.contains("failed") ||
               message.contains("chrome not reachable") ||
               message.contains("session not created") ||
               message.contains("out of memory") ||
               message.contains("no internet") ||
               message.contains("network unreachable");
    }

    /**
     * Schließt den WebDriver sauber mit Cleanup
     */
    public void closeDriver() {
        try {
            if (driver != null) {
                logger.info("Schließe WebDriver...");
                driver.quit();
                logger.info("WebDriver erfolgreich geschlossen");
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Schließen des WebDrivers: {}", e.getMessage());
        } finally {
            driver = null;
            
            // Cleanup der Session-Daten
            if (webDriverManager != null) {
                try {
                    webDriverManager.cleanupSession();
                    logger.info("WebDriver-Session erfolgreich bereinigt");
                } catch (Exception e) {
                    logger.warn("Fehler beim Bereinigen der WebDriver-Session: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Führt einen vollständigen Neustart des WebDrivers durch
     * Nützlich bei schwerwiegenden Problemen
     */
    public void restartWebDriver() {
        logger.info("Führe vollständigen WebDriver-Neustart durch...");
        
        // Schließe aktuellen WebDriver
        closeDriver();
        
        // Warte kurz
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Initialisiere neu
        initializeWebDriver();
        
        // Downloader neu initialisieren falls vorhanden
        if (downloader != null) {
            try {
                initializeDownloader();
                logger.info("WebDriver-Neustart erfolgreich abgeschlossen");
            } catch (IOException e) {
                logger.error("Fehler beim Neuinitialisieren des Downloaders nach WebDriver-Neustart: {}", e.getMessage());
                throw new RuntimeException("Downloader-Neuinitialisierung fehlgeschlagen", e);
            }
        }
    }

    /**
     * Prüft den Gesundheitszustand des Systems
     * 
     * @return true wenn alle Komponenten funktionsfähig sind
     */
    public boolean isSystemHealthy() {
        try {
            // Prüfe WebDriver
            if (driver == null || !webDriverManager.isDriverHealthy(driver)) {
                logger.warn("WebDriver ist nicht gesund");
                return false;
            }
            
            // Prüfe Downloader
            if (downloader == null) {
                logger.warn("Downloader ist nicht initialisiert");
                return false;
            }
            
            // Prüfe Konfiguration
            if (configManager == null) {
                logger.warn("ConfigurationManager ist nicht verfügbar");
                return false;
            }
            
            logger.debug("System-Gesundheitscheck erfolgreich");
            return true;
            
        } catch (Exception e) {
            logger.error("Fehler beim System-Gesundheitscheck: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Stoppt den aktuellen Download-Prozess sauber
     */
    public void stopDownload() {
        if (downloader != null) {
            logger.info("Stoppe Download-Prozess...");
            downloader.setStopFlag(true);
        }
    }

    /**
     * Getter für den ConfigurationManager
     * 
     * @return ConfigurationManager-Instanz
     */
    public ConfigurationManager getConfigManager() {
        return configManager;
    }

    /**
     * Getter für den WebDriverManager
     * 
     * @return WebDriverManager-Instanz  
     */
    public WebDriverManager getWebDriverManager() {
        return webDriverManager;
    }

    /**
     * Hauptmethode für Standalone-Ausführung
     * 
     * @param args Kommandozeilenargumente
     */
    public static void main(String[] args) {
        StartDownloader starter = null;
        
        try {
            logger.info("Starte MQL Signal Downloader...");
            starter = new StartDownloader();
            
            // Initialisiere Downloader
            starter.initializeDownloader();
            
            // Starte Download (nur bei direkter Ausführung)
            if (args.length > 0 && "autostart".equals(args[0])) {
                starter.startDownload();
            } else {
                logger.info("StartDownloader bereit - verwende GUI zum Starten des Downloads");
            }
            
        } catch (Exception e) {
            logger.error("Kritischer Fehler beim Starten der Anwendung: {}", e.getMessage(), e);
            
            // Cleanup bei kritischen Fehlern
            if (starter != null) {
                starter.closeDriver();
            }
            
            System.exit(1);
        } finally {
            // Cleanup bei normalem Ende (nur bei Autostart)
            if (args.length > 0 && "autostart".equals(args[0]) && starter != null) {
                starter.closeDriver();
            }
        }
    }
}