package logging;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class LoggerManager {
    private static final Logger logger = LogManager.getLogger(LoggerManager.class);
    private static boolean isInitialized = false;
    private static final ReentrantLock flushLock = new ReentrantLock();
    private static volatile boolean shutdownInProgress = false;
    
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

        // Create Console Appender mit immediateFlush
        builder.add(builder.newAppender("Console", "CONSOLE")
                .addAttribute("immediateFlush", "true")
                .add(builder.newLayout("PatternLayout")
                        .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n")));

        // Create File Appender mit explizitem immediateFlush=true und bufferedIO=false
        builder.add(builder.newAppender("LogToFile", "File")
                .addAttribute("fileName", "logs/application.txt")
                .addAttribute("immediateFlush", "true")
                .addAttribute("bufferedIO", "false")
                .addAttribute("append", "true")
                .add(builder.newLayout("PatternLayout")
                        .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n")));

        // Configure Root Logger
        builder.add(builder.newRootLogger(org.apache.logging.log4j.Level.INFO)
                .add(builder.newAppenderRef("Console"))
                .add(builder.newAppenderRef("LogToFile")));

        Configurator.initialize(builder.build());
    }
    
    /**
     * SICHERE und EINFACHE Flush-Methode ohne gefährliche Appender-Manipulationen
     */
    public static void flushAllLogs() {
        if (shutdownInProgress) {
            return; // Verhindere Flush-Versuche während Shutdown
        }
        
        boolean lockAcquired = false;
        try {
            // Versuche Lock mit Timeout zu bekommen
            lockAcquired = flushLock.tryLock(500, TimeUnit.MILLISECONDS);
            if (!lockAcquired) {
                System.err.println("LoggerManager: Konnte Flush-Lock nicht bekommen, überspringe Flush");
                return;
            }
            
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            if (context != null && context.getConfiguration() != null) {
                // SICHERE Flush-Methode - NUR flush() ohne stop/start
                context.getConfiguration().getAppenders().values().forEach(appender -> {
                    if (appender != null && appender.isStarted()) {
                        try {
                            // Versuche nur sichere flush()-Methoden ohne Appender-Manipulation
                            Class<?> appenderClass = appender.getClass();
                            
                            // Methode 1: Versuche direkten flush() Aufruf
                            try {
                                java.lang.reflect.Method flushMethod = appenderClass.getMethod("flush");
                                flushMethod.invoke(appender);
                                return; // Erfolgreich - beende für diesen Appender
                            } catch (NoSuchMethodException e) {
                                // flush() nicht verfügbar, versuche Manager-Methode
                            }
                            
                            // Methode 2: Versuche getManager() und dann flush()
                            try {
                                java.lang.reflect.Method getManagerMethod = appenderClass.getMethod("getManager");
                                Object manager = getManagerMethod.invoke(appender);
                                if (manager != null) {
                                    java.lang.reflect.Method managerFlushMethod = manager.getClass().getMethod("flush");
                                    managerFlushMethod.invoke(manager);
                                }
                            } catch (Exception e) {
                                // Manager-Flush nicht verfügbar - ignorieren
                            }
                            
                            // ENTFERNT: stop/start Mechanismus der den Logger beschädigt hat
                            
                        } catch (Exception e) {
                            // Nur System.err verwenden um Log-Rekursion zu vermeiden
                            System.err.println("LoggerManager: Fehler beim Flushing von Appender " + 
                                             appender.getName() + " (Typ: " + appender.getClass().getSimpleName() + "): " + e.getMessage());
                        }
                    }
                });
                
                // System-Flush ist immer sicher
                System.out.flush();
                System.err.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("LoggerManager: Flush unterbrochen");
        } catch (Exception e) {
            System.err.println("LoggerManager: Unerwarteter Fehler beim Flushing: " + e.getMessage());
        } finally {
            if (lockAcquired) {
                flushLock.unlock();
            }
        }
    }
    
    /**
     * NEUE sanfte Flush-Methode die nur System-Streams leert
     */
    public static void gentleFlush() {
        try {
            // Nur sichere System-Flush-Operationen
            System.out.flush();
            System.err.flush();
        } catch (Exception e) {
            // System-Flush sollte nie fehlschlagen, aber sicherheitshalber
            System.err.println("LoggerManager: Fehler beim System-Flush: " + e.getMessage());
        }
    }
    
    /**
     * Thread-sichere Logging-Methode für kritische Situationen
     */
    public static void safeLog(String message) {
        try {
            if (!shutdownInProgress && isInitialized) {
                logger.info(message);
            } else {
                // Fallback auf System.out wenn Logger nicht verfügbar
                System.out.println("[SAFE-LOG] " + message);
            }
        } catch (Exception e) {
            // Fallback bei jedem Logging-Fehler
            System.out.println("[SAFE-LOG-FALLBACK] " + message);
            System.err.println("Logging-Fehler: " + e.getMessage());
        }
    }
    
    /**
     * Thread-sichere Error-Logging-Methode
     */
    public static void safeLogError(String message, Throwable throwable) {
        try {
            if (!shutdownInProgress && isInitialized) {
                logger.error(message, throwable);
            } else {
                // Fallback auf System.err wenn Logger nicht verfügbar
                System.err.println("[SAFE-LOG-ERROR] " + message);
                if (throwable != null) {
                    throwable.printStackTrace();
                }
            }
        } catch (Exception e) {
            // Fallback bei jedem Logging-Fehler
            System.err.println("[SAFE-LOG-ERROR-FALLBACK] " + message);
            if (throwable != null) {
                throwable.printStackTrace();
            }
            System.err.println("Logging-Fehler: " + e.getMessage());
        }
    }
    
    /**
     * SICHERE Shutdown-Methode ohne gefährliche Appender-Manipulationen
     */
    public static void safeShutdown() {
        if (shutdownInProgress) {
            return; // Verhindere mehrfache Shutdown-Versuche
        }
        
        shutdownInProgress = true;
        
        try {
            boolean lockAcquired = false;
            try {
                // Versuche Lock mit kurzem Timeout zu bekommen
                lockAcquired = flushLock.tryLock(500, TimeUnit.MILLISECONDS);
                
                if (lockAcquired) {
                    // Finaler sanfter Flush vor Shutdown
                    LoggerContext context = (LoggerContext) LogManager.getContext(false);
                    if (context != null && context.getConfiguration() != null) {
                        context.getConfiguration().getAppenders().values().forEach(appender -> {
                            if (appender != null && appender.isStarted()) {
                                try {
                                    // Nur sichere flush()-Operationen
                                    Class<?> appenderClass = appender.getClass();
                                    
                                    try {
                                        java.lang.reflect.Method flushMethod = appenderClass.getMethod("flush");
                                        flushMethod.invoke(appender);
                                    } catch (NoSuchMethodException e) {
                                        // Versuche Manager-Flush
                                        try {
                                            java.lang.reflect.Method getManagerMethod = appenderClass.getMethod("getManager");
                                            Object manager = getManagerMethod.invoke(appender);
                                            if (manager != null) {
                                                java.lang.reflect.Method managerFlushMethod = manager.getClass().getMethod("flush");
                                                managerFlushMethod.invoke(manager);
                                            }
                                        } catch (Exception ex) {
                                            // Ignore - keine Flush-Methode verfügbar
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Fehler beim finalen Flush von " + appender.getName() + ": " + e.getMessage());
                                }
                            }
                        });
                    }
                }
                
                // System-Flush ist immer sicher
                System.out.flush();
                System.err.flush();
                
                // Kurze Wartezeit für finale Writes
                Thread.sleep(200);
                
                System.out.println("LoggerManager: Sicherer Shutdown abgeschlossen - Logger bleiben aktiv");
                
            } finally {
                if (lockAcquired) {
                    flushLock.unlock();
                }
            }
            
        } catch (Exception e) {
            System.err.println("LoggerManager: Fehler beim sicheren Shutdown: " + e.getMessage());
        } finally {
            // Reset Shutdown-Flag nach Wartezeit für zukünftige Verwendung
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    shutdownInProgress = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    
    /**
     * Status-Prüfung
     */
    public static boolean isShutdownInProgress() {
        return shutdownInProgress;
    }
    
    /**
     * NEUE konditionelle Flush-Methode die nur bei Bedarf flusht
     */
    public static void conditionalFlush(int operationCount) {
        // Verwende sanften Flush statt aggressiven flushAllLogs()
        if (operationCount % 10 == 0) {
            gentleFlush();
        }
    }
}