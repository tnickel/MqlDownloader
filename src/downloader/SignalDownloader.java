package downloader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import browser.WebDriverManager;
import config.ConfigurationManager;
import config.Credentials;
import utils.MqlDownloadProtokoll;

public class SignalDownloader {
    private WebDriver driver;
    private final WebDriverManager webDriverManager;
    private final ConfigurationManager configManager;
    private final Credentials credentials;
    private WebDriverWait wait;
    private String baseUrl;
    private static final Logger logger = LogManager.getLogger(SignalDownloader.class);
    private volatile boolean stopRequested;
    private int providerCount = 0; // Für Rückwärtskompatibilität mit getMqlLimit() Prüfungen
    private ProgressCallback progressCallback;
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5; // Erhöht von 2 auf 5
    private MqlDownloadProtokoll downloadProtokoll;
    
    // NEUE Klassenvariablen für korrekte Numerierung
    private int totalProvidersProcessed = 0;  // Gesamtzahl aller verarbeiteten Provider
    private int successfulDownloads = 0;      // Nur erfolgreich heruntergeladene
    private int skippedProviders = 0;         // Übersprungene Provider
    
    // Fehlertypen für bessere Klassifizierung
    private enum ErrorType {
        CRITICAL,           // Sofortiger Stopp (Internetverbindung, schwerwiegende WebDriver-Fehler)
        RECOVERABLE,        // Recovery möglich (Element nicht gefunden, Timeout)
        NON_CRITICAL        // Weiter mit nächstem Provider (Einzelner Download-Fehler)
    }

    public SignalDownloader(WebDriver driver, ConfigurationManager configManager, Credentials credentials) throws IOException {
        this.driver = driver;
        this.webDriverManager = new WebDriverManager(configManager.getDownloadPath());
        this.configManager = configManager;
        this.credentials = credentials;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        this.baseUrl = configManager.getMqlBaseUrl();
        this.stopRequested = false;
        this.providerCount = 0;
        this.totalProvidersProcessed = 0;
        this.successfulDownloads = 0;
        this.skippedProviders = 0;
    }

    public void setStopFlag(boolean stopRequested) {
        this.stopRequested = stopRequested;
        if (stopRequested) {
            providerCount = 0;
            totalProvidersProcessed = 0;
            successfulDownloads = 0;
            skippedProviders = 0;
        }
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    
    public void setDownloadProtokoll(MqlDownloadProtokoll protokoll) {
        this.downloadProtokoll = protokoll;
    }

    /**
     * KORRIGIERTE Fortschritts-Update-Methode mit korrekter Numerierung
     */
    private void updateProgress(String providerName, String action, boolean isSuccessful) {
        totalProvidersProcessed++;
        
        if (isSuccessful) {
            successfulDownloads++;
        } else if (action.contains("ÜBERSPRUNGEN")) {
            skippedProviders++;
        }
        
        // Einheitliche Log-Nachricht mit korrekter Numerierung
        logger.info("Fortschritt MQL{}: {}/{} Provider verarbeitet - Provider #{}: '{}' - {} (Erfolgreich: {}, Übersprungen: {})", 
                   configManager.getMqlVersion().contains("4") ? "4" : "5",
                   totalProvidersProcessed, getMqlLimit(), totalProvidersProcessed, providerName, action,
                   successfulDownloads, skippedProviders);
        
        // KORRIGIERT: Verwende sanften Flush statt problematischen flushAllLogs()
        // Nur alle 10 Provider einen sanften Flush durchführen
        if (totalProvidersProcessed % 10 == 0) {
            logging.LoggerManager.gentleFlush();
        }
        
        if (progressCallback != null) {
            progressCallback.onProgress(totalProvidersProcessed);
        }
    }

    /**
     * Überladene Methode für Rückwärtskompatibilität
     */
  

    public void setMqlVersion(String version) throws IOException {
        if (!version.equals("mt4") && !version.equals("mt5")) {
            throw new IllegalArgumentException("MQL-Version muss entweder 'mt4' oder 'mt5' sein");
        }
        configManager.setMqlVersion(version);
        updateBaseUrl(version);
    }

    private void updateBaseUrl(String version) {
        this.baseUrl = String.format("https://www.mql5.com/en/signals/%s/list", version);
    }

    /**
     * NEUE METHODE: Ermittelt die maximale Seitenzahl aus der Pagination
     */
    private int getMaxPageNumber() {
        try {
            logger.info("Ermittle maximale Seitenzahl aus der Pagination...");
            
            // Lade die erste Seite, falls nicht bereits geladen
            if (!driver.getCurrentUrl().contains("/signals/")) {
                driver.get(baseUrl);
                Thread.sleep(getRandomWaitTime());
            }
            
            // Verschiedene Selektoren für Pagination-Elemente
            List<String> paginationSelectors = Arrays.asList(
                ".paging a",                      // Standard Pagination Links
                "a.paging__link",                 // Alternative Klasse
                ".pagination a",                  // Alternative Pagination
                "[class*='paging'] a",            // Beliebige Klasse die 'paging' enthält
                "a[href*='/page']"                // Links die '/page' enthalten
            );
            
            int maxPage = 1;
            boolean paginationFound = false;
            
            for (String selector : paginationSelectors) {
                try {
                    List<WebElement> pageLinks = driver.findElements(By.cssSelector(selector));
                    
                    if (!pageLinks.isEmpty()) {
                        logger.debug("Pagination-Links gefunden mit Selektor '{}': {} Links", selector, pageLinks.size());
                        paginationFound = true;
                        
                        // Durchsuche alle Pagination-Links
                        for (WebElement link : pageLinks) {
                            String linkText = link.getText().trim();
                            String href = link.getAttribute("href");
                            
                            // Versuche Seitenzahl aus Text zu extrahieren
                            try {
                                int pageNum = Integer.parseInt(linkText);
                                if (pageNum > maxPage) {
                                    maxPage = pageNum;
                                }
                            } catch (NumberFormatException e) {
                                // Text ist keine Zahl, versuche aus href zu extrahieren
                                if (href != null && href.contains("/page")) {
                                    String pageNumStr = href.replaceAll(".*page(\\d+).*", "$1");
                                    try {
                                        int pageNum = Integer.parseInt(pageNumStr);
                                        if (pageNum > maxPage) {
                                            maxPage = pageNum;
                                        }
                                    } catch (NumberFormatException ex) {
                                        // Ignoriere, wenn keine Nummer extrahiert werden kann
                                    }
                                }
                            }
                        }
                        
                        // Wenn Pagination gefunden wurde, breche die Suche ab
                        break;
                    }
                } catch (Exception e) {
                    logger.debug("Fehler mit Selektor '{}': {}", selector, e.getMessage());
                }
            }
            
            // Falls keine Pagination gefunden wurde, versuche alternative Methode
            if (!paginationFound) {
                logger.warn("Keine Pagination-Elemente gefunden, versuche alternative Methode...");
                
                // Suche nach dem "Last Page" Link oder ähnlichem
                try {
                    // Suche nach dem letzten numerischen Link
                    List<WebElement> allLinks = driver.findElements(By.tagName("a"));
                    for (WebElement link : allLinks) {
                        String href = link.getAttribute("href");
                        if (href != null && href.matches(".*page\\d+$")) {
                            String pageNumStr = href.replaceAll(".*page(\\d+)$", "$1");
                            try {
                                int pageNum = Integer.parseInt(pageNumStr);
                                if (pageNum > maxPage) {
                                    maxPage = pageNum;
                                }
                            } catch (NumberFormatException e) {
                                // Ignoriere
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Alternative Methode zur Pagination-Erkennung fehlgeschlagen: {}", e.getMessage());
                }
            }
            
            logger.info("Maximale Seitenzahl ermittelt: {} Seiten", maxPage);
            
            // Protokolliere die ermittelte Seitenzahl
            if (downloadProtokoll != null) {
                String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                downloadProtokoll.logSystemEvent(mqlVersionForLog, "PAGINATION ERKANNT", 
                    "Maximale Seitenzahl: " + maxPage);
            }
            
            return maxPage;
            
        } catch (Exception e) {
            logger.error("Fehler beim Ermitteln der maximalen Seitenzahl: {}", e.getMessage(), e);
            // Im Fehlerfall geben wir 0 zurück, was bedeutet, dass die alte Logik verwendet wird
            return 0;
        }
    }

    /**
     * KORRIGIERTE startDownloadProcess Methode ohne problematisches LogManager.shutdown()
     */
    public void startDownloadProcess() {
        try {
            // Reset der Zähler bei Start
            totalProvidersProcessed = 0;
            successfulDownloads = 0;
            skippedProviders = 0;
            providerCount = 0; // Für Rückwärtskompatibilität mit getMqlLimit() Prüfungen
            
            logger.info("=== DOWNLOAD-PROZESS GESTARTET für {} ===", configManager.getMqlVersion().toUpperCase());
            logging.LoggerManager.flushAllLogs();
            
            if (!stopRequested) performLogin();
            if (!stopRequested) processSignalProviders();
            
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("Fehler im Download-Prozess", e);
                logging.LoggerManager.flushAllLogs();
                throw e;
            }
        } finally {
            // KORRIGIERTES Cleanup ohne LogManager.shutdown()
            cleanup();
        }
    }

    /**
     * NEUE sichere Cleanup-Methode
     */
    private void cleanup() {
        try {
            // Finale Statistiken loggen
            logger.info("=== DOWNLOAD-PROZESS BEENDET ===");
            logger.info("Gesamte Provider verarbeitet: {}", totalProvidersProcessed);
            logger.info("Erfolgreich heruntergeladen: {}", successfulDownloads);
            logger.info("Übersprungen: {}", skippedProviders);
            logger.info("Fehlgeschlagen: {}", totalProvidersProcessed - successfulDownloads - skippedProviders);
            
            // WebDriver cleanup
            if (webDriverManager != null) {
                logger.info("Bereinige WebDriver-Session...");
                webDriverManager.cleanupSession();
            }
            
            // KORRIGIERT: Verwende sanften Flush statt aggressiven flushAllLogs()
            logger.info("Führe sanften Log-Flush durch...");
            logging.LoggerManager.gentleFlush();
            
            // Kurz warten damit alle Writes abgeschlossen sind
            Thread.sleep(100);
            
            // Nochmaliger sanfter Flush
            logging.LoggerManager.gentleFlush();
            
            logger.info("Cleanup abgeschlossen - Logger bleiben stabil aktiv");
            
        } catch (Exception e) {
            System.err.println("Fehler beim Cleanup: " + e.getMessage());
            e.printStackTrace();
            
            // Notfall-Flush auch bei Fehlern - aber sanft
            try {
                logging.LoggerManager.gentleFlush();
            } catch (Exception flushError) {
                System.err.println("Notfall-Flush fehlgeschlagen: " + flushError.getMessage());
            }
        }
    }

    private void performLogin() {
        logger.info("Starte Anmeldeprozess...");
        
        try {
            driver.get("https://www.mql5.com/en/auth_login");

            WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("Login")));
            WebElement passwordField = driver.findElement(By.id("Password"));

            usernameField.sendKeys(credentials.getUsername());
            passwordField.sendKeys(credentials.getPassword());

            clickLoginButton();
            verifyLogin();
            
        } catch (Exception e) {
            ErrorType errorType = classifyError(e);
            if (errorType == ErrorType.CRITICAL) {
                logger.error("Kritischer Fehler beim Login - stoppe sofort: {}", e.getMessage());
                throw new RuntimeException("Kritischer Login-Fehler", e);
            } else {
                logger.error("Fehler beim Login - versuche Recovery: {}", e.getMessage());
                throw new RuntimeException("Login fehlgeschlagen", e);
            }
        }
    }

    private void clickLoginButton() {
        WebElement loginButton = findLoginButton();
        if (loginButton != null) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);
        } else {
            throw new RuntimeException("Login Button konnte nicht gefunden werden");
        }
    }

    private WebElement findLoginButton() {
        try {
            return driver.findElement(By.id("loginSubmit"));
        } catch (Exception e) {
            try {
                return driver.findElement(By.cssSelector("input.button.button_yellow.qa-submit"));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private void verifyLogin() {
        try {
            wait.until(ExpectedConditions.urlContains("/en"));
            Thread.sleep(getRandomWaitTime());
        } catch (Exception e) {
            throw new RuntimeException("Login-Verifizierung fehlgeschlagen", e);
        }
    }

    /**
     * ERWEITERTE processSignalProviders Methode mit Pagination-Erkennung
     */
    private void processSignalProviders() {
        int currentPage = 1;
        boolean hasNextPage = true;
        int mqlLimit = getMqlLimit(); // Hole das konfigurierte Limit
        
        logger.info("Starte Download-Prozess für {} - Limit: {} Provider", 
                   configManager.getMqlVersion().toUpperCase(), mqlLimit);

        // Log start in protocol
        if (downloadProtokoll != null) {
            String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
            downloadProtokoll.logSystemEvent(mqlVersionForLog, "DOWNLOAD GESTARTET", 
                "Limit: " + mqlLimit + " Provider | URL: " + baseUrl);
        }

        // NEUE LOGIK: Ermittle maximale Seitenzahl
        int maxPageNumber = 0;
        try {
            // Lade erste Seite für Pagination-Analyse
            driver.get(baseUrl);
            Thread.sleep(getRandomWaitTime());
            maxPageNumber = getMaxPageNumber();
        } catch (Exception e) {
            logger.warn("Fehler beim Ermitteln der maximalen Seitenzahl: {}", e.getMessage());
            // Fahre mit alter Logik fort, wenn Pagination-Erkennung fehlschlägt
        }

        while (hasNextPage && !stopRequested && totalProvidersProcessed < mqlLimit) {
            // NEUE LOGIK: Prüfe ob maximale Seitenzahl erreicht wurde
            if (maxPageNumber > 0 && currentPage > maxPageNumber) {
                logger.info("Maximale Seitenzahl ({}) erreicht - beende Download-Prozess", maxPageNumber);
                hasNextPage = false;
                break;
            }
            
            String pageUrl = baseUrl + "/page" + currentPage;
            try {
                logger.info("Verarbeite Seite {} von {} - Provider {}/{}", 
                           currentPage, 
                           maxPageNumber > 0 ? maxPageNumber : "unbekannt", 
                           totalProvidersProcessed, 
                           mqlLimit);
                
                // NEUE LOGIK: Prüfe ob Seite Provider enthält
                boolean pageHasProviders = processSignalProvidersPage(pageUrl);
                
                if (!pageHasProviders) {
                    logger.info("Keine Provider auf Seite {} gefunden - Ende der Liste erreicht", currentPage);
                    hasNextPage = false;
                    break;
                }
                
                currentPage++;
                consecutiveErrors = 0; // Reset bei erfolgreichem Processing
                
                // Log page progress
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logPageProgress(mqlVersionForLog, currentPage - 1, 0, totalProvidersProcessed);
                }
                
                // Prüfe Limit nach jeder Seite
                if (totalProvidersProcessed >= mqlLimit) {
                    logger.info("LIMIT ERREICHT: {} von {} Providern verarbeitet für {}", 
                               totalProvidersProcessed, mqlLimit, configManager.getMqlVersion().toUpperCase());
                    break;
                }
                
            } catch (RuntimeException e) {
                ErrorType errorType = classifyError(e);
                
                if (errorType == ErrorType.CRITICAL) {
                    logger.error("KRITISCHER FEHLER bei Seite {} - stoppe sofort: {}", currentPage, e.getMessage());
                    
                    if (downloadProtokoll != null) {
                        String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                        downloadProtokoll.logSystemEvent(mqlVersionForLog, "KRITISCHER FEHLER", 
                            "Download-Prozess gestoppt: " + e.getMessage());
                    }
                    throw e;
                }
                
                consecutiveErrors++;
                logger.error("Fehler beim Verarbeiten der Seite {} (Fehler {} von {}): {}", 
                           currentPage, consecutiveErrors, MAX_CONSECUTIVE_ERRORS, e.getMessage());
                
                // Protokolliere den Fehler bei der Seitenverarbeitung
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.log(mqlVersionForLog, String.format("WARNING SEITENFEHLER %d (Fehler %d/%d): %s", 
                        currentPage, consecutiveErrors, MAX_CONSECUTIVE_ERRORS, e.getMessage()));
                }
                
                // Prüfe ob Ende der Seiten erreicht
                if (e.getMessage().equals("Keine Signal-Provider gefunden")) {
                    logger.info("Keine weiteren Signalprovider auf Seite {} gefunden - reguläres Ende", currentPage);
                    hasNextPage = false;
                    consecutiveErrors = 0; // Reset, da es sich um ein reguläres Ende handelt
                    continue;
                }
                
                // Prüfe Limit für aufeinanderfolgende Fehler
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    logger.error("Zu viele aufeinanderfolgende Fehler ({} von {}). Beende Download-Prozess.", 
                               consecutiveErrors, MAX_CONSECUTIVE_ERRORS);
                    
                    if (downloadProtokoll != null) {
                        String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                        downloadProtokoll.logSystemEvent(mqlVersionForLog, "ZU VIELE FEHLER", 
                            String.format("Download beendet nach %d aufeinanderfolgenden Fehlern", MAX_CONSECUTIVE_ERRORS));
                    }
                    throw e;
                }
                
                // Versuche Recovery bei recovery-fähigen Fehlern
                if (errorType == ErrorType.RECOVERABLE) {
                    logger.info("Versuche Recovery nach Fehler bei Seite {}", currentPage);
                    boolean recovered = attemptRecovery(e);
                    
                    // Log recovery attempt
                    if (downloadProtokoll != null) {
                        String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                        downloadProtokoll.logRecoveryAttempt(mqlVersionForLog, "Seitenfehler", "WebDriver Recovery", recovered);
                    }
                    
                    if (recovered) {
                        logger.info("Recovery erfolgreich, setze mit Seite {} fort", currentPage);
                        // Seite wiederholen ohne Increment
                        continue;
                    } else {
                        logger.warn("Recovery fehlgeschlagen, überspringe Seite {}", currentPage);
                    }
                }
                
                // Warte vor dem nächsten Versuch
                try {
                    Thread.sleep(getRandomWaitTime());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Gehe zur nächsten Seite über
                currentPage++;
            }
        }
        
        // Detaillierte Abschluss-Logs
        String reason = determineEndReason(hasNextPage, totalProvidersProcessed, mqlLimit, consecutiveErrors, maxPageNumber, currentPage);
        logger.info("Download-Prozess beendet - {}: {} Provider verarbeitet, {} Seiten durchsucht", 
                   reason, totalProvidersProcessed, currentPage - 1);
        
        // Protokolliere das Ende des Prozesses mit Details
        if (downloadProtokoll != null) {
            String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
            downloadProtokoll.logSystemEvent(mqlVersionForLog, "DOWNLOAD BEENDET", reason);
            
            // Verwende die tatsächlichen Statistiken
            downloadProtokoll.logFinalStatistics(mqlVersionForLog, totalProvidersProcessed, 
                                                successfulDownloads, skippedProviders, 
                                                totalProvidersProcessed - successfulDownloads - skippedProviders, 
                                                currentPage - 1);
        }
    }
    
    /**
     * ERWEITERTE processSignalProvidersPage Methode die boolean zurückgibt
     */
    private boolean processSignalProvidersPage(String pageUrl) {
        if (stopRequested) return false;

        try {
            driver.get(pageUrl);
            
            // Warte auf Seitenladung mit robusten Selektoren
            boolean pageLoaded = waitForPageElements();
            
            if (!pageLoaded) {
                logger.warn("Seite konnte nicht geladen werden oder keine Signal-Provider gefunden für: {}", pageUrl);
                return false;
            }

            List<WebElement> providerLinks = findProviderLinks();

            if (providerLinks.isEmpty()) {
                logger.info("Keine Signal-Provider auf Seite {} gefunden", pageUrl);
                return false;
            }

            logger.info("Seite {}: {} Provider gefunden (Gesamt bisher: {})", 
                       pageUrl, providerLinks.size(), totalProvidersProcessed);

            int mqlLimit = getMqlLimit();
            
            for (int i = 0; i < providerLinks.size() && !stopRequested; i++) {
                // Prüfe Limit vor jedem Provider
                if (totalProvidersProcessed >= mqlLimit) {
                    logger.info("LIMIT ERREICHT: {} Provider verarbeitet von maximal {}", totalProvidersProcessed, mqlLimit);
                    break;
                }
                
                try {
                    processSignalProvider(pageUrl, i);
                } catch (Exception e) {
                    if (!stopRequested) {
                        ErrorType errorType = classifyError(e);
                        handleProviderError(pageUrl, i, e, errorType);
                    }
                }
            }
            
            return true; // Seite hatte Provider
            
        } catch (Exception e) {
            ErrorType errorType = classifyError(e);
            if (errorType == ErrorType.CRITICAL) {
                logger.error("Kritischer Fehler beim Verarbeiten der Seite {} - stoppe sofort: {}", pageUrl, e.getMessage());
                throw e;
            } else {
                logger.error("Fehler beim Verarbeiten der Seite {}: {}", pageUrl, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Wartet auf das Laden der Seitenelemente mit mehreren Fallback-Strategien
     */
    private boolean waitForPageElements() {
        List<By> selectors = Arrays.asList(
            By.className("signal"),
            By.cssSelector(".signal"),
            By.cssSelector("[class*='signal']"),
            By.cssSelector("a[href*='/signals/']"),
            By.cssSelector("a[href*='signals']")
        );

        for (By selector : selectors) {
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                logger.debug("Seite geladen, Elemente gefunden mit Selektor: {}", selector);
                return true;
            } catch (TimeoutException e) {
                logger.debug("Timeout mit Selektor {}, versuche nächsten", selector);
            }
        }

        logger.warn("Keine Signal-Provider-Elemente gefunden mit allen Selektoren");
        return false;
    }

    /**
     * Findet Provider-Links mit robusten Selektoren
     */
    private List<WebElement> findProviderLinks() {
        List<String> selectors = Arrays.asList(
            ".signal a[href*='/signals/']",
            "a[href*='/signals/']",
            "a[href*='signals']",
            "[class*='signal'] a",
            ".signal-item a",
            ".provider-link"
        );

        for (String selector : selectors) {
            try {
                List<WebElement> links = driver.findElements(By.cssSelector(selector));
                if (!links.isEmpty()) {
                    logger.debug("Provider-Links gefunden mit Selektor '{}': {} Links", selector, links.size());
                    return links;
                }
            } catch (Exception e) {
                logger.debug("Selektor '{}' fehlgeschlagen: {}", selector, e.getMessage());
            }
        }

        logger.warn("Keine Provider-Links mit allen Selektoren gefunden");
        return new ArrayList<>();
    }

    /**
     * Klassifiziert Fehler nach Schweregrad und Recovery-Möglichkeit
     */
    private ErrorType classifyError(Exception e) {
        String message = e.getMessage().toLowerCase();
        String className = e.getClass().getSimpleName().toLowerCase();

        // Kritische Fehler - sofortiger Stopp
        if (message.contains("no internet") ||
            message.contains("network is unreachable") ||
            message.contains("connection refused") ||
            message.contains("session not created") ||
            message.contains("chrome not reachable") ||
            message.contains("out of memory") ||
            className.contains("outofmemory")) {
            
            return ErrorType.CRITICAL;
        }

        // Recovery-fähige Fehler
        if (message.contains("timeout") ||
            message.contains("no such element") ||
            message.contains("element not found") ||
            message.contains("stale element") ||
            className.contains("timeout") ||
            className.contains("nosuchelement")) {
            
            return ErrorType.RECOVERABLE;
        }

        // Alle anderen als nicht-kritisch behandeln
        return ErrorType.NON_CRITICAL;
    }

    /**
     * Behandelt Fehler bei der Provider-Verarbeitung
     */
    private void handleProviderError(String pageUrl, int providerIndex, Exception e, ErrorType errorType) {
        String errorMsg = String.format("Fehler bei Provider %d auf Seite %s: %s", 
                                       providerIndex, pageUrl, e.getMessage());
        
        switch (errorType) {
            case CRITICAL:
                logger.error("KRITISCHER FEHLER - {}", errorMsg);
                throw new RuntimeException("Kritischer Fehler", e);
                
            case RECOVERABLE:
                logger.warn("RECOVERY-FÄHIGER FEHLER - {}", errorMsg);
                // Versuche Recovery, aber zähle als Fehler
                boolean recovered = attemptRecovery(e);
                if (!recovered) {
                    logger.error("Recovery fehlgeschlagen für Provider {}", providerIndex);
                }
                break;
                
            case NON_CRITICAL:
            default:
                logger.warn("NICHT-KRITISCHER FEHLER - {}", errorMsg);
                // Logge nur und fahre fort
                break;
        }
    }

    /**
     * Versucht Recovery von recovery-fähigen Fehlern
     */
    private boolean attemptRecovery(Exception e) {
        try {
            logger.info("Versuche Recovery von Fehler: {}", e.getMessage());
            
            // Check WebDriver Health
            if (!webDriverManager.isDriverHealthy(driver)) {
                logger.info("WebDriver ist nicht gesund, starte Recovery...");
                
                WebDriver newDriver = webDriverManager.recoverWebDriver(driver);
                if (newDriver != null) {
                    this.driver = newDriver;
                    this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
                    logger.info("WebDriver erfolgreich wiederhergestellt");
                    
                    // Re-login nach WebDriver Recovery
                    performLogin();
                    return true;
                } else {
                    logger.error("WebDriver-Recovery fehlgeschlagen");
                    return false;
                }
            }
            
            // Einfache Recovery: Seite neu laden
            logger.info("Lade aktuelle Seite neu für Recovery...");
            driver.navigate().refresh();
            Thread.sleep(getRandomWaitTime());
            
            return true;
            
        } catch (Exception recoveryEx) {
            logger.error("Recovery-Versuch fehlgeschlagen: {}", recoveryEx.getMessage());
            return false;
        }
    }

    /**
     * Prüft, ob die Dateien für einen bestimmten Provider kürzlich heruntergeladen wurden
     * basierend auf dem konfigurierten Alter in Tagen.
     * 
     * @param providerId Die ID des Providers
     * @param providerName Der Name des Providers
     * @return true, wenn die Dateien innerhalb der konfigurierten Tage heruntergeladen wurden
     */
    private boolean isFileRecentlyDownloaded(String providerId, String providerName) {
        String targetPath = configManager.getCurrentDownloadPath();
        String safeProviderName = providerName.replaceAll("[\\/:*?\"<>|\\s]+", "_");
        
        // Überprüfe die HTML-Datei
        String htmlFileName = String.format("%s_%s_root.html", safeProviderName, providerId);
        File htmlFile = new File(targetPath, htmlFileName);
        
        // Überprüfe die CSV-Datei (könnte mehrere Dateien mit diesem Präfix geben)
        File[] csvFiles = new File(targetPath).listFiles((dir, name) -> 
            name.startsWith(safeProviderName) && name.endsWith(".csv"));
        
        // Wenn beide Dateien existieren, prüfe ihr Alter
        if (htmlFile.exists() && csvFiles != null && csvFiles.length > 0) {
            // Konfigurierte Tage aus den Einstellungen abrufen
            int configuredDays = configManager.getDownloadDays();
            
            // Wenn 0 Tage konfiguriert sind, immer neu herunterladen
            if (configuredDays == 0) {
                logger.debug("Konfigurierte Tage ist 0, lade Dateien für Provider {} neu herunter", providerName);
                return false;
            }
            
            long currentTime = System.currentTimeMillis();
            long configuredDaysInMillis = configuredDays * 24 * 60 * 60 * 1000L; // Konfigurierte Tage in Millisekunden
            
            long htmlFileAge = currentTime - htmlFile.lastModified();
            
            // Finde die jüngste CSV-Datei
            long youngestCsvFileAge = Long.MAX_VALUE;
            for (File csvFile : csvFiles) {
                long age = currentTime - csvFile.lastModified();
                if (age < youngestCsvFileAge) {
                    youngestCsvFileAge = age;
                }
            }
            
            // Wenn beide Dateien jünger als die konfigurierten Tage sind
            boolean result = (htmlFileAge < configuredDaysInMillis && youngestCsvFileAge < configuredDaysInMillis);
            if (result) {
                logger.debug("Provider {} Dateien sind jünger als {} Tage, überspringe", 
                    providerName, configuredDays);
                
                // Protokolliere das Überspringen
                if (downloadProtokoll != null) {
                    String mqlVersion = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logSkipped(mqlVersion, providerName, 
                        "Dateien sind jünger als " + configuredDays + " Tage");
                }
            } else {
                logger.debug("Provider {} Dateien sind älter als {} Tage, lade neu herunter", 
                    providerName, configuredDays);
            }
            return result;
        }
        
        // Wenn eine der Dateien nicht existiert, muss heruntergeladen werden
        logger.debug("Eine oder beide Dateien für Provider {} existieren nicht, lade herunter", providerName);
        return false;
    }

    /**
     * KORRIGIERTE processSignalProvider Methode mit fortlaufender Numerierung
     */
    private void processSignalProvider(String pageUrl, int index) {
        if (stopRequested) return;

        String providerName = "Unbekannt";
        String providerId = "0";
        
        try {
            List<WebElement> providerLinks = findProviderLinks();
            if (index >= providerLinks.size() || stopRequested) {
                logger.debug("Provider-Index {} außerhalb der Grenzen ({}) oder Stop angefordert", index, providerLinks.size());
                return;
            }

            WebElement link = providerLinks.get(index);
            String providerUrl = link.getAttribute("href");
            providerName = link.getText().trim();
            
            // Provider ID extrahieren und bereinigen
            providerId = providerUrl.substring(providerUrl.lastIndexOf("/") + 1);
            if (providerId.contains("?")) {
                providerId = providerId.substring(0, providerId.indexOf("?"));
            }

            // FORTLAUFENDE NUMERIERUNG: Verwende totalProvidersProcessed für die globale Nummer
            int globalProviderNumber = totalProvidersProcessed;

            logger.info("STARTE Provider: '{}' (ID: {}) - Fortlaufende Nr. {} (Index {} auf Seite)", 
                       providerName, providerId, globalProviderNumber + 1, index);

            // Bestimme die aktuelle MQL-Version für das Protokoll
            String mqlVersion = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
            
            // Protokolliere den Versuch mit FORTLAUFENDER NUMMER
            if (downloadProtokoll != null) {
                downloadProtokoll.logAttempt(mqlVersion, providerName, providerId, globalProviderNumber);
            }

            // Prüfe, ob Dateien kürzlich heruntergeladen wurden
            if (isFileRecentlyDownloaded(providerId, providerName)) {
                // KORRIGIERTE Fortschrittsanzeige für übersprungene Provider
                updateProgress(providerName, "ÜBERSPRUNGEN (Dateien jünger als " + configManager.getDownloadDays() + " Tage)", false);
                
                // Protokolliere das Überspringen mit FORTLAUFENDER NUMMER
                if (downloadProtokoll != null) {
                    downloadProtokoll.logSkipped(mqlVersion, providerName, 
                        "Dateien sind jünger als " + configManager.getDownloadDays() + " Tage", globalProviderNumber);
                }
                return;
            }

            // Versuche Root Page zu downloaden
            try {
                logger.debug("Lade Root-Seite für Provider: {}", providerName);
                downloadProviderRootPage(providerUrl, providerId, providerName);
                logger.debug("Root-Seite erfolgreich für Provider: {}", providerName);
            } catch (RuntimeException e) {
                // Wenn kritischer Fehler, weiterwerfen
                if (e.getMessage().contains("Kritischer Fehler")) {
                    throw e;
                }
                logger.warn("Root Page Download fehlgeschlagen für '{}', überspringe Trading History", providerName);
            }
            
            if (!stopRequested) {
                // Versuche Trading History zu downloaden
                try {
                    logger.debug("Lade Trading History für Provider: {}", providerName);
                    downloadTradeHistory(providerUrl, providerName);
                    logger.debug("Trading History erfolgreich für Provider: {}", providerName);
                } catch (RuntimeException e) {
                    // Wenn kritischer Fehler, weiterwerfen
                    if (e.getMessage().contains("Kritischer Fehler")) {
                        throw e;
                    }
                    logger.warn("Trading History Download fehlgeschlagen für '{}'", providerName);
                }
                
                // KORRIGIERTE Fortschrittsanzeige für erfolgreich verarbeitete Provider
                updateProgress(providerName, "ERFOLGREICH HERUNTERGELADEN", true);
                
                // Protokolliere den erfolgreichen Download mit FORTLAUFENDER NUMMER
                if (downloadProtokoll != null) {
                    downloadProtokoll.logSuccess(mqlVersion, providerName, globalProviderNumber);
                }
            }
            
            if (!stopRequested) {
                logger.debug("Kehre zur Übersichtsseite zurück");
                driver.get(pageUrl);
            }
            
        } catch (Exception e) {
            if (!stopRequested) {
                ErrorType errorType = classifyError(e);
                
                if (errorType == ErrorType.CRITICAL) {
                    logger.error("KRITISCHER FEHLER bei Provider '{}' (ID: {}) - stoppe sofort: {}", 
                                providerName, providerId, e.getMessage());
                    throw new RuntimeException("Kritischer Provider-Fehler", e);
                }
                
                // FORTLAUFENDE NUMERIERUNG auch in Fehlermeldungen
                int globalProviderNumber = totalProvidersProcessed;
                String errorMsg = "Fehler beim Verarbeiten von Provider '" + providerName + "' (ID: " + providerId + ", Fortlaufende Nr. " + (globalProviderNumber + 1) + "): " + e.getMessage();
                
                switch (errorType) {
                    case RECOVERABLE:
                        logger.warn("RECOVERY-FÄHIGER FEHLER - {}", errorMsg);
                        // Protokolliere den Fehlschlag mit FORTLAUFENDER NUMMER
                        if (downloadProtokoll != null) {
                            String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                            downloadProtokoll.logFailure(mqlVersionForLog, providerName, "Recovery-fähiger Fehler: " + e.getMessage(), globalProviderNumber);
                        }
                        throw e; // Weiterwerfen für Recovery in höherer Ebene
                        
                    case NON_CRITICAL:
                    default:
                        logger.warn("NICHT-KRITISCHER FEHLER - {}", errorMsg);
                        // Protokolliere den Fehlschlag mit FORTLAUFENDER NUMMER
                        if (downloadProtokoll != null) {
                            String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                            downloadProtokoll.logFailure(mqlVersionForLog, providerName, "Nicht-kritischer Fehler: " + e.getMessage(), globalProviderNumber);
                        }
                        
                        // KORRIGIERTE Fortschrittsanzeige für fehlgeschlagene Provider
                        updateProgress(providerName, "FEHLGESCHLAGEN (" + e.getMessage() + ")", false);
                        // Nicht weiterwerfen - mit nächstem Provider fortfahren
                        break;
                }
            }
        }
    }

    private void downloadProviderRootPage(String providerUrl, String providerId, String providerName) {
        if (stopRequested) return;

        try {
            String mqlVersion = configManager.getMqlVersion();
            String cleanProviderId = providerId;
            if (cleanProviderId.contains("?")) {
                cleanProviderId = cleanProviderId.substring(0, cleanProviderId.indexOf("?"));
            }
            
            String rootPageUrl = String.format("https://www.mql5.com/de/signals/%s?source=Site+Signals+%s+Table",
                    cleanProviderId, mqlVersion.toUpperCase());
            
            logger.debug("Lade Root-Seite für '{}': {}", providerName, rootPageUrl);
            driver.get(rootPageUrl);
            Thread.sleep(getRandomWaitTime());
            
            String pageSource = driver.getPageSource();
            
            String safeProviderName = providerName.replaceAll("[\\/:*?\"<>|\\s]+", "_");
            String targetPath = configManager.getCurrentDownloadPath();
            String htmlFileName = String.format("%s_%s_root.html", safeProviderName, cleanProviderId);
            
            File htmlFile = new File(targetPath, htmlFileName);
            try (FileWriter writer = new FileWriter(htmlFile)) {
                writer.write(pageSource);
            }
            
            long fileSizeKB = htmlFile.length() / 1024;
            logger.info("Root-Seite gespeichert für '{}' (ID: {}): {} ({} KB)", 
                       providerName, cleanProviderId, htmlFileName, fileSizeKB);
            
            // Log file details to protocol
            if (downloadProtokoll != null) {
                String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                downloadProtokoll.logFileDetails(mqlVersionForLog, providerName, cleanProviderId, 
                                               htmlFileName, fileSizeKB, null, 0);
            }
                
        } catch (Exception e) {
            if (!stopRequested) {
                ErrorType errorType = classifyError(e);
                String errorMsg = "Fehler beim Herunterladen der Root-Seite für '" + providerName + "' (ID: " + providerId + "): " + e.getMessage();
                
                // Protokolliere den Fehlschlag
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logFailure(mqlVersionForLog, providerName, "Root-Seite: " + e.getMessage());
                }
                
                switch (errorType) {
                    case CRITICAL:
                        logger.error("KRITISCHER FEHLER - {}", errorMsg);
                        throw new RuntimeException("Kritischer Fehler beim Root-Page Download", e);
                        
                    case RECOVERABLE:
                        logger.warn("RECOVERY-FÄHIGER FEHLER - {}", errorMsg);
                        boolean recovered = attemptRecovery(e);
                        if (!recovered) {
                            logger.error("Recovery fehlgeschlagen für Root-Page Download von '{}'", providerName);
                            throw new RuntimeException("Recovery fehlgeschlagen", e);
                        }
                        break;
                        
                    case NON_CRITICAL:
                    default:
                        logger.warn("NICHT-KRITISCHER FEHLER - {}", errorMsg);
                        // Bei nicht-kritischen Fehlern weitermachen
                        break;
                }
            }
        }
    }

    private void downloadTradeHistory(String providerUrl, String providerName) {
        if (stopRequested) return;

        try {
            logger.debug("Lade Trading History für '{}': {}", providerName, providerUrl);
            driver.get(providerUrl);
            
            WebElement tradeHistoryTab = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[text()='Trading history']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tradeHistoryTab);

            List<WebElement> exportLinks = driver.findElements(By.xpath("//*[text()='History']"));
            if (exportLinks.isEmpty()) {
                String warnMsg = "Kein Export-Link für Trading History gefunden bei Provider: " + providerName;
                logger.warn(warnMsg);
                
                // Protokolliere die fehlende History
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logFailure(mqlVersionForLog, providerName, "Trading History: Kein Export-Link gefunden");
                }
                return;
            }

            logger.debug("Starte CSV-Download für '{}'", providerName);
            WebElement exportLink = exportLinks.get(exportLinks.size() - 1);
            exportLink.click();

            handleDownloadedFile(providerName, providerUrl.substring(providerUrl.lastIndexOf("/") + 1));
            
        } catch (Exception e) {
            if (!stopRequested) {
                ErrorType errorType = classifyError(e);
                String errorMsg = "Fehler beim Herunterladen der Trading History für '" + providerName + "': " + e.getMessage();
                
                // Protokolliere den Fehlschlag
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logFailure(mqlVersionForLog, providerName, "Trading History: " + e.getMessage());
                }
                
                switch (errorType) {
                    case CRITICAL:
                        logger.error("KRITISCHER FEHLER - {}", errorMsg);
                        throw new RuntimeException("Kritischer Fehler beim Trading History Download", e);
                        
                    case RECOVERABLE:
                        logger.warn("RECOVERY-FÄHIGER FEHLER - {}", errorMsg);
                        boolean recovered = attemptRecovery(e);
                        if (!recovered) {
                            logger.error("Recovery fehlgeschlagen für Trading History Download von '{}'", providerName);
                            throw new RuntimeException("Recovery fehlgeschlagen", e);
                        }
                        break;
                        
                    case NON_CRITICAL:
                    default:
                        logger.warn("NICHT-KRITISCHER FEHLER - {}", errorMsg);
                        // Bei nicht-kritischen Fehlern weitermachen
                        break;
                }
            }
        }
    }

    private void handleDownloadedFile(String providerName, String signalProviderId) {
        if (stopRequested) return;

        try {
            Thread.sleep(getRandomWaitTime());
            File downloadedFile = findDownloadedFile(configManager.getDownloadPath());
            
            if (downloadedFile != null && downloadedFile.exists()) {
                String safeProviderName = providerName.replaceAll("[\\/:*?\"<>|\\s]+", "_");
                String originalId = downloadedFile.getName().replaceAll("[^0-9]", "");
                
                String targetPath = configManager.getCurrentDownloadPath();
                createDirectoryIfNotExists(targetPath);
                
                File targetFile = new File(targetPath, 
                    String.format("%s_%s.csv", safeProviderName, originalId));
                    
                Files.move(downloadedFile.toPath(), targetFile.toPath(), 
                    StandardCopyOption.REPLACE_EXISTING);
                
                long fileSizeKB = targetFile.length() / 1024;
                logger.info("CSV-Datei gespeichert für '{}' (ID: {}): {} ({} KB)", 
                           providerName, originalId, targetFile.getName(), fileSizeKB);
                
                // Update protocol with complete file information
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    // Find corresponding HTML file for complete logging
                    String htmlFileName = String.format("%s_%s_root.html", safeProviderName, originalId);
                    File htmlFile = new File(targetPath, htmlFileName);
                    long htmlSizeKB = htmlFile.exists() ? htmlFile.length() / 1024 : 0;
                    
                    downloadProtokoll.logFileDetails(mqlVersionForLog, providerName, originalId, 
                                                   htmlFileName, htmlSizeKB, targetFile.getName(), fileSizeKB);
                }
            } else {
                String warnMsg = "Keine CSV-Datei im Download-Verzeichnis gefunden für Provider: " + providerName;
                logger.warn(warnMsg);
                
                // Protokolliere den fehlenden Download
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logFailure(mqlVersionForLog, providerName, "CSV-Download: Keine Datei gefunden");
                }
            }
        } catch (Exception e) {
            if (!stopRequested) {
                ErrorType errorType = classifyError(e);
                String errorMsg = "Fehler beim Verarbeiten der CSV-Datei für '" + providerName + "': " + e.getMessage();
                
                // Protokolliere den Fehler beim Dateihandling
                if (downloadProtokoll != null) {
                    String mqlVersionForLog = configManager.getMqlVersion().startsWith("mt4") ? "mql4" : "mql5";
                    downloadProtokoll.logFailure(mqlVersionForLog, providerName, "CSV-Handling: " + e.getMessage());
                }
                
                switch (errorType) {
                    case CRITICAL:
                        logger.error("KRITISCHER FEHLER - {}", errorMsg);
                        throw new RuntimeException("Kritischer Fehler beim Dateihandling", e);
                        
                    case RECOVERABLE:
                        logger.warn("RECOVERY-FÄHIGER FEHLER - {}", errorMsg);
                        boolean recovered = attemptRecovery(e);
                        if (!recovered) {
                            logger.error("Recovery fehlgeschlagen für Dateihandling von '{}'", providerName);
                            throw new RuntimeException("Recovery fehlgeschlagen", e);
                        }
                        break;
                        
                    case NON_CRITICAL:
                    default:
                        logger.warn("NICHT-KRITISCHER FEHLER - {}", errorMsg);
                        // Bei nicht-kritischen Fehlern weitermachen
                        break;
                }
            }
        }
    }

    private void createDirectoryIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                logger.error("Konnte Verzeichnis nicht erstellen: " + path);
            }
        }
    }

    private static File findDownloadedFile(String downloadDirectory) {
        File dir = new File(downloadDirectory);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
        return files != null && files.length > 0 ? files[0] : null;
    }

    private int getRandomWaitTime() {
        int minWait = configManager.getMinWaitTime();
        int maxWait = configManager.getMaxWaitTime();
        return (int) (Math.random() * (maxWait - minWait)) + minWait;
    }
    
    /**
     * ERWEITERTE determineEndReason Methode mit maxPageNumber Parameter
     */
    private String determineEndReason(boolean hasNextPage, int processedProviders, int mqlLimit, 
                                    int consecutiveErrors, int maxPageNumber, int currentPage) {
        if (stopRequested) {
            return "BENUTZER-STOPP";
        } else if (totalProvidersProcessed >= mqlLimit) {
            return "LIMIT ERREICHT (" + mqlLimit + ")";
        } else if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            return "ZU VIELE FEHLER (" + consecutiveErrors + ")";
        } else if (maxPageNumber > 0 && currentPage > maxPageNumber) {
            return "MAXIMALE SEITENZAHL ERREICHT (" + maxPageNumber + ")";
        } else if (!hasNextPage) {
            return "KEINE WEITEREN SEITEN";
        } else {
            return "UNBEKANNT";
        }
    }
    
    /**
     * Holt das konfigurierte MQL-Limit basierend auf der aktuellen Version
     */
    private int getMqlLimit() {
        String mqlVersion = configManager.getMqlVersion();
        if (mqlVersion.startsWith("mt4")) {
            return configManager.getMql4Limit();
        } else {
            return configManager.getMql5Limit();
        }
    }
}