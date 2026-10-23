package rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.ConfigurationManager;
import database.DatabaseManager;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReviewFollowupRestTest {
    @TempDir Path dir;
    RestApiServer server;
    String base;

    @BeforeEach void start() throws Exception {
        ConfigurationManager config = new ConfigurationManager(dir.toString());
        config.initializeDirectories();
        DatabaseManager db = new DatabaseManager(dir.toString());
        db.checkAndUpdateSubscribers("123", "mql5", "Synthetic", 1, null);
        db.checkAndUpdateSubscribers("123", "mql5", "Synthetic", 2, null);
        try (ServerSocket socket = new ServerSocket(0)) { config.setApiPort(socket.getLocalPort()); }
        server = new RestApiServer(db, config);
        server.start();
        base = "http://127.0.0.1:" + config.getApiPort() + "/api/v1";
    }

    @AfterEach void stop() throws Exception {
        if (server != null) server.stop();
        String path = (dir + "/config/subscribers").replace("\\", "/");
        try (java.sql.Connection c = DriverManager.getConnection("jdbc:h2:file:" + path, "sa", "");
             java.sql.Statement s = c.createStatement()) { s.execute("SHUTDOWN"); }
    }

    @Test void includeUnchangedTrueIncludesBaselineButDefaultAndFalseDoNot() throws Exception {
        JsonObject normal = json("/events");
        assertEquals(1, normal.get("count").getAsInt());
        assertTrue(normal.get("onlyChanges").getAsBoolean());
        JsonObject all = json("/events?includeUnchanged=true");
        assertEquals(2, all.get("count").getAsInt());
        assertFalse(all.get("onlyChanges").getAsBoolean());
        assertEquals(1, json("/events?includeUnchanged=false").get("count").getAsInt());
        assertEquals(3, response("/events?includeUnchanged=true&format=csv", 200).split("\\R").length);
    }

    @Test void invalidDocumentedEnumValuesNeverSilentlySelectAnotherRepresentation() throws Exception {
        for (String query : new String[]{"/providers?sort=typo", "/providers?version=xyz",
                "/providers?order=sideways", "/providers?format=xml", "/events?format=xml",
                "/events?includeUnchanged=maybe", "/trades?version=xyz"}) {
            response(query, 400);
        }
        response("/providers?version=mt5&sort=latestChange&order=ASC&format=json", 200);
    }

    @Test void metricsExposeNativeCurrencyAndUnknownLegacyUnit() throws Exception {
        Path root = Files.createDirectories(dir.resolve("download/mql5")).resolve("Synthetic_123_root.txt");
        Files.write(root, "Balance=1234.56\nCurrency=USC\nAverage3MonthProfitMonthsIncluded=2\n".getBytes(StandardCharsets.UTF_8));
        JsonObject metrics = json("/providers/123/mql5/metrics").getAsJsonObject("metrics");
        assertEquals(1234.56, metrics.get("Balance").getAsDouble());
        assertEquals("USC", metrics.get("Currency").getAsString());
        assertEquals(2, metrics.get("Average3MonthProfitMonthsIncluded").getAsInt());
        Files.write(root, "Balance=1234.56\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("unknown", json("/providers/123/mql5/metrics").getAsJsonObject("metrics").get("Currency").getAsString());
    }

    @Test void metricsAndTradesUseSameRenamedProfileFamilyAndMissingSiblingIs404() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("download/mql5"));
        Path oldCsv = folder.resolve("Previous_123.csv");
        Path currentCsv = folder.resolve("Current_123.csv");
        Path oldMetrics = folder.resolve("Previous_123_root.txt");
        Path currentMetrics = folder.resolve("Current_123_root.txt");
        byte[] history = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n".getBytes(StandardCharsets.UTF_8);
        byte[] oldData = "Balance=999\nCurrency=EUR\n".getBytes(StandardCharsets.UTF_8);
        Files.write(oldCsv, history); Files.write(currentCsv, history);
        Files.write(oldMetrics, oldData);
        Files.write(currentMetrics, "Balance=100\nCurrency=USD\n".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(oldCsv, java.nio.file.attribute.FileTime.fromMillis(1000));
        Files.setLastModifiedTime(currentCsv, java.nio.file.attribute.FileTime.fromMillis(2000));
        Files.setLastModifiedTime(currentMetrics, java.nio.file.attribute.FileTime.fromMillis(3000));
        Files.setLastModifiedTime(oldMetrics, java.nio.file.attribute.FileTime.fromMillis(4000));
        assertEquals("Current_123.csv", json("/providers/123/mql5/trades").getAsJsonObject("file").get("name").getAsString());
        JsonObject metrics = json("/providers/123/mql5/metrics");
        assertEquals("Current_123_root.txt", metrics.getAsJsonObject("file").get("name").getAsString());
        assertEquals("USD", metrics.getAsJsonObject("metrics").get("Currency").getAsString());
        assertEquals(100, metrics.getAsJsonObject("metrics").get("Balance").getAsInt());
        Files.delete(currentMetrics);
        response("/providers/123/mql5/metrics", 404);
        assertArrayEquals(history, Files.readAllBytes(currentCsv));
        assertArrayEquals(oldData, Files.readAllBytes(oldMetrics));
    }

    @Test void legacyMetricsRecoverExplicitCurrencyFromIdentifiedMatchingHtmlWithoutWritingFiles() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("download/mql5"));
        Path root = folder.resolve("Synthetic_123_root.txt");
        Path html = folder.resolve("Synthetic_123_root.html");
        byte[] legacy = "Balance=1234,56\nSubscribers=2\n".getBytes(StandardCharsets.UTF_8);
        Files.write(root, legacy);
        for (String currency : new String[]{"USD", "EUR", "USC"}) {
            byte[] evidence = accountHtml("123", "5", "1 234.56 " + currency).getBytes(StandardCharsets.UTF_8);
            Files.write(html, evidence);
            JsonObject metrics = json("/providers/123/mql5/metrics").getAsJsonObject("metrics");
            assertEquals(currency, metrics.get("Currency").getAsString());
            assertEquals("1234,56", metrics.get("Balance").getAsString());
            assertArrayEquals(legacy, Files.readAllBytes(root));
            assertArrayEquals(evidence, Files.readAllBytes(html));
        }
        // A declared native unit is preserved even when another HTML unit exists.
        Files.write(root, "Balance=1234.56\nCurrency=EUR\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("EUR", json("/providers/123/mql5/metrics").getAsJsonObject("metrics").get("Currency").getAsString());
    }

    @Test void legacyMetricsNeverGuessCurrencyFromForeignOrAmbiguousEvidence() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("download/mql5"));
        Files.write(folder.resolve("Synthetic_123_root.txt"), "Balance=1234.56\n".getBytes(StandardCharsets.UTF_8));
        Path html = folder.resolve("Synthetic_123_root.html");
        for (String evidence : new String[]{
                accountHtml("999", "5", "1234.56 USD"),
                accountHtml("123", "4", "1234.56 USD"),
                accountHtml("123", "5", "9999.99 USD"),
                accountHtml("123", "5", "1234.56 USD / EUR"),
                accountHtml("123", "5", "1234.56 USD") + accountHtml("123", "5", "1234.56 EUR"),
                accountHtml("123", "5", "1234.56") + "<p>USD EURUSD</p>",
                accountHtml("123", "5", "1234.56 USD") + "<link rel='canonical' href='https://www.mql5.com/en/signals/999'>",
                "<div class='s-list-info__item'>Balance: <span class='s-list-info__value'>1234.56 USD</span></div>"}) {
            Files.write(html, evidence.getBytes(StandardCharsets.UTF_8));
            assertEquals("unknown", json("/providers/123/mql5/metrics").getAsJsonObject("metrics").get("Currency").getAsString());
        }
    }

    private String accountHtml(String id, String mt, String balance) {
        return "<div data-id='" + id + "' data-mt='" + mt + "'></div>"
                + "<div class='s-list-info__item'>Balance: <span class='s-list-info__value'>"
                + balance + "</span></div>";
    }

    JsonObject json(String path) throws Exception { return JsonParser.parseString(response(path, 200)).getAsJsonObject(); }
    String response(String path, int expected) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(5000);
        try {
            assertEquals(expected, c.getResponseCode(), path);
            try (java.io.InputStream in = expected >= 400 ? c.getErrorStream() : c.getInputStream()) {
                return new String(read(in), StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
    byte[] read(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[1024]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n);
        return out.toByteArray();
    }
}
