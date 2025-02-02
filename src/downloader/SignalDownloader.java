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
import org.openqa.selenium.net.UrlChecker.TimeoutException;
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
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

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

            // Versuche verschiedene Arten, die Provider-Links zu finden
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
                    logger.error("Fehler beim Verarbeiten von Provider " + i + " auf Seite " + pageUrl, e);
                    // Einzelner Provider-Fehler führt nicht zum Abbruch
                }
            }
            
        } catch (Exception e) {
            // Logge den Seiteninhalt für Debugging-Zwecke
            try {
                logger.debug("Aktueller Seiteninhalt: " + driver.getPageSource());
            } catch (Exception pageSourceError) {
                logger.error("Konnte Seiteninhalt nicht loggen", pageSourceError);
            }
            throw e;
        }
    }

    private void processSignalProvider(String pageUrl, int index) {
        if (stopRequested) return;

        List<WebElement> providerLinks = driver.findElements(By.cssSelector(".signal a[href*='/signals/']"));
        if (index >= providerLinks.size()) {
            logger.warn("Provider-Index außerhalb der Grenzen, überspringe");
            return;
        }

        WebElement link = providerLinks.get(index);
        String providerUrl = link.getAttribute("href");
        String providerName = link.getText().trim();
        
        // Extract provider ID from URL and clean it
        String providerId = providerUrl.substring(providerUrl.lastIndexOf("/") + 1);
        if (providerId.contains("?")) {
            providerId = providerId.substring(0, providerId.indexOf("?"));
        }

        // Download root page first
        downloadProviderRootPage(providerUrl, providerId, providerName);
        
        // Then download trade history if not stopped
        if (!stopRequested) {
            downloadTradeHistory(providerUrl, providerName);
            updateProgress(); // Update the counter after successful download
        }
        
        // Return to provider list if not stopped
        if (!stopRequested) {
            driver.get(pageUrl);
        }
    }

    private void downloadProviderRootPage(String providerUrl, String providerId, String providerName) {
        if (stopRequested) return;

        try {
            String mqlVersion = configManager.getMqlVersion(); // "mt4" or "mt5"
            String cleanProviderId = providerId;
            if (cleanProviderId.contains("?")) {
                cleanProviderId = cleanProviderId.substring(0, cleanProviderId.indexOf("?"));
            }
            
            // Construct the root page URL
            String rootPageUrl = String.format("https://www.mql5.com/de/signals/%s?source=Site+Signals+%s+Table",
                    cleanProviderId, mqlVersion.toUpperCase());
            
            // Navigate to the page
            driver.get(rootPageUrl);
            
            // Wait for the page to load
            Thread.sleep(getRandomWaitTime());
            
            // Get the page source
            String pageSource = driver.getPageSource();
            
            // Create a safe filename
            String safeProviderName = providerName.replaceAll("[\\/:*?\"<>|\\s]+", "_");
            String targetPath = configManager.getCurrentDownloadPath();
            String htmlFileName = String.format("%s_%s_root.html", safeProviderName, cleanProviderId);
            
            // Save the HTML content
            File htmlFile = new File(targetPath, htmlFileName);
            try (FileWriter writer = new FileWriter(htmlFile)) {
                writer.write(pageSource);
            }
            
            logger.info("Provider root page downloaded for " + providerName + 
                " (ID: " + cleanProviderId + "): " + htmlFile.getAbsolutePath());
                
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
                    
                logger.info("Datei heruntergeladen für " + providerName + 
                    " (ID: " + originalId + "): " + targetFile.getAbsolutePath());
            } else {
                logger.warn("Keine heruntergeladene Datei gefunden für Provider: " + providerName);
            }
        } catch (Exception e) {
            if (!stopRequested) {
                logger.error("Fehler beim Verarbeiten der heruntergeladenen Datei für " + providerName, e);
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
                consecutiveErrors = 0; // Reset error counter on success
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
                    
                    // Warte kurz und versuche die gleiche Seite nochmal
                    try {
                        Thread.sleep(getRandomWaitTime());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue; // Versuche die gleiche Seite nochmal
                }
            }
        }
    }
}