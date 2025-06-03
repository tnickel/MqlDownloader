package browser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class WebDriverManager {
    private final String downloadPath;
    private static final Logger logger = LogManager.getLogger(WebDriverManager.class);
    private String currentUserDataDir;
    private static final int MAX_INITIALIZATION_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 2000;

    public WebDriverManager(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    /**
     * Initialisiert den WebDriver mit robuster Fehlerbehandlung und Retry-Mechanismus
     * 
     * @return WebDriver-Instanz
     * @throws RuntimeException wenn der WebDriver nach allen Versuchen nicht initialisiert werden konnte
     */
    public WebDriver initializeDriver() {
        WebDriver driver = null;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_INITIALIZATION_ATTEMPTS; attempt++) {
            try {
                logger.info("WebDriver-Initialisierung Versuch {} von {}", attempt, MAX_INITIALIZATION_ATTEMPTS);
                
                // Cleanup von vorherigen Versuchen
                if (attempt > 1) {
                    cleanupPreviousSession();
                    Thread.sleep(RETRY_DELAY_MS);
                }
                
                // WebDriverManager automatisch setup
                io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
                
                // Erstelle Chrome-Optionen mit eindeutiger user-data-dir
                ChromeOptions options = createRobustChromeOptions();
                
                // Versuche WebDriver zu erstellen
                driver = new ChromeDriver(options);
                
                if (driver != null) {
                    logger.info("WebDriver erfolgreich initialisiert nach {} Versuch(en)", attempt);
                    return driver;
                }
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("WebDriver-Initialisierung Versuch {} fehlgeschlagen: {}", attempt, e.getMessage());
                
                // Cleanup bei Fehler
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception cleanupEx) {
                        logger.warn("Fehler beim Cleanup des fehlgeschlagenen WebDrivers: {}", cleanupEx.getMessage());
                    }
                    driver = null;
                }
                
                // Bei kritischen Fehlern sofort abbrechen
                if (isCriticalError(e)) {
                    logger.error("Kritischer Fehler erkannt, breche WebDriver-Initialisierung ab: {}", e.getMessage());
                    break;
                }
            }
        }
        
        // Wenn alle Versuche fehlgeschlagen sind
        String errorMessage = String.format("WebDriver konnte nach %d Versuchen nicht initialisiert werden", 
                                           MAX_INITIALIZATION_ATTEMPTS);
        if (lastException != null) {
            errorMessage += ": " + lastException.getMessage();
        }
        
        logger.error(errorMessage);
        throw new RuntimeException(errorMessage, lastException);
    }

    /**
     * Erstellt robuste Chrome-Optionen mit eindeutiger user-data-dir und optimierten Einstellungen
     * 
     * @return ChromeOptions mit robusten Einstellungen
     */
    private ChromeOptions createRobustChromeOptions() {
        // Erstelle eindeutige user-data-dir
        currentUserDataDir = createUniqueUserDataDir();
        
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadPath);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("safebrowsing.enabled", true);
        prefs.put("profile.default_content_settings.popups", 0);

        ChromeOptions options = new ChromeOptions();
        
        // Basis-Optionen
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        
        // Eindeutige user-data-dir (löst das Hauptproblem)
        options.addArguments("--user-data-dir=" + currentUserDataDir);
        
        // Robustheit-Optionen
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images");
        options.addArguments("--disable-javascript");
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-features=TranslateUI");
        options.addArguments("--disable-ipc-flooding-protection");
        
        // Stabilität und Performance
        options.addArguments("--max_old_space_size=4096");
        options.addArguments("--remote-debugging-port=0"); // Zufälliger Port
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        
        logger.info("Chrome-Optionen erstellt mit user-data-dir: {}", currentUserDataDir);
        
        return options;
    }

    /**
     * Erstellt eine eindeutige user-data-dir basierend auf Timestamp und UUID
     * 
     * @return Pfad zur eindeutigen user-data-dir
     */
    private String createUniqueUserDataDir() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            String tempDir = System.getProperty("java.io.tmpdir");
            String userDataDir = Paths.get(tempDir, "chrome_user_data_" + timestamp + "_" + uuid).toString();
            
            // Stelle sicher, dass das Verzeichnis existiert
            Files.createDirectories(Paths.get(userDataDir));
            
            logger.info("Eindeutige user-data-dir erstellt: {}", userDataDir);
            return userDataDir;
            
        } catch (IOException e) {
            logger.warn("Fehler beim Erstellen der user-data-dir, verwende Fallback: {}", e.getMessage());
            // Fallback: Verwende temp-dir mit UUID
            String tempDir = System.getProperty("java.io.tmpdir");
            return Paths.get(tempDir, "chrome_fallback_" + UUID.randomUUID().toString().substring(0, 8)).toString();
        }
    }

    /**
     * Bereinigt die aktuelle Session und entfernt temporäre Dateien
     */
    public void cleanupSession() {
        cleanupPreviousSession();
    }

    /**
     * Bereinigt vorherige Session-Daten
     */
    private void cleanupPreviousSession() {
        if (currentUserDataDir != null) {
            try {
                Path userDataPath = Paths.get(currentUserDataDir);
                if (Files.exists(userDataPath)) {
                    deleteDirectoryRecursively(userDataPath);
                    logger.info("User-data-dir bereinigt: {}", currentUserDataDir);
                }
            } catch (Exception e) {
                logger.warn("Fehler beim Bereinigen der user-data-dir {}: {}", currentUserDataDir, e.getMessage());
            }
        }
        
        // Töte eventuell hängende Chrome-Prozesse (nur unter Windows)
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            try {
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "chrome.exe");
                pb.start().waitFor();
                Thread.sleep(1000); // Warte kurz nach dem Töten der Prozesse
            } catch (Exception e) {
                logger.debug("Konnte Chrome-Prozesse nicht töten (normal wenn keine laufen): {}", e.getMessage());
            }
        }
    }

    /**
     * Löscht ein Verzeichnis rekursiv
     * 
     * @param path Pfad zum zu löschenden Verzeichnis
     * @throws IOException bei Fehlern beim Löschen
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // Sortiere absteigend für korrekte Löschung
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        logger.debug("Konnte Datei/Verzeichnis nicht löschen: {} - {}", p, e.getMessage());
                    }
                });
        }
    }

    /**
     * Prüft, ob es sich um einen kritischen Fehler handelt, bei dem nicht retry werden sollte
     * 
     * @param exception Die aufgetretene Exception
     * @return true wenn es ein kritischer Fehler ist
     */
    private boolean isCriticalError(Exception exception) {
        String message = exception.getMessage().toLowerCase();
        
        // Kritische Fehler, bei denen kein Retry helfen würde
        return message.contains("no such file or directory") ||
               message.contains("permission denied") ||
               message.contains("access denied") ||
               message.contains("out of memory") ||
               message.contains("cannot find chrome binary") ||
               message.contains("chromedriver") && message.contains("not found");
    }

    /**
     * Erstellt einen neuen WebDriver mit Recovery-Mechanismus
     * Verwendbar wenn der bestehende WebDriver fehlerhaft geworden ist
     * 
     * @param oldDriver Der fehlerhafte WebDriver (wird sauber geschlossen)
     * @return Neuer WebDriver oder null bei Fehlschlag
     */
    public WebDriver recoverWebDriver(WebDriver oldDriver) {
        logger.info("Starte WebDriver-Recovery...");
        
        // Schließe den alten WebDriver sauber
        if (oldDriver != null) {
            try {
                oldDriver.quit();
            } catch (Exception e) {
                logger.warn("Fehler beim Schließen des alten WebDrivers: {}", e.getMessage());
            }
        }
        
        // Cleanup und kurze Pause
        cleanupPreviousSession();
        
        try {
            Thread.sleep(3000); // Längere Pause für Recovery
            return initializeDriver();
        } catch (Exception e) {
            logger.error("WebDriver-Recovery fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Prüft, ob ein WebDriver noch funktionsfähig ist
     * 
     * @param driver Der zu prüfende WebDriver
     * @return true wenn der WebDriver funktionsfähig ist
     */
    public boolean isDriverHealthy(WebDriver driver) {
        if (driver == null) {
            return false;
        }
        
        try {
            // Einfacher Gesundheitscheck
            driver.getCurrentUrl();
            return true;
        } catch (Exception e) {
            logger.warn("WebDriver-Gesundheitscheck fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Getter für die aktuelle user-data-dir (für Debugging)
     * 
     * @return Aktueller Pfad zur user-data-dir
     */
    public String getCurrentUserDataDir() {
        return currentUserDataDir;
    }
}