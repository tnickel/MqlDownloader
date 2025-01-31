package config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigurationManager {
    private final String rootDirPath;
    private final String configDirPath;
    private final String mqlConfigFilePath;
    private final String logDirPath;
    private final String downloadPath;
    private static final Logger logger = LogManager.getLogger(ConfigurationManager.class);

    public ConfigurationManager(String rootDirPath) {
        this.rootDirPath = rootDirPath;
        this.configDirPath = rootDirPath + "\\config";
        this.mqlConfigFilePath = configDirPath + "\\MqldownloaderConfig.txt";
        this.logDirPath = rootDirPath + "\\logs";
        this.downloadPath = rootDirPath + "\\download";
    }

    public String getMqlBaseUrl() throws IOException {
        Properties props = new Properties();
        File mqlConfigFile = new File(mqlConfigFilePath);

        if (mqlConfigFile.exists()) {
            props.load(Files.newBufferedReader(mqlConfigFile.toPath()));
            return props.getProperty("baseUrl", "https://www.mql5.com/en/signals/mt5");
        } else {
            try (FileWriter writer = new FileWriter(mqlConfigFile)) {
                props.setProperty("baseUrl", "https://www.mql5.com/en/signals/mt5");
                props.store(writer, "MQL5 Downloader Configuration");
            }
            return "https://www.mql5.com/en/signals/mt5";
        }
    }

    public Credentials getCredentials() throws IOException {
        Properties props = new Properties();
        File mqlConfigFile = new File(mqlConfigFilePath);

        if (mqlConfigFile.exists()) {
            props.load(Files.newBufferedReader(mqlConfigFile.toPath()));
            String username = props.getProperty("username");
            String password = props.getProperty("password");
            
            if (username == null || password == null) {
                logger.error("Benutzername oder Passwort nicht in Konfigurationsdatei gefunden");
                throw new IOException("Anmeldedaten nicht in Konfigurationsdatei gefunden");
            }
            
            return new Credentials(username, password);
        } else {
            logger.error("Konfigurationsdatei nicht gefunden: " + mqlConfigFilePath);
            throw new IOException("Konfigurationsdatei nicht gefunden");
        }
    }

    public void initializeDirectories() {
        createDirectory(configDirPath);
        createDirectory(logDirPath);
        createDirectory(downloadPath);
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
}
