// StartDownloader.java
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
    private final WebDriver driver;
    private SignalDownloader downloader;

    public StartDownloader() {
        this.configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        configManager.initializeDirectories();
        
        // Logger initialisieren
        LoggerManager.initializeLogger(configManager.getLogConfigPath());
        
        // WebDriver initialisieren
        WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
        this.driver = webDriverManager.initializeDriver();
    }

    public void initializeDownloader() throws IOException {
        Credentials credentials = new Credentials("username", "password");
        this.downloader = new SignalDownloader(driver, configManager, credentials);
    }

    public void switchMqlVersion(String version) throws IOException {
        if (downloader != null) {
            downloader.setMqlVersion(version);
            logger.info("MQL Version gewechselt zu: " + version);
        }
    }

    public void startDownload() {
        try {
            if (downloader != null) {
                downloader.startDownloadProcess();
            } else {
                logger.error("Downloader wurde nicht initialisiert");
            }
        } catch (Exception e) {
            logger.error("Fehler im Hauptprozess", e);
        }
    }

    public void closeDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    public static void main(String[] args) {
        StartDownloader starter = new StartDownloader();
        try {
            starter.initializeDownloader();
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren", e);
            starter.closeDriver();
        }
    }
}