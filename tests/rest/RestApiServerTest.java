package rest;

import config.ConfigurationManager;
import database.DatabaseManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrationstest: startet den RestApiServer gegen eine temporäre H2-Datenbank
 * und temporäre Download-/Analyse-Verzeichnisse und prüft alle wichtigen
 * Endpunkte über echtes HTTP.
 */
class RestApiServerTest {
    @TempDir
    static Path tempDir;

    static ConfigurationManager config;
    static DatabaseManager database;
    static RestApiServer server;
    static int port;

    @BeforeAll
    static void setUp() throws Exception {
        config = new ConfigurationManager(tempDir.toString());
        config.initializeDirectories();
        database = new DatabaseManager(tempDir.toString());

        // Zwei Provider seeden (Baseline + Aenderung) und mql4-Leerfall vermeiden
        database.checkAndUpdateSubscribers("123", "mql5", "Test Provider Alpha", 42,
                "https://www.mql5.com/en/signals/123");
        database.checkAndUpdateSubscribers("123", "mql5", "Test Provider Alpha", 45, null);
        database.checkAndUpdateSubscribers("55", "mql4", "Beta Gold", 7,
                "https://www.mql5.com/en/signals/55");

        // Dateisatz: Trading-History-CSV, Kennzahlen-TXT, Report-PDF
        Path mql5 = tempDir.resolve("download").resolve("mql5");
        Files.createDirectories(mql5);
        String csv = "Open Time;Symbol;Type;Volume\n"
                + "2025.01.02 10:00:00;EURUSD;buy;0.10\n"
                + "2025.01.03 11:30:00;GBPUSD;sell;0.20\n";
        Files.write(mql5.resolve("Test_Provider_Alpha_123.csv"), csv.getBytes(StandardCharsets.UTF_8));
        String rootTxt = "Balance=1234.56\n"
                + "Subscribers=45\n"
                + "MaxDDGraphic=10.00\n"
                + "EquityDrawdown=8.50\n"
                + "Average3MonthProfit=2.25\n"
                + "StabilityValue=0.75\n"
                + "MonthProfitProz=2025.06=1.5,2025.07=-0.5\n"
                + "3MPDD=0.8123\n"
                + "********************************\n"
                + "\n"
                + "Drawdown Chart Data=\n"
                + "2025.01.05: 1.23%\n"
                + "-----------------\n";
        Files.write(mql5.resolve("Test_Provider_Alpha_123_root.txt"),
                rootTxt.getBytes(StandardCharsets.UTF_8));
        Path analyse = tempDir.resolve("analyse");
        Files.createDirectories(analyse);
        byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x74, 0x65, 0x73, 0x74};
        Files.write(analyse.resolve("testreport_123.pdf"), pdfBytes);
        config.setAnalysePath(analyse.toString());

        ServerSocket probe = new ServerSocket(0);
        port = probe.getLocalPort();
        probe.close();
        config.setApiPort(port);

        server = new RestApiServer(database, config);
        server.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        // H2-Datei freigeben, damit JUnit das TempDir auf Windows loeschen kann
        String dbPath = (tempDir + "/config/subscribers").replace("\\", "/");
        try (Statement stmt = DriverManager.getConnection("jdbc:h2:file:" + dbPath,
                "sa", "").createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }

    @Test
    void servesAllEndpoints() throws Exception {
        // Health
        HttpResponse health = get("/api/v1/health", null);
        assertEquals(200, health.status);
        assertTrue(health.body.contains("\"status\":\"ok\""));
        assertTrue(health.body.contains("\"providers\":2"));
        assertTrue(health.body.contains("\"tokenRequired\":false"));

        // Provider-Liste mit Filter und Alias mt5
        HttpResponse providers = get("/api/v1/providers?version=mt5", null);
        assertEquals(200, providers.status);
        assertTrue(providers.body.contains("Test Provider Alpha"));
        assertTrue(providers.body.contains("\"signalId\":\"123\""));
        assertTrue(providers.body.contains("\"subscribers\":45"));
        assertTrue(providers.body.contains("\"links\""));

        // CSV-Format
        HttpResponse providersCsv = get("/api/v1/providers?format=csv", null);
        assertEquals(200, providersCsv.status);
        assertTrue(providersCsv.body.startsWith("signalId;mqlVersion"));
        assertTrue(providersCsv.body.contains("Beta Gold"));

        // Filter ohne Treffer
        HttpResponse empty = get("/api/v1/providers?name=existiert+nicht", null);
        assertEquals(200, empty.status);
        assertTrue(empty.body.contains("\"total\":0"));

        // Provider-Detail ueber alle Versionen
        HttpResponse detail = get("/api/v1/providers/123", null);
        assertEquals(200, detail.status);
        assertTrue(detail.body.contains("\"count\":1"));

        // Historie: Baseline (0) + Ereignis (+3)
        HttpResponse history = get("/api/v1/providers/123/mql5/history", null);
        assertEquals(200, history.status);
        assertTrue(history.body.contains("\"total\":2"));
        assertTrue(history.body.contains("\"change\":3"));

        // Historie mit days-Filter schliesst nichts aus, aber reagiert
        HttpResponse historyDays = get("/api/v1/providers/123/mql5/history?days=7", null);
        assertEquals(200, historyDays.status);

        // Trades als JSON
        HttpResponse trades = get("/api/v1/providers/123/mql5/trades", null);
        assertEquals(200, trades.status);
        assertTrue(trades.body.contains("\"total\":2"));
        assertTrue(trades.body.contains("EURUSD"));
        assertTrue(trades.body.contains("Open Time"));

        // Trades mit q-Filter
        HttpResponse tradesFiltered = get("/api/v1/providers/123/mql5/trades?q=GBPUSD", null);
        assertTrue(tradesFiltered.body.contains("\"total\":1"));
        assertTrue(tradesFiltered.body.contains("GBPUSD"));

        // Trades als rohe CSV
        HttpResponse tradesCsv = get("/api/v1/providers/123/mql5/trades.csv", null);
        assertEquals(200, tradesCsv.status);
        assertTrue(tradesCsv.contentType.startsWith("text/csv"));
        assertTrue(tradesCsv.body.contains("Open Time;Symbol;Type;Volume"));

        // Metriken aus der Root-TXT
        HttpResponse metrics = get("/api/v1/providers/123/mql5/metrics", null);
        assertEquals(200, metrics.status);
        assertTrue(metrics.body.contains("\"Balance\":1234.56"));
        assertTrue(metrics.body.contains("\"2025.06\":1.5"));
        assertTrue(metrics.body.contains("\"2025.07\":-0.5"));
        assertTrue(metrics.body.contains("\"date\":\"2025.01.05\""));

        // Reports: Liste und Download
        HttpResponse reports = get("/api/v1/providers/123/mql5/reports", null);
        assertEquals(200, reports.status);
        assertTrue(reports.body.contains("testreport_123.pdf"));
        HttpResponse reportPdf = get("/api/v1/providers/123/mql5/reports/testreport_123.pdf", null);
        assertEquals(200, reportPdf.status);
        assertEquals("application/pdf", reportPdf.contentType);
        assertTrue(reportPdf.bodyBytes.length > 0);

        // Trades-Katalog und Download ueber Dateinamen
        HttpResponse catalog = get("/api/v1/trades?version=mql5", null);
        assertEquals(200, catalog.status);
        assertTrue(catalog.body.contains("Test_Provider_Alpha_123.csv"));
        assertTrue(catalog.body.contains("\"signalId\":\"123\""));
        HttpResponse catalogCsv = get("/api/v1/trades/file/mql5/Test_Provider_Alpha_123.csv", null);
        assertEquals(200, catalogCsv.status);
        assertTrue(catalogCsv.body.contains("EURUSD"));

        // Pfad-Traversierung wird abgewiesen
        HttpResponse traversal = get("/api/v1/trades/file/mql5/..%2F..%2Fconfig%2Fsubscribers.mv.db", null);
        assertEquals(404, traversal.status);

        // Events ueber alle Signale
        HttpResponse events = get("/api/v1/events", null);
        assertEquals(200, events.status);
        assertTrue(events.body.contains("\"change\":3"));
        assertFalse(events.body.contains("\"recordType\":\"BASELINE\""));

        // Zusammenfassung
        HttpResponse summary = get("/api/v1/summary", null);
        assertEquals(200, summary.status);
        assertTrue(summary.body.contains("\"total\":2"));
        assertTrue(summary.body.contains("\"mql4\":1"));
        assertTrue(summary.body.contains("\"mql5\":1"));
        assertTrue(summary.body.contains("topWeek"));

        // OpenAPI
        HttpResponse openApi = get("/api/v1/openapi.json", null);
        assertEquals(200, openApi.status);
        assertTrue(openApi.body.contains("\"openapi\":\"3.0.3\""));

        // Unbekannter Pfad
        assertEquals(404, get("/api/v1/gibtsnicht", null).status);

        // Falsche Parameter
        assertEquals(400, get("/api/v1/providers?minSubscribers=abc", null).status);
    }

    @Test
    void tokenProtectsAllEndpoints() throws Exception {
        config.setApiToken("geheim123");
        try {
            assertEquals(401, get("/api/v1/health", null).status);
            assertEquals(401, get("/api/v1/health", "falsch").status);
            HttpResponse withHeader = get("/api/v1/health", "geheim123");
            assertEquals(200, withHeader.status);
            HttpResponse withQuery = get("/api/v1/health?token=geheim123", null);
            assertEquals(200, withQuery.status);
        } finally {
            config.setApiToken("");
        }
    }

    // ------------------------------------------------------------------

    private static HttpResponse get(String path, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL("http://localhost:" + port + path).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        if (token != null) {
            conn.setRequestProperty("X-API-Token", token);
        }
        int status = conn.getResponseCode();
        String contentType = conn.getContentType() == null ? "" : conn.getContentType();
        InputStream stream = status < 400 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                }
            }
        }
        // UTF-8-BOM (z. B. bei CSV-Antworten fuer Excel) nicht mit pruuefen
        if (body.length() > 0 && body.charAt(0) == '\uFEFF') {
            body.deleteCharAt(0);
        }
        byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
        return new HttpResponse(status, contentType, body.toString(), raw);
    }

    private static final class HttpResponse {
        final int status;
        final String contentType;
        final String body;
        final byte[] bodyBytes;

        HttpResponse(int status, String contentType, String body, byte[] bodyBytes) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.bodyBytes = bodyBytes;
        }
    }
}
