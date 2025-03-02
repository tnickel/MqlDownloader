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
    private static final String KEY_MIN_WAIT = "minWaitTime";
    private static final String KEY_MAX_WAIT = "maxWaitTime";
    private static final String KEY_MQL4_LIMIT = "mql4Limit";
    private static final String KEY_MQL5_LIMIT = "mql5Limit";
    
    private static final int DEFAULT_MIN_WAIT = 10000; // 10 seconds
    private static final int DEFAULT_MAX_WAIT = 30000; // 30 seconds
    private static final int DEFAULT_MQL4_LIMIT = 1000;
    private static final int DEFAULT_MQL5_LIMIT = 1000;

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
        logger.info("Credentials aktualisiert für Benutzer: " + username);
    }

    public Credentials getCredentials() {
        if (credentials == null) {
            loadCredentials();
        }
        return credentials;
    }

    public void setWaitTimes(int minWait, int maxWait) {
        if (minWait < 2000) { // Minimum 2 seconds
            minWait = 2000;
        }
        if (maxWait <= minWait) {
            maxWait = minWait + 1000; // Ensure max is always greater than min
        }
        
        Properties props = loadProperties();
        props.setProperty(KEY_MIN_WAIT, String.valueOf(minWait));
        props.setProperty(KEY_MAX_WAIT, String.valueOf(maxWait));
        saveProperties(props, "MQL Downloader Konfiguration");
        logger.info("Wait times updated - Min: " + minWait + "ms, Max: " + maxWait + "ms");
    }

    public int getMinWaitTime() {
        Properties props = loadProperties();
        return Integer.parseInt(props.getProperty(KEY_MIN_WAIT, String.valueOf(DEFAULT_MIN_WAIT)));
    }

    public int getMaxWaitTime() {
        Properties props = loadProperties();
        return Integer.parseInt(props.getProperty(KEY_MAX_WAIT, String.valueOf(DEFAULT_MAX_WAIT)));
    }

    public int getMql4Limit() {
        Properties props = loadProperties();
        return Integer.parseInt(props.getProperty(KEY_MQL4_LIMIT, String.valueOf(DEFAULT_MQL4_LIMIT)));
    }

    public void setMql4Limit(int limit) {
        if (limit < 1 || limit > 5000) {
            throw new IllegalArgumentException("MQL4 Limit muss zwischen 1 und 5000 liegen");
        }
        Properties props = loadProperties();
        props.setProperty(KEY_MQL4_LIMIT, String.valueOf(limit));
        saveProperties(props, "MQL Downloader Konfiguration");
        logger.info("MQL4 Limit aktualisiert auf: " + limit);
    }

    public int getMql5Limit() {
        Properties props = loadProperties();
        return Integer.parseInt(props.getProperty(KEY_MQL5_LIMIT, String.valueOf(DEFAULT_MQL5_LIMIT)));
    }

    public void setMql5Limit(int limit) {
        if (limit < 1 || limit > 5000) {
            throw new IllegalArgumentException("MQL5 Limit muss zwischen 1 und 5000 liegen");
        }
        Properties props = loadProperties();
        props.setProperty(KEY_MQL5_LIMIT, String.valueOf(limit));
        saveProperties(props, "MQL Downloader Konfiguration");
        logger.info("MQL5 Limit aktualisiert auf: " + limit);
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
            props.setProperty(KEY_MIN_WAIT, String.valueOf(DEFAULT_MIN_WAIT));
            props.setProperty(KEY_MAX_WAIT, String.valueOf(DEFAULT_MAX_WAIT));
            props.setProperty(KEY_MQL4_LIMIT, String.valueOf(DEFAULT_MQL4_LIMIT));
            props.setProperty(KEY_MQL5_LIMIT, String.valueOf(DEFAULT_MQL5_LIMIT));
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
        return rootDirPath + "\\config\\log4j2.xml";
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
        props.setProperty(KEY_MIN_WAIT, String.valueOf(DEFAULT_MIN_WAIT));
        props.setProperty(KEY_MAX_WAIT, String.valueOf(DEFAULT_MAX_WAIT));
        props.setProperty(KEY_MQL4_LIMIT, String.valueOf(DEFAULT_MQL4_LIMIT));
        props.setProperty(KEY_MQL5_LIMIT, String.valueOf(DEFAULT_MQL5_LIMIT));
        saveProperties(props, "MQL Downloader Konfiguration - Zurückgesetzt auf Standardwerte");
        
        logger.info("Konfiguration wurde auf Standardwerte zurückgesetzt");
        this.baseUrl = null;
        this.currentDownloadPath = null;
        this.credentials = new Credentials("", "");
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
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

    public void setDownloadPath(String path) {
        this.currentDownloadPath = path;
        createDirectory(path);
        
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