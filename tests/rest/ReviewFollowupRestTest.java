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
