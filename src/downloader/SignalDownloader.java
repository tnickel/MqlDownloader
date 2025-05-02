package downloader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import config.ConfigurationManager;
import config.Credentials;

public class SignalDownloader {
    private final WebDriver driver;
    private final ConfigurationManager configManager;
    private final Credentials credentials;
    private final WebDriverWait wait;
    private String baseUrl;
    private static final Logger logger = LogManager.getLogger(SignalDownloader.class);
    private volatile boolean stopRequested;
    private int providerCount = 0;
    private ProgressCallback progressCallback;
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 2;

    public SignalDownloader(WebDriver driver, ConfigurationManager configManager, Credentials credentials) throws IOException {
        this.driver = driver;
        this.configManager = configManager;
        this.credentials = credentials;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        this.baseUrl = configManager.getMqlBaseUrl();
        this.stopRequested = false;
        this.providerCount = 0;
    }

    public void setStopFlag(boolean stopRequested) {
        this.stopRequested = stopRequested;
        if (stopRequested) {
            providerCount = 0;
        }
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    private void updateProgress() {
        providerCount++;
        if (progressCallback != null) {
            progressCallback.onProgress(providerCount);
        }
    }

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

    public void startDownloadProcess() {
        try {
            if (!stopRequested) performLogin();
            if (!stopRequested) processSignalProviders();
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("Fehler im Download-Prozess", e);
                throw e;
            }
        } finally {
            // Forciere Log-Flush
            if (providerCount > 0) {
                org.apache.logging.log4j.LogManager.shutdown();
                // Reinitialisiere den Logger, da shutdown() ihn schließt
                org.apache.logging.log4j.LogManager.getLogger(SignalDownloader.class);
            }
        }
    }

    private void performLogin() {
        logger.info("Starte Anmeldeprozess...");
        driver.get("https://www.mql5.com/en/auth_login");

        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("Login")));
        WebElement passwordField = driver.findElement(By.id("Password"));

        usernameField.sendKeys(credentials.getUsername());
        passwordField.sendKeys(credentials.getPassword());

        clickLoginButton();
        verifyLogin();
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

    private void processSignalProvidersPage(String pageUrl) {
        if (stopRequested) return;

        driver.get(pageUrl);
        
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("signal")));

            List<WebElement> providerLinks = new ArrayList<>();
            
            try {
                providerLinks = driver.findElements(By.cssSelector(".signal a[href*='/signals/']"));
            } catch (Exception e) {
                // Alternative Selektoren
                providerLinks = driver.findElements(By.cssSelector("a[href*='/signals/']"));
            }

            if (providerLinks.isEmpty()) {
                // Letzer Versuch: Suche nach allen Links die "signals" im href haben
                providerLinks = driver.findElements(By.cssSelector("a[href*='signals']"));
            }

            if (providerLinks.isEmpty()) {
                throw new RuntimeException("Keine Signal-Provider gefunden");
            }

            for (int i = 0; i < providerLinks.size() && !stopRequested; i++) {
                try {
                    processSignalProvider(pageUrl, i);
                } catch (Exception e) {
                    if (!stopRequested) {
                        logger.error("Fehler beim Verarbeiten von Provider " + i + " auf Seite " + pageUrl, e);
                    }
                }
            }
            
        } catch (Exception e) {
            try {
                logger.debug("Aktueller Seiteninhalt: " + driver.getPageSource());
            } catch (Exception pageSourceError) {
                logger.error("Konnte Seiteninhalt nicht loggen", pageSourceError);
            }
            throw e;
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

    private void processSignalProvider(String pageUrl, int index) {
        if (stopRequested) return;

        try {
            List<WebElement> providerLinks = driver.findElements(By.cssSelector(".signal a[href*='/signals/']"));
            if (index >= providerLinks.size() || stopRequested) {
                logger.debug("Provider-Index außerhalb der Grenzen oder Stop angefordert, überspringe");
                return;
            }

            WebElement link = providerLinks.get(index);
            String providerUrl = link.getAttribute("href");
            String providerName = link.getText().trim();
            
            // Provider ID extrahieren und bereinigen
            String providerId = providerUrl.substring(providerUrl.lastIndexOf("/") + 1);
            if (providerId.contains("?")) {
                providerId = providerId.substring(0, providerId.indexOf("?"));
            }

            // Prüfe, ob Dateien kürzlich heruntergeladen wurden
            if (isFileRecentlyDownloaded(providerId, providerName)) {
                logger.info("Provider #{} - {} (ID: {}) wurde kürzlich heruntergeladen, überspringe", 
                    providerCount + 1, providerName, providerId);
                updateProgress(); // Aktualisiere den Fortschritt auch für übersprungene Provider
                return;
            }

            downloadProviderRootPage(providerUrl, providerId, providerName);
            
            if (!stopRequested) {
                downloadTradeHistory(providerUrl, providerName);
                updateProgress();
            }
            
            if (!stopRequested) {
                driver.get(pageUrl);
            }
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("Fehler beim Verarbeiten von Provider " + index + " auf Seite " + pageUrl, e);
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
            
            logger.info("Provider #{} - Root page downloaded for {} (ID: {}): {}", 
            	    providerCount, providerName, cleanProviderId, htmlFile.getAbsolutePath());
                
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("Error downloading root page for provider: " + providerName, e);
            }
        }
    }

    private void downloadTradeHistory(String providerUrl, String providerName) {
        if (stopRequested) return;

        driver.get(providerUrl);
        
        WebElement tradeHistoryTab = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//*[text()='Trading history']")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", tradeHistoryTab);

        List<WebElement> exportLinks = driver.findElements(By.xpath("//*[text()='History']"));
        if (exportLinks.isEmpty()) {
            logger.warn("Kein Export-Link gefunden für Provider: " + providerName);
            return;
        }

        WebElement exportLink = exportLinks.get(exportLinks.size() - 1);
        exportLink.click();

        handleDownloadedFile(providerName, providerUrl.substring(providerUrl.lastIndexOf("/") + 1));
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
                    
                logger.info("Provider #{} - Datei heruntergeladen für {} (ID: {}): {}", 
                	    providerCount, providerName, originalId, targetFile.getAbsolutePath());
            } else {
                logger.warn("Keine heruntergeladene Datei gefunden für Provider: " + providerName+"Count="+providerCount);
            }
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("Fehler beim Verarbeiten der heruntergeladenen Datei für " + providerName+"Count="+providerCount, e);
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

    private void processSignalProviders() {
        int currentPage = 1;
        boolean hasNextPage = true;

        while (hasNextPage && !stopRequested) {
            String pageUrl = baseUrl + "/page" + currentPage;
            try {
                processSignalProvidersPage(pageUrl);
                currentPage++;
                consecutiveErrors = 0;
            } catch (RuntimeException e) {
                if (e.getMessage().equals("Keine Signal-Provider gefunden")) {
                    hasNextPage = false;
                } else {
                    consecutiveErrors++;
                    logger.error("Fehler beim Verarbeiten der Seite " + currentPage + 
                               " (Fehler " + consecutiveErrors + " von " + MAX_CONSECUTIVE_ERRORS + ")", e);
                    
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        logger.error("Zu viele aufeinanderfolgende Fehler (" + MAX_CONSECUTIVE_ERRORS + 
                                   "). Beende Download-Prozess.");
                        throw e;
                    }
                    
                    try {
                        Thread.sleep(getRandomWaitTime());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
            }
        }
    }
}