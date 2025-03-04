

package logging;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;


public class LoggerManager {
    private static final Logger logger = LogManager.getLogger(LoggerManager.class);
    private static boolean isInitialized = false;
    
    public static void initializeLogger(String configPath) {
    	
    	if (isInitialized) {
            logger.warn("LoggerManager is already initialized. Skipping re-initialization.");
            System.out.println("Warn:LoggerManager already initialized, I go on");
            return;
        }
        isInitialized = true;
    	
        System.out.println("Übergebener configPath: " + configPath);
        System.out.println("Arbeitsverzeichnis: " + System.getProperty("user.dir"));
        File log4jConfigFile = new File(configPath);
        System.out.println("Absoluter Pfad der Konfigurationsdatei: " + log4jConfigFile.getAbsolutePath());
        System.out.println("Datei existiert: " + log4jConfigFile.exists());
        System.out.println("Versuche Log4j zu initialisieren mit Konfigurationsdatei: " + configPath);
        
      
        if (log4jConfigFile.exists()) {
            try {
                System.out.println("Log4j-Konfigurationsdatei gefunden: " + log4jConfigFile.getAbsolutePath());
                Configurator.initialize(null, log4jConfigFile.getAbsolutePath());
                logger.info("Log4j configuration file loaded successfully.");
            } catch (Exception e) {
                System.err.println("Fehler beim Laden der Log4j-Konfiguration: " + e.getMessage());
                e.printStackTrace();
                configureDefaultLogger();
                logger.error("Could not load Log4j configuration file, using default configuration.", e);
            }
        } else {
            System.out.println("Log4j-Konfigurationsdatei nicht gefunden: " + configPath);
            System.out.println("Verwende Standard-Konfiguration...");
            configureDefaultLogger();
            logger.warn("Log4j configuration file not found, using default configuration.");
        }
    }

    private static void configureDefaultLogger() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(org.apache.logging.log4j.Level.ERROR);
        builder.setConfigurationName("DefaultConfig");

        // Create Console Appender
        builder.add(builder.newAppender("Console", "CONSOLE")
                .add(builder.newLayout("PatternLayout")
                        .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n")));

        // Create File Appender mit explizitem immediateFlush=true
        builder.add(builder.newAppender("LogToFile", "File")
                .addAttribute("fileName", "logs/application.txt")
                .addAttribute("immediateFlush", "true")
                .addAttribute("append", "true")
                .add(builder.newLayout("PatternLayout")
                        .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n")));

        // Configure Root Logger
        builder.add(builder.newRootLogger(org.apache.logging.log4j.Level.INFO)
                .add(builder.newAppenderRef("Console"))
                .add(builder.newAppenderRef("LogToFile")));

        Configurator.initialize(builder.build());
    }
}