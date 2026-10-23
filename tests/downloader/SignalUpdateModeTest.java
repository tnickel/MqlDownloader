package downloader;

import config.ConfigurationManager;
import database.SubscriberStat;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real file/cache/export code with an in-memory browser, never Selenium/HTTP. */
class SignalUpdateModeTest {
    private static final String HEADER = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n";
    @TempDir Path dir;
    private int exportClicks;
    private boolean missingExport;
    private boolean stopOnExport;
    private SignalDownloader active;

    private SignalDownloader fixture(boolean existing) throws Exception {
        Files.createDirectories(dir.resolve("config"));
        Files.createDirectories(dir.resolve("incoming"));
        ConfigurationManager config = new ConfigurationManager(dir.toString()) {
            @Override public String getDownloadPath() { return dir.resolve("incoming").toString(); }
            @Override public String getCurrentDownloadPath() { return dir.toString(); }
            @Override public String getMqlVersion() { return "mt5"; }
            @Override public int getMinWaitTime() { return 0; }
            @Override public int getMaxWaitTime() { return 0; }
        };
        if (existing) {
            Files.writeString(dir.resolve("Alpha_101_root.html"), "old html");
            Files.writeString(dir.resolve("Alpha_101.csv"), HEADER + row(1));
        }
        WebElement element = (WebElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{WebElement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("click")) {
                        exportClicks++;
                        if (stopOnExport) { active.setStopFlag(true); return null; }
                        Files.writeString(dir.resolve("incoming/101.positions.csv"), HEADER + row(2));
                    }
                    if (method.getName().equals("isDisplayed") || method.getName().equals("isEnabled")) return true;
                    return null;
                });
        WebDriver driver = (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{WebDriver.class, JavascriptExecutor.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCurrentUrl": return "https://www.mql5.com/en/signals/101";
                        case "getPageSource": return "<html>synthetic current metadata</html>";
                        case "findElement": return element;
                        case "findElements": return missingExport ? Collections.emptyList() : Arrays.asList(element);
                        case "toString": return "InMemoryBrowser";
                        default: return null;
                    }
                });
        active = new SignalDownloader(driver, config, null);
        return active;
    }

    private String row(int profit) {
        return "2026.10.01 10:00:00;Buy;0.10;EURUSD;1.1;0.10;2026.10.01 11:00:00;1.2;0;0;" + profit + "\n";
    }

    private void runKnown(SignalDownloader downloader, boolean trades) throws Exception {
        downloader.setUpdateMode(trades, Arrays.asList(
                new SubscriberStat("101", "mql5", "Alpha", 5, 0, null, null, null, null)));
        Method method = SignalDownloader.class.getDeclaredMethod("processKnownProviders");
        method.setAccessible(true);
        method.invoke(downloader);
    }

    @Test void updateActuallyReplacesRecentTradeFileInsteadOfReusingFiveDayCache() throws Exception {
        SignalDownloader downloader = fixture(true);
        assertTrue(downloader.isFileRecentlyDownloaded("101", "Alpha"));
        runKnown(downloader, true);
        assertEquals(1, exportClicks);
        assertEquals(HEADER + row(2), Files.readString(dir.resolve("Alpha_101.csv")));
        assertEquals(1, downloader.getUpdatedTradeLists()); assertEquals(0, downloader.getNewTradeLists());
        assertEquals(1, downloader.getSubscribersDownloadedCount());
    }

    @Test void catalogueOnlyModeRefreshesMetadataAndLeavesTradeFileUntouched() throws Exception {
        SignalDownloader downloader = fixture(true);
        runKnown(downloader, false);
        assertEquals(0, exportClicks);
        assertEquals(HEADER + row(1), Files.readString(dir.resolve("Alpha_101.csv")));
        assertTrue(Files.readString(dir.resolve("Alpha_101_root.html")).contains("current metadata"));
        assertEquals(0, downloader.getUpdatedTradeLists()); assertEquals(0, downloader.getNewTradeLists());
    }

    @Test void newlyDownloadedHistoryIsCountedSeparately() throws Exception {
        SignalDownloader downloader = fixture(false);
        runKnown(downloader, true);
        assertEquals(1, downloader.getNewTradeLists()); assertEquals(0, downloader.getUpdatedTradeLists());
    }

    @Test void missingExportPreservesOldCsvAndDoesNotInventUpdatedCount() throws Exception {
        SignalDownloader downloader = fixture(true); missingExport = true;
        runKnown(downloader, true);
        assertEquals(1, downloader.getFailedProvidersCount());
        assertEquals(0, downloader.getUpdatedTradeLists());
        assertEquals(HEADER + row(1), Files.readString(dir.resolve("Alpha_101.csv")));
    }

    @Test void stopKeepsAlreadyCompletedCountersAndStartsNoSecondDownload() throws Exception {
        SignalDownloader downloader = fixture(true);
        runKnown(downloader, true);
        downloader.setStopFlag(true);
        runKnown(downloader, true);
        assertEquals(1, exportClicks);
        assertEquals(1, downloader.getSubscribersDownloadedCount());
        assertEquals(1, downloader.getUpdatedTradeLists());
    }

    @Test void stopDuringExportDoesNotInventCompletedProviderOrTrade() throws Exception {
        SignalDownloader downloader = fixture(true); stopOnExport = true;
        runKnown(downloader, true);
        assertEquals(1, exportClicks);
        assertEquals(0, downloader.getSubscribersDownloadedCount());
        assertEquals(0, downloader.getUpdatedTradeLists());
        assertEquals(HEADER + row(1), Files.readString(dir.resolve("Alpha_101.csv")));
    }
}
