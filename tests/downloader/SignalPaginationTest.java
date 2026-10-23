package downloader;

import config.ConfigurationManager;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import static org.junit.jupiter.api.Assertions.*;

class SignalPaginationTest {
    @TempDir Path dir;

    @Test void subscriberSortSurvivesBothPlatformPathsAndEncodedFilters() {
        for (String version : Arrays.asList("mt4", "mt5")) {
            String root = "https://www.mql5.com/en/signals/" + version;
            assertEquals(root + "/list?orderby=subscribers",
                    SignalDownloader.providerListPageUrl(root + "/list", 1, true));
            assertEquals(root + "/list/page2?filter=a%26b&orderby=subscribers",
                    SignalDownloader.providerListPageUrl(root + "/list?filter=a%26b&orderby=growth", 2, true));
            assertEquals(root + "/list/page3?orderby=growth",
                    SignalDownloader.providerListPageUrl(root + "/list/page2?orderby=growth", 3, false));
            assertEquals(root + "/list/page4",
                    SignalDownloader.providerListPageUrl(root + "/list", 4, false));
            assertEquals(root + "/list/page2?orderby=subscribers",
                    SignalDownloader.providerListPageUrl(root + "/page1", 2, true));
        }
    }

    @Test void realListLoopReachesSubscribersAfterPageOneAndPreservesReturnUrls() throws Exception {
        checkListLoop(false);
    }
    @Test void subscriberOnlyListCollectsAcrossPagesWithoutAnyProfileOrTradeNavigation() throws Exception {
        checkListLoop(true);
    }
    private void checkListLoop(boolean listOnly) throws Exception {
        Files.createDirectories(dir.resolve("config"));
        ConfigurationManager config = new ConfigurationManager(dir.toString()) {
            @Override public String getMqlBaseUrl() { return "https://www.mql5.com/en/signals/mt4/list"; }
            @Override public String getMqlVersion() { return "mt4"; }
            @Override public boolean isSubscribersOnly() { return !listOnly; }
            @Override public int getMql4Limit() { return 20; }
            @Override public String getCurrentDownloadPath() { return dir.toString(); }
            @Override public String getDownloadPath() { return dir.toString(); }
            @Override public int getMinWaitTime() { return 0; }
            @Override public int getMaxWaitTime() { return 0; }
        };
        AtomicReference<String> url = new AtomicReference<>("");
        List<String> navigations = new ArrayList<>();
        WebElement header = element((method, args) -> {
            if (method.equals("getText")) return "Subscribers";
            if (method.equals("click")) url.set(url.get().split("\\?")[0] + "?orderby=subscribers");
            return null;
        });
        WebDriver driver = (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{WebDriver.class, JavascriptExecutor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) {
                        url.set((String) args[0]); navigations.add(url.get()); return null;
                    }
                    if (method.getName().equals("getCurrentUrl")) return url.get();
                    if (method.getName().equals("getPageSource")) return "<html>synthetic profile</html>";
                    if (method.getName().equals("findElement")) return header;
                    if (method.getName().equals("findElements")) {
                        String selector = args[0].toString();
                        if (selector.contains("//th")) return Collections.singletonList(header);
                        if (selector.contains(".paging a")) {
                            List<WebElement> pages = new ArrayList<>();
                            for (int i = 1; i <= 3; i++) {
                                final int page = i;
                                pages.add(element((name, values) -> name.equals("getText") ? String.valueOf(page)
                                        : name.equals("getAttribute") ? "https://www.mql5.com/en/signals/mt4/list/page" + page + "?orderby=subscribers"
                                        : null));
                            }
                            return pages;
                        }
                        if (selector.contains("/signals/")) {
                            // The real table-view paginator observed on 18 October
                            // retains /list. A tile/navigation page has no rows.
                            if (!url.get().contains("/mt4/list")) return Collections.singletonList(navigationLink());
                            int page = url.get().contains("/page3") ? 3 : url.get().contains("/page2") ? 2 : 1;
                            if (page > 1 && !url.get().contains("orderby=subscribers"))
                                return Collections.singletonList(provider(999, 0));
                            if (page == 1) return Arrays.asList(provider(101, 10), provider(102, 8));
                            if (page == 2) return Arrays.asList(provider(103, 7), provider(104, 4));
                            return Arrays.asList(provider(105, 1), provider(106, 0));
                        }
                        return Collections.emptyList();
                    }
                    return null;
                });
        SignalDownloader downloader = new SignalDownloader(driver, config, null);
        downloader.setUpdateMode(false, null);
        if(listOnly) downloader.setSubscriberListMode(5);
        Method run = SignalDownloader.class.getDeclaredMethod("processSignalProviders");
        run.setAccessible(true); run.invoke(downloader);
        assertEquals(5, downloader.getSubscribersDownloadedCount());
        assertEquals(0, downloader.getFailedProvidersCount());
        Field stopped = SignalDownloader.class.getDeclaredField("stopRequested");
        stopped.setAccessible(true);
        assertEquals(false, stopped.get(downloader));
        Method reason = SignalDownloader.class.getDeclaredMethod("determineEndReason", boolean.class, int.class, int.class, int.class, int.class, int.class);
        reason.setAccessible(true);
        if(!listOnly) assertTrue(reason.invoke(downloader, true, 5, 20, 0, 3, 4).toString().startsWith("ABONNENTEN-GRENZE"));
        for (int id = 101; id <= 105; id++) assertEquals(!listOnly, Files.exists(dir.resolve("Provider" + id + "_" + id + "_root.html")));
        assertFalse(Files.exists(dir.resolve("Provider106_106_root.html")));
        List<String> listRequests = new ArrayList<>();
        for (String value : navigations) if (value.contains("/mt4/")) listRequests.add(value);
        assertTrue(listRequests.stream().allMatch(value -> value.contains("orderby=subscribers")));
        assertTrue(listRequests.stream().allMatch(value -> value.contains("/mt4/list")));
        assertTrue(listRequests.stream().anyMatch(value -> value.contains("/page3?")));
        // Exactly one page load plus one return per processed provider.
        assertEquals(listOnly?3:8, listRequests.size());
        if(listOnly) {
            assertEquals(5,downloader.getSubscriberList().size());
            assertEquals(0,downloader.getNewTradeLists());
            assertEquals(0,downloader.getUpdatedTradeLists());
            assertEquals(navigations.size(),listRequests.size(),"No individual provider URLs");
        }
    }

    @Test void missingSubscriberEvidenceDoesNotBecomeZeroOrACompleteList() throws Exception {
        WebElement unknown = providerWithCount(201, element((name, values) -> name.equals("getText") ? "N/A" : null));
        SignalDownloader downloader = prepared(Collections.singletonList(unknown));
        Method run = SignalDownloader.class.getDeclaredMethod("processSignalProviders");
        run.setAccessible(true);
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> run.invoke(downloader));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("unbekannt"));
        assertEquals(1, downloader.getFailedProvidersCount());
        assertEquals(0, downloader.getSubscribersDownloadedCount());
        assertFalse(Files.exists(dir.resolve("Provider201_201_root.html")));
    }

    @Test void navigationLinksOnAnErrorPageCannotSupplyAProviderOrAZeroBoundary() throws Exception {
        SignalDownloader downloader = prepared(Collections.singletonList(navigationLink()));
        Method run = SignalDownloader.class.getDeclaredMethod("processSignalProviders");
        run.setAccessible(true);
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> run.invoke(downloader));
        assertTrue(error.getCause().getMessage().contains("nicht mit numerischen Signal-Links"));
        assertEquals(1, downloader.getFailedProvidersCount());
        Field boundary = SignalDownloader.class.getDeclaredField("subscriberBoundaryReached");
        boundary.setAccessible(true);
        assertEquals(false, boundary.get(downloader));
    }

    @Test void asynchronouslyArrivingSubscriberCountIsWaitedForInsteadOfZero() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        WebElement delayed = providerWithCount(301, element((name, values) -> name.equals("getText")
                ? (reads.incrementAndGet() <= 8 ? "" : "3") : null));
        SignalDownloader downloader = prepared(Collections.singletonList(delayed));
        Field wait = SignalDownloader.class.getDeclaredField("wait");
        wait.setAccessible(true);
        WebDriver driver = (WebDriver) field(downloader, "driver");
        wait.set(downloader, new WebDriverWait(driver, Duration.ofSeconds(1)).pollingEvery(Duration.ofMillis(5)));
        Method page = SignalDownloader.class.getDeclaredMethod("processSignalProvidersPage", String.class);
        page.setAccessible(true);
        assertEquals(true, page.invoke(downloader, "https://www.mql5.com/en/signals/mt4/list?orderby=subscribers"));
        assertTrue(reads.get() > 8);
        assertEquals(1, downloader.getSubscribersDownloadedCount());
        assertTrue(Files.exists(dir.resolve("Provider301_301_root.html")));
    }

    @Test void zeroBeforeLaterSubscribersIsAnUnverifiedSortOrder() throws Exception {
        SignalDownloader downloader = prepared(Arrays.asList(provider(401, 1), provider(402, 0), provider(403, 2)));
        Method run = SignalDownloader.class.getDeclaredMethod("processSignalProviders");
        run.setAccessible(true);
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> run.invoke(downloader));
        assertTrue(error.getCause().getMessage().contains("nicht absteigend"));
        assertEquals(1, downloader.getFailedProvidersCount());
        assertFalse(Files.exists(dir.resolve("Provider401_401_root.html")));
    }

    @Test void subscriberOnlyModeRejectsAnIncreaseAcrossPageBoundariesBeforeCollecting() throws Exception {
        SignalDownloader downloader=prepared(Arrays.asList(provider(501,4),provider(502,2)));
        downloader.setSubscriberListMode(200);
        Field previous=SignalDownloader.class.getDeclaredField("previousListSubscribers");
        previous.setAccessible(true);previous.set(downloader,3);
        Method page=SignalDownloader.class.getDeclaredMethod("processSignalProvidersPage",String.class);
        page.setAccessible(true);
        InvocationTargetException error=assertThrows(InvocationTargetException.class,
            ()->page.invoke(downloader,"https://www.mql5.com/en/signals/mt4/list/page2?orderby=subscribers"));
        assertTrue(error.getCause().getMessage().contains("zwischen Listenseiten"));
        assertTrue(downloader.getSubscriberList().isEmpty());
        assertEquals(0,downloader.getSubscribersDownloadedCount());
    }

    @Test void onlyExplicitIntegerCountsCanProduceAZeroSubscriberBoundary() {
        assertEquals(0, SignalDownloader.parseSubscriberCount("0"));
        assertEquals(1200, SignalDownloader.parseSubscriberCount("1\u202f200"));
        assertEquals(1200, SignalDownloader.parseSubscriberCount("1,200"));
        for (String unknown : Arrays.asList("", "N/A", "-", "5%", "3.2", "2147483648"))
            assertNull(SignalDownloader.parseSubscriberCount(unknown));
        assertNull(SignalDownloader.parseSubscriberCount(null));
    }

    @Test void visiblePageWindowIsNotAnEndProofForTheSubscriberSelection() throws Exception {
        SignalDownloader downloader = subscriberPages(Arrays.asList(
                Arrays.asList(provider(601, 10), provider(602, 8)),
                Arrays.asList(provider(603, 7), provider(604, 4)),
                Arrays.asList(provider(605, 1))), 1, 5);
        Method run = SignalDownloader.class.getDeclaredMethod("processSignalProviders");
        run.setAccessible(true); run.invoke(downloader);
        assertEquals(5, downloader.getSubscriberList().size(),
                "The visible page window cannot truncate a subscriber selection before its target");
        assertEquals(true,downloader.getSubscriberListResult().proof().get("targetReached"));
        assertEquals(false,downloader.getSubscriberListResult().proof().get("zeroBoundaryReached"));
    }

    @Test void shortPrefixRequiresAnActualZeroBoundaryInsteadOfVisibleLastPage() throws Exception {
        SignalDownloader downloader = subscriberPages(Arrays.asList(
                Arrays.asList(provider(611, 8), provider(612, 4)),
                Arrays.asList(provider(613, 1))), 2, 200);
        Method run=SignalDownloader.class.getDeclaredMethod("processSignalProviders");run.setAccessible(true);
        InvocationTargetException error=assertThrows(InvocationTargetException.class,()->run.invoke(downloader));
        assertTrue(error.getCause().getMessage().contains("verifiziert"));
        assertEquals(3,downloader.getSubscriberList().size());
        assertThrows(IllegalStateException.class,downloader::getSubscriberListResult);
        assertEquals(1,downloader.getFailedProvidersCount());
    }

    @Test void actualZeroBoundaryProvesAValidShortOrEmptySubscriberSelection() throws Exception {
        for(boolean empty:Arrays.asList(false,true)) {
            SignalDownloader downloader = subscriberPages(Arrays.asList(empty
                    ? Arrays.asList(provider(620, 0)) : Arrays.asList(provider(621, 4),provider(622, 1),provider(623, 0))), 1, 200);
            Method run=SignalDownloader.class.getDeclaredMethod("processSignalProviders");run.setAccessible(true);run.invoke(downloader);
            assertEquals(empty?0:2,downloader.getSubscriberListResult().providers().size());
            assertEquals(true,downloader.getSubscriberListResult().proof().get("zeroBoundaryReached"));
            assertEquals(false,downloader.getSubscriberListResult().proof().get("targetReached"));
        }
    }

    @Test void unstartedOrStoppedSubscriberReadCannotReturnAFreshReceipt() throws Exception {
        SignalDownloader downloader=subscriberPages(Arrays.asList(Arrays.asList(provider(631,0))),1,200);
        assertThrows(IllegalStateException.class,downloader::getSubscriberListResult);
        downloader.setStopFlag(true);
        Method run=SignalDownloader.class.getDeclaredMethod("processSignalProviders");run.setAccessible(true);run.invoke(downloader);
        assertThrows(IllegalStateException.class,downloader::getSubscriberListResult);
    }

    private SignalDownloader subscriberPages(List<List<WebElement>> pages, int visibleMax, int target) throws Exception {
        Files.createDirectories(dir.resolve("config"));
        ConfigurationManager config = new ConfigurationManager(dir.toString()) {
            @Override public String getMqlBaseUrl() { return "https://www.mql5.com/en/signals/mt4/list"; }
            @Override public String getMqlVersion() { return "mt4"; }
            @Override public String getCurrentDownloadPath() { return dir.toString(); }
            @Override public String getDownloadPath() { return dir.toString(); }
            @Override public int getMinWaitTime() { return 0; }
            @Override public int getMaxWaitTime() { return 0; }
        };
        AtomicReference<String> url = new AtomicReference<>("");
        WebDriver driver = (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{WebDriver.class}, (proxy, method, args) -> {
            if (method.getName().equals("get")) { url.set((String) args[0]); return null; }
            if (method.getName().equals("getCurrentUrl")) return url.get();
            if (method.getName().equals("findElements")) {
                String selector = args[0].toString();
                if (selector.contains("paging") || selector.contains("pagination") || selector.contains("/page")) {
                    if (visibleMax == 0) return Collections.emptyList();
                    return Collections.singletonList(element((name, values) -> name.equals("getText") ? ""+visibleMax
                            : name.equals("getAttribute") ? "https://www.mql5.com/en/signals/mt4/list/page"+visibleMax : null));
                }
                if (selector.contains("/signals/") || selector.contains("signal")) {
                    java.util.regex.Matcher match = java.util.regex.Pattern.compile("/page([0-9]+)").matcher(url.get());
                    int page = match.find() ? Integer.parseInt(match.group(1)) : 1;
                    return page <= pages.size() ? pages.get(page-1) : Collections.emptyList();
                }
                return Collections.emptyList();
            }
            return null;
        });
        SignalDownloader downloader = new SignalDownloader(driver, config, null);
        downloader.setSubscriberListMode(target);
        Field wait = SignalDownloader.class.getDeclaredField("wait"); wait.setAccessible(true);
        wait.set(downloader, new WebDriverWait(driver, Duration.ofMillis(30)).pollingEvery(Duration.ofMillis(5)));
        return downloader;
    }

    private Object field(Object owner, String name) throws Exception {
        Field field = SignalDownloader.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private SignalDownloader prepared(List<WebElement> rows) throws Exception {
        Files.createDirectories(dir.resolve("config"));
        ConfigurationManager config = new ConfigurationManager(dir.toString()) {
            @Override public String getMqlBaseUrl() { return "https://www.mql5.com/en/signals/mt4/list"; }
            @Override public String getMqlVersion() { return "mt4"; }
            @Override public boolean isSubscribersOnly() { return true; }
            @Override public int getMql4Limit() { return 20; }
            @Override public String getCurrentDownloadPath() { return dir.toString(); }
            @Override public String getDownloadPath() { return dir.toString(); }
            @Override public int getMinWaitTime() { return 0; }
            @Override public int getMaxWaitTime() { return 0; }
        };
        AtomicReference<String> url = new AtomicReference<>("");
        WebDriver driver = (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{WebDriver.class}, (proxy, method, args) -> {
            if (method.getName().equals("get")) { url.set((String) args[0]); return null; }
            if (method.getName().equals("getCurrentUrl")) return url.get();
            if (method.getName().equals("getPageSource")) return "<html>synthetic identified profile</html>";
            if (method.getName().equals("findElements")) return args[0].toString().contains("/signals/") ? rows : Collections.emptyList();
            return null;
        });
        SignalDownloader downloader = new SignalDownloader(driver, config, null);
        downloader.setUpdateMode(false, null);
        Field wait = SignalDownloader.class.getDeclaredField("wait");
        wait.setAccessible(true);
        wait.set(downloader, new WebDriverWait(driver, Duration.ofMillis(30)).pollingEvery(Duration.ofMillis(5)));
        return downloader;
    }

    private WebElement navigationLink() {
        return element((name, values) -> name.equals("getAttribute") ? "https://www.mql5.com/en/signals/mt4/list"
                : name.equals("getText") ? "Signals" : null);
    }

    private WebElement provider(int id, int subscribers) {
        WebElement count = element((name, values) -> name.equals("getText") ? String.valueOf(subscribers) : null);
        return providerWithCount(id, count);
    }

    private WebElement providerWithCount(int id, WebElement count) {
        WebElement row = element((name, values) -> name.equals("findElement") ? count : null);
        return element((name, values) -> {
            if (name.equals("getText")) return "Provider" + id;
            if (name.equals("getAttribute")) return "https://www.mql5.com/en/signals/" + id;
            if (name.equals("findElement")) return row;
            return null;
        });
    }

    private interface ElementCall { Object invoke(String method, Object[] args) throws Exception; }
    private WebElement element(ElementCall call) {
        return (WebElement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{WebElement.class},
                (proxy, method, args) -> call.invoke(method.getName(), args));
    }
}
