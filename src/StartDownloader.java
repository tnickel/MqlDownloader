// StartDownloader.java
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
    
    public StartDownloader() {}
    
    public static void main(String[] args) {
        try {
            // Konfiguration initialisieren
            ConfigurationManager configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
            configManager.initializeDirectories();
            
            // Logger initialisieren
            LoggerManager.initializeLogger(configManager.getLogConfigPath());
            
            // WebDriver initialisieren
            WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
            WebDriver driver = webDriverManager.initializeDriver();
            
            // Anmeldedaten aus Konfiguration lesen
            Credentials credentials = configManager.getCredentials();
            
            // Download-Prozess starten
            try {
                SignalDownloader downloader = new SignalDownloader(driver, configManager, credentials);
                downloader.startDownloadProcess();
            } catch (IOException e) {
                logger.error("Fehler beim Initialisieren des SignalDownloaders", e);
                if (driver != null) {
                    driver.quit();
                }
            }
            
        } catch (Exception e) {
            logger.error("Fehler im Hauptprozess", e);
        }
    }
}