package config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigurationManager {
    private final String rootDirPath;
    private final String configDirPath;
    private final String mqlConfigFilePath;
    private final String logDirPath;
    private final String downloadPath;
    private String baseUrl;
    private String currentDownloadPath;
    private Credentials credentials;
    private static final Logger logger = LogManager.getLogger(ConfigurationManager.class);

    // Property-Keys als Konstanten definieren
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_DOWNLOAD_PATH = "downloadPath";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_MQL_VERSION = "mqlVersion";

    public ConfigurationManager(String rootDirPath) {
        this.rootDirPath = rootDirPath;
        this.configDirPath = rootDirPath + "\\config";
        this.mqlConfigFilePath = configDirPath + "\\MqldownloaderConfig.txt";
        this.logDirPath = rootDirPath + "\\logs";
        this.downloadPath = rootDirPath + "\\download";
        loadCredentials();
    }

    

    private void saveProperty(String key, String value) {
        Properties props = loadProperties();
        props.setProperty(key, value);
        saveProperties(props, "MQL Downloader Konfiguration");
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        File configFile = new File(mqlConfigFilePath);
        if (configFile.exists()) {
            try {
                props.load(Files.newBufferedReader(configFile.toPath()));
            } catch (IOException e) {
                logger.error("Fehler beim Laden der Konfiguration", e);
            }
        }
        return props;
    }

    private void saveProperties(Properties props, String comments) {
        try (FileWriter writer = new FileWriter(mqlConfigFilePath)) {
            props.store(writer, comments);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern der Konfiguration", e);
        }
    }

    private void loadCredentials() {
        Properties props = loadProperties();
        String username = props.getProperty(KEY_USERNAME, "");
        String password = props.getProperty(KEY_PASSWORD, "");
        this.credentials = new Credentials(username, password);
    }

    public void setCredentials(String username, String password) {
        this.credentials = new Credentials(username, password);
        Properties props = loadProperties();
        props.setProperty(KEY_USERNAME, username);
        props.setProperty(KEY_PASSWORD, password);
        saveProperties(props, "MQL Downloader Konfiguration");
        logger.info("Credentials aktualisiert f�r Benutzer: " + username);
    }

    public Credentials getCredentials() {
        if (credentials == null) {
            loadCredentials();
        }
        return credentials;
    }

  

    public void initializeDirectories() {
        createDirectory(configDirPath);
        createDirectory(logDirPath);
        createDirectory(downloadPath);
        initializeDefaultConfig();
    }

    private void initializeDefaultConfig() {
        File configFile = new File(mqlConfigFilePath);
        if (!configFile.exists()) {
            Properties props = new Properties();
            props.setProperty(KEY_BASE_URL, "https://www.mql5.com/en/signals/mt5/list");
            props.setProperty(KEY_DOWNLOAD_PATH, downloadPath);
            props.setProperty(KEY_USERNAME, "");
            props.setProperty(KEY_PASSWORD, "");
            saveProperties(props, "MQL Downloader Standard-Konfiguration");
            logger.info("Standard-Konfigurationsdatei erstellt: " + mqlConfigFilePath);
        }
    }

    private void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Verzeichnis erstellt: " + path);
            } else {
                logger.error("Konnte Verzeichnis nicht erstellen: " + path);
            }
        }
    }

    public String getLogConfigPath() {
        return rootDirPath + "\\log4j2.xml";
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public String getConfigDirPath() {
        return configDirPath;
    }

    public String getRootDirPath() {
        return rootDirPath;
    }

    public String getLogDirPath() {
        return logDirPath;
    }

    public String getMqlConfigFilePath() {
        return mqlConfigFilePath;
    }

    public void resetConfiguration() {
        Properties props = new Properties();
        props.setProperty(KEY_BASE_URL, "https://www.mql5.com/en/signals/mt5/list");
        props.setProperty(KEY_DOWNLOAD_PATH, downloadPath);
        props.setProperty(KEY_USERNAME, "");
        props.setProperty(KEY_PASSWORD, "");
        saveProperties(props, "MQL Downloader Konfiguration - Zur�ckgesetzt auf Standardwerte");
        
        logger.info("Konfiguration wurde auf Standardwerte zur�ckgesetzt");
        this.baseUrl = null;
        this.currentDownloadPath = null;
        this.credentials = new Credentials("", "");
    }
    public void setBaseUrl(String url) {
        this.baseUrl = url;
        // Speichern der URL in der Konfigurationsdatei
        Properties props = new Properties();
        File mqlConfigFile = new File(mqlConfigFilePath);

        try {
            if (mqlConfigFile.exists()) {
                props.load(Files.newBufferedReader(mqlConfigFile.toPath()));
            }
            props.setProperty("baseUrl", url);
            try (FileWriter writer = new FileWriter(mqlConfigFile)) {
                props.store(writer, "MQL Downloader Konfiguration");
            }
            logger.info("Base URL aktualisiert auf: " + url);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern der Base URL", e);
        }
    }

    // Neue Methode zum Setzen des Download-Pfads
    public void setDownloadPath(String path) {
        this.currentDownloadPath = path;
        // Erstellen des Verzeichnisses, falls es nicht existiert
        createDirectory(path);
        
        // Speichern des Pfads in der Konfigurationsdatei
        Properties props = new Properties();
        File mqlConfigFile = new File(mqlConfigFilePath);

        try {
            if (mqlConfigFile.exists()) {
                props.load(Files.newBufferedReader(mqlConfigFile.toPath()));
            }
            props.setProperty("downloadPath", path);
            try (FileWriter writer = new FileWriter(mqlConfigFile)) {
                props.store(writer, "MQL Downloader Konfiguration");
            }
            logger.info("Download-Pfad aktualisiert auf: " + path);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern des Download-Pfads", e);
        }
    }

    // Getter f�r die Base URL
    public String getBaseUrl() {
        if (baseUrl == null) {
            try {
                Properties props = new Properties();
                File mqlConfigFile = new File(mqlConfigFilePath);
                if (mqlConfigFile.exists()) {
                    props.load(Files.newBufferedReader(mqlConfigFile.toPath()));
                    baseUrl = props.getProperty("baseUrl");
                }
            } catch (IOException e) {
                logger.error("Fehler beim Laden der Base URL", e);
            }
        }
        return baseUrl;
    }

    // Getter f�r den aktuellen Download-Pfad
    public String getCurrentDownloadPath() {
        if (currentDownloadPath == null) {
            try {
                Properties props = new Properties();
                File mqlConfigFile = new File(mqlConfigFilePath);
                if (mqlConfigFile.exists()) {
                    props.load(Files.newBufferedReader(mqlConfigFile.toPath()));
                    currentDownloadPath = props.getProperty("downloadPath", downloadPath);
                }
            } catch (IOException e) {
                logger.error("Fehler beim Laden des Download-Pfads", e);
                currentDownloadPath = downloadPath; // Fallback zum Standard-Pfad
            }
        }
        return currentDownloadPath;
    }
    public void setMqlVersion(String version) throws IOException {
        if (!version.equals("mt4") && !version.equals("mt5")) {
            throw new IllegalArgumentException("MQL-Version muss entweder 'mt4' oder 'mt5' sein");
        }

        Properties props = loadProperties();
        props.setProperty(KEY_MQL_VERSION, version);
        props.setProperty(KEY_BASE_URL, String.format("https://www.mql5.com/en/signals/%s/list", version));
        saveProperties(props, "MQL Downloader Konfiguration");
        
        this.baseUrl = String.format("https://www.mql5.com/en/signals/%s/list", version);
        logger.info("MQL-Version aktualisiert auf: " + version);
    }

    public String getMqlVersion() {
        Properties props = loadProperties();
        return props.getProperty(KEY_MQL_VERSION, "mt5"); // Standard ist MT5
    }
    public String getMqlBaseUrl() {
        Properties props = loadProperties();
        if (baseUrl == null) {
            baseUrl = props.getProperty(KEY_BASE_URL, "https://www.mql5.com/en/signals/mt5/list");
        }
        return baseUrl;
    }

}