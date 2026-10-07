package rest;

import config.ConfigurationManager;
import database.DatabaseManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stufe 0 „Clients aktualisieren" (SignalKiScanner doc/23): die beiden neuen
 * Endpunkte POST /api/v1/update und GET /api/v1/update/status — mit einem
 * Fake-Starter (die echte Kaskade MQL4→MQL5→Konvertierung ist Selenium- und
 * Netz-abhängig und gehört nicht in den Unit-Test).
 */
class UpdateEndpointTest {
    @TempDir
    static Path tempDir;

    static RestApiServer server;
    static int port;
    static final AtomicInteger starts = new AtomicInteger();
    static final AtomicReference<Map<String, Object>> letzterBody = new AtomicReference<>();
    static UpdateJob fakeJob;

    @BeforeAll
    static void setUp() throws Exception {
        ConfigurationManager config = new ConfigurationManager(tempDir.toString());
        config.initializeDirectories();
        new DatabaseManager(tempDir.toString());
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        config.setApiPort(port);
        UpdateStarter starter = new UpdateStarter() {
            @Override
            public UpdateAntwort starte(Map<String, Object> body) {
                letzterBody.set(body);
                if (fakeJob != null && fakeJob.laeuft()) {
                    return UpdateAntwort.laufend(fakeJob);
                }
                starts.incrementAndGet();
                fakeJob = new UpdateJob("u-rest-1");
                fakeJob.melde("mql4", 0, 0, "Testlauf");
                return UpdateAntwort.gestartet(fakeJob);
            }

            @Override
            public UpdateJob letzterJob() {
                return fakeJob;
            }
        };
        server = new RestApiServer(new DatabaseManager(tempDir.toString()), config, starter);
        server.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        String dbPath = (tempDir + "/config/subscribers").replace("\\", "/");
        try (Statement stmt = DriverManager.getConnection("jdbc:h2:file:" + dbPath, "sa", "")
                .createStatement()) {
            stmt.execute("SHUTDOWN");
        } catch (Exception ignored) {
            // DB evtl. nie geöffnet worden — TempDir-Aufräumen trotzdem ok
        }
    }

    private static HttpURLConnection verbindung(String pfad, String methode) throws Exception {
        URL url = new URL("http://localhost:" + port + "/api/v1" + pfad);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(methode);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    private static String lese(HttpURLConnection conn) throws Exception {
        InputStream in = conn.getResponseCode() >= 400
                ? conn.getErrorStream() : conn.getInputStream();
        byte[] puffer = in.readAllBytes();
        return new String(puffer == null ? new byte[0] : puffer, StandardCharsets.UTF_8);
    }

    private static int post(String pfad, String body) throws Exception {
        HttpURLConnection conn = verbindung(pfad, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        lese(conn);
        return code;
    }

    private static String get(String pfad) throws Exception {
        HttpURLConnection conn = verbindung(pfad, "GET");
        int code = conn.getResponseCode();
        String text = lese(conn);
        assertEquals(200, code, "GET " + pfad + " → 200, Body: " + text);
        return text;
    }

    /** Vor jedem Start-Test den vorherigen Fake-Job abschließen, sonst
     *  antwortet der Starter „läuft bereits“ (200) statt 202. */
    private static void beendeFakeJob() {
        if (fakeJob != null && fakeJob.laeuft()) {
            fakeJob.fertig("test");
        }
    }

    @Test
    void postUpdateStartetUndUebergibtParameter() throws Exception {
        beendeFakeJob();
        int vorher = starts.get();
        assertEquals(202, post("/update",
                "{\"target\": 200, \"tradelisten\": true, \"katalogMaxAlterH\": 72}"));
        assertEquals(vorher + 1, starts.get());
        Map<String, Object> body = letzterBody.get();
        assertEquals(200, ((Number) body.get("target")).intValue());
        assertEquals(72, ((Number) body.get("katalogMaxAlterH")).intValue());
        assertEquals(Boolean.TRUE, body.get("tradelisten"));
    }

    @Test
    void postLeererBodyNutztDefaults() throws Exception {
        beendeFakeJob();
        assertEquals(202, post("/update", ""));
        Map<String, Object> body = letzterBody.get();
        // Keine Werte im Body → Starter erhält eine leere Map (Defaults gelten GUI-seitig)
        assertTrue(body.isEmpty() || body.get("target") != null);
    }

    @Test
    void postAnderePfadeBleiben405() throws Exception {
        assertEquals(405, post("/providers", "{}"));
        assertEquals(405, post("/health", "{}"));
        assertEquals(405, post("/summary", "{}"));
    }

    @Test
    void getStatusLiefertJobZustand() throws Exception {
        post("/update", "{}");
        String status = get("/update/status");
        assertTrue(status.contains("\"running\""), status);
        assertTrue(status.contains("mql4"), status);
        assertTrue(status.contains("u-rest-1"), status);
    }

    @Test
    void updateJobErgebnisUndFehlerZustaende() {
        UpdateJob job = new UpdateJob("u-x");
        assertEquals("idle", job.statusJson().get("state"));
        job.melde("mql5", 3, 10, "läuft");
        assertEquals("running", job.statusJson().get("state"));
        assertEquals(3, job.statusJson().get("done"));
        job.ergebnis("signaleGeliefert", 50);
        job.hinweis("Test");
        job.fertig("2026-10-07T16:00:00");
        assertEquals("done", job.statusJson().get("state"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ergebnis = (Map<String, Object>) job.statusJson().get("ergebnis");
        assertEquals(50, ergebnis.get("signaleGeliefert"));
        assertEquals("2026-10-07T16:00:00", ergebnis.get("datenstand"));

        UpdateJob fehlerfall = new UpdateJob("u-y");
        fehlerfall.melde("mql4", 0, 0, "start");
        fehlerfall.fehler("Selenium-Login fehlgeschlagen");
        assertEquals("error", fehlerfall.statusJson().get("state"));
        assertTrue(((String) fehlerfall.statusJson().get("error")).contains("Selenium"));
    }
}
