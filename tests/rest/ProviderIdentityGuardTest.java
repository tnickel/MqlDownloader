package rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.ConfigurationManager;
import database.DatabaseManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Preserved legacy rows/files must never become public MQL provider identities. */
class ProviderIdentityGuardTest {
    private static final String[] INVALID = {"test_1", "test_2", "page2", "0", "-7", "001", "1e3", "123\n", "１２３"};
    @TempDir Path dir;
    DatabaseManager database;
    RestApiServer server;
    String base;
    Path csvDir;

    @BeforeEach void start() throws Exception {
        ConfigurationManager config = new ConfigurationManager(dir.toString());
        config.initializeDirectories();
        database = new DatabaseManager(dir.toString());
        database.checkAndUpdateSubscribers("123", "mql5", "Actual", 1, "https://www.mql5.com/en/signals/123");
        database.checkAndUpdateSubscribers("123", "mql5", "Actual", 2, null);
        database.checkAndUpdateSubscribers("456", "mql4", "ActualZero", 0, "https://www.mql5.com/en/signals/456");
        for (String id : INVALID) {
            if (!id.equals(id.trim())) {
                seedUnnormalizedLegacyRow(id);
                continue;
            }
            database.checkAndUpdateSubscribers(id, "mql5", "LegacyLabel_" + id, 999, "https://www.mql5.com/en/signals/" + id);
            database.checkAndUpdateSubscribers(id, "mql5", "LegacyLabel_" + id, 1000, null);
        }
        csvDir = Files.createDirectories(dir.resolve("download/mql5"));
        for (String filename : new String[]{"Actual_123.csv", "Unknown.csv", "Zero_0.csv"}) {
            Files.write(csvDir.resolve(filename), "Time;Symbol\n2026.01.01 00:00:00;EURUSD\n".getBytes(StandardCharsets.UTF_8));
        }
        Files.write(csvDir.resolve("LegacyLabel_page2_root.txt"), "Currency=USD\n".getBytes(StandardCharsets.UTF_8));
        try (ServerSocket socket = new ServerSocket(0)) { config.setApiPort(socket.getLocalPort()); }
        server = new RestApiServer(database, config);
        server.start();
        base = "http://127.0.0.1:" + config.getApiPort() + "/api/v1";
    }

    /** The regular writer trims labels; use raw historical rows to test exact API/SQL boundaries. */
    private void seedUnnormalizedLegacyRow(String id) throws Exception {
        String dbPath = (dir + "/config/subscribers").replace("\\", "/");
        try (Connection c = DriverManager.getConnection("jdbc:h2:file:" + dbPath, "sa", "");
             PreparedStatement p = c.prepareStatement("INSERT INTO signal_subscribers"
                     + " (signal_id,mql_version,signal_name,subscribers,last_updated) VALUES (?, 'mql5', ?, 1000, CURRENT_TIMESTAMP)");
             PreparedStatement h = c.prepareStatement("INSERT INTO subscriber_history"
                     + " (signal_id,mql_version,signal_name,subscribers,change_amount,record_type,timestamp)"
                     + " VALUES (?, 'mql5', ?, 1000, ?, 'EVENT', CURRENT_TIMESTAMP)")) {
            p.setString(1, id); p.setString(2, "LegacyLabel_raw"); p.executeUpdate();
            h.setString(1, id); h.setString(2, "LegacyLabel_raw");
            for (int change : new int[]{0, 1}) { h.setInt(3, change); h.executeUpdate(); }
        }
    }

    @AfterEach void stop() throws Exception {
        if (server != null) server.stop();
        String dbPath = (dir + "/config/subscribers").replace("\\", "/");
        try (Connection c = DriverManager.getConnection("jdbc:h2:file:" + dbPath, "sa", "");
             Statement s = c.createStatement()) { s.execute("SHUTDOWN"); }
    }

    @Test void catalogTotalsSortingPaginationCsvAndSummaryExcludeLegacyLabelsWithoutDeletingRows() throws Exception {
        assertEquals(2, json("/health").get("providers").getAsInt());
        JsonObject first = json("/providers?minSubscribers=0&limit=1&offset=0");
        assertEquals(2, first.get("total").getAsInt());
        assertEquals(1, first.get("count").getAsInt());
        assertEquals("123", first.getAsJsonArray("items").get(0).getAsJsonObject().get("signalId").getAsString());
        JsonObject second = json("/providers?minSubscribers=0&limit=1&offset=1");
        assertEquals(2, second.get("total").getAsInt());
        assertEquals("456", second.getAsJsonArray("items").get(0).getAsJsonObject().get("signalId").getAsString());
        assertEquals(0, json("/providers?minSubscribers=0&limit=1&offset=2").get("count").getAsInt());
        assertFalse(response("/providers?format=csv&minSubscribers=0", 200).contains("LegacyLabel"));
        JsonObject summary = json("/summary");
        assertEquals(2, summary.getAsJsonObject("providers").get("total").getAsInt());
        assertEquals(2, summary.getAsJsonObject("subscribers").get("total").getAsInt());
        assertEquals(2 + INVALID.length, database.getAllSubscriberStatistics().size());
        assertTrue(database.getRecentEvents(null, null, false, 100).stream()
                .anyMatch(event -> "page2".equals(event.getSignalId())), "Raw historical evidence must remain readable");
    }

    @Test void invalidProviderRoutesNeverExposeDetailsHistoryCsvMetricsOrReports() throws Exception {
        for (String id : INVALID) {
            for (String suffix : new String[]{"", "/mql5", "/mql5/history", "/mql5/trades", "/mql5/trades.csv",
                    "/mql5/metrics", "/mql5/reports", "/mql5/reports/test.pdf"}) {
                response("/providers/" + URLEncoder.encode(id, StandardCharsets.UTF_8.name()) + suffix, 404);
            }
        }
        response("/providers/123", 200);
        response("/providers/123/mql5/trades.csv", 200);
        assertTrue(Files.isRegularFile(csvDir.resolve("LegacyLabel_page2_root.txt")));
        assertEquals(2 + INVALID.length, database.getAllSubscriberStatistics().size());
    }

    @Test void tradeCatalogAndDirectFilenameRoutesRejectUnownedOrZeroIdentityFilesWithoutDeletingThem() throws Exception {
        JsonObject trades = json("/trades?limit=1");
        assertEquals(1, trades.get("total").getAsInt());
        assertEquals("123", trades.getAsJsonArray("items").get(0).getAsJsonObject().get("signalId").getAsString());
        response("/trades/file/mql5/Unknown.csv", 404);
        response("/trades/file/mql5/Zero_0.csv", 404);
        response("/trades/file/mql5/Actual_123.csv", 200);
        assertEquals("Time;Symbol\n2026.01.01 00:00:00;EURUSD\n",
                new String(Files.readAllBytes(csvDir.resolve("Unknown.csv")), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(csvDir.resolve("Zero_0.csv")));
    }

    @Test void eventIdentityFilterPrecedesLimitAndKeepsRawLegacyChronicle() throws Exception {
        JsonObject latest = json("/events?limit=1&includeUnchanged=true");
        assertEquals(1, latest.get("count").getAsInt());
        assertEquals("456", latest.getAsJsonArray("items").get(0).getAsJsonObject().get("signalId").getAsString());
        JsonObject change = json("/events?limit=1");
        assertEquals("123", change.getAsJsonArray("items").get(0).getAsJsonObject().get("signalId").getAsString());
        assertFalse(response("/events?limit=100&includeUnchanged=true&format=csv", 200).contains("LegacyLabel"));
        assertEquals(3, database.getRecentProviderEvents(null, null, false, 100).size());
        assertEquals(3 + 2 * INVALID.length, database.getRecentEvents(null, null, false, 100).size());
    }

    private JsonObject json(String path) throws Exception {
        return JsonParser.parseString(response(path, 200)).getAsJsonObject();
    }

    private String response(String path, int expected) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(5000);
        try {
            int status = c.getResponseCode();
            assertEquals(expected, status, path);
            java.io.InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            try (java.io.InputStream in = stream) {
                return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
}
