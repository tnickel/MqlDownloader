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
            // Initialize configuration
            ConfigurationManager configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
            configManager.initializeDirectories();
            
            // Initialize logger
            LoggerManager.initializeLogger(configManager.getLogConfigPath());
            
            // Initialize WebDriver
            WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
            WebDriver driver = webDriverManager.initializeDriver();
            
            // Direktes Erstellen der Credentials, da wir jetzt nur MQL5-spezifische Konfiguration haben
            Credentials credentials = new Credentials("username", "password");
            
            // Start the download process
            try {
                SignalDownloader downloader = new SignalDownloader(driver, configManager, credentials);
                downloader.startDownloadProcess();
            } catch (IOException e) {
                logger.error("Error initializing SignalDownloader", e);
                if (driver != null) {
                    driver.quit();
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in main process", e);
        }
    }
}