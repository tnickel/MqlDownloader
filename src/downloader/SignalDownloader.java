package downloader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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

    public SignalDownloader(WebDriver driver, ConfigurationManager configManager, Credentials credentials) throws IOException {
        this.driver = driver;
        this.configManager = configManager;
        this.credentials = credentials;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        this.baseUrl = configManager.getMqlBaseUrl();
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
            performLogin();
            processSignalProviders();
        } catch (Exception e) {
            logger.error("Fehler im Download-Prozess", e);
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

    private void processSignalProviders() {
        int currentPage = 1;
        boolean hasNextPage = true;

        while (hasNextPage) {
            String pageUrl = baseUrl + "/page" + currentPage;
            try {
                processSignalProvidersPage(pageUrl);
                currentPage++;
            } catch (RuntimeException e) {
                if (e.getMessage().equals("Keine Signal-Provider gefunden")) {
                    hasNextPage = false;
                } else {
                    throw e;
                }
            }
        }
    }

    private void processSignalProvidersPage(String pageUrl) {
        driver.get(pageUrl);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("signal")));

        List<WebElement> providerLinks = driver.findElements(By.cssSelector(".signal a[href*='/signals/']"));
        if (providerLinks.isEmpty()) {
            throw new RuntimeException("Keine Signal-Provider gefunden");
        }

        for (int i = 0; i < providerLinks.size(); i++) {
            processSignalProvider(pageUrl, i);
        }
    }

    private void processSignalProvider(String pageUrl, int index) {
        List<WebElement> providerLinks = driver.findElements(By.cssSelector(".signal a[href*='/signals/']"));
        if (index >= providerLinks.size()) {
            logger.warn("Provider-Index außerhalb der Grenzen, überspringe");
            return;
        }

        WebElement link = providerLinks.get(index);
        String providerUrl = link.getAttribute("href");
        String providerName = link.getText().trim();
        
        // Extract provider ID from URL and remove any additional parameters
        String providerId = providerUrl.substring(providerUrl.lastIndexOf("/") + 1);
        if (providerId.contains("?")) {
            providerId = providerId.substring(0, providerId.indexOf("?"));
        }
        
        // Download the root page first
        downloadProviderRootPage(providerUrl, providerId, providerName);
        
        // Then download the trade history
        downloadTradeHistory(providerUrl, providerName);
        
        // Return to the provider list
        driver.get(pageUrl);
    }

    private void downloadProviderRootPage(String providerUrl, String providerId, String providerName) {
        try {
            // Construct the root page URL
            String mqlVersion = configManager.getMqlVersion(); // "mt4" or "mt5"
            // Clean providerId to ensure it only contains the numeric part
            String cleanProviderId = providerId;
            if (cleanProviderId.contains("?")) {
                cleanProviderId = cleanProviderId.substring(0, cleanProviderId.indexOf("?"));
            }
            String rootPageUrl = String.format("https://www.mql5.com/de/signals/%s?source=Site+Signals+%s+Table",
                    cleanProviderId, mqlVersion.toUpperCase());
            
            // Navigate to the page
            driver.get(rootPageUrl);
            
            // Wait for the page to load
            Thread.sleep(getRandomWaitTime());
            
            // Get the page source
            String pageSource = driver.getPageSource();
            
            // Create a safe filename by removing invalid characters from both provider name and ID
            String safeProviderName = providerName.replaceAll("[\\/:*?\"<>|\\s]+", "_");
            // Clean up the providerId to ensure it only contains the numeric ID
            String safeProviderId = providerId;
            if (safeProviderId.contains("?")) {
                safeProviderId = safeProviderId.substring(0, safeProviderId.indexOf("?"));
            }
            String targetPath = configManager.getCurrentDownloadPath();
            String htmlFileName = String.format("%s_%s_root.html", safeProviderName, safeProviderId);
            
            // Save the HTML content
            File htmlFile = new File(targetPath, htmlFileName);
            try (FileWriter writer = new FileWriter(htmlFile)) {
                writer.write(pageSource);
            }
            
            logger.info("Provider root page downloaded for " + providerName + 
                " (ID: " + providerId + "): " + htmlFile.getAbsolutePath());
                
        } catch (Exception e) {
            logger.error("Error downloading root page for provider: " + providerName, e);
        }
    }

    private void downloadTradeHistory(String providerUrl, String providerName) {
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
        try {
            Thread.sleep(getRandomWaitTime());
            File downloadedFile = findDownloadedFile(configManager.getDownloadPath());
            
            if (downloadedFile != null && downloadedFile.exists()) {
                String safeProviderName = providerName.replaceAll("[\\/:*?\"<>|]", "_");
                String originalId = downloadedFile.getName().replaceAll("[^0-9]", "");
                
                // Den aktuellen spezifischen Download-Pfad (mql4 oder mql5) verwenden
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
            logger.error("Fehler beim Verarbeiten der heruntergeladenen Datei für " + providerName, e);
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

    private static int getRandomWaitTime() {
        return (int) (Math.random() * (30000 - 10000)) + 10000;
    }
}