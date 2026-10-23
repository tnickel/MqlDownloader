package rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.ConfigurationManager;
import database.DatabaseManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProviderRefreshEndpointTest {
    @TempDir Path dir;
    RestApiServer server; ConfigurationManager config; String base;
    boolean supported = true, busy; int targeted, whole;
    long selectedId; String selectedVersion; UpdateJob job;

    @BeforeEach void start() throws Exception {
        config = new ConfigurationManager(dir.toString()); config.initializeDirectories();
        try (ServerSocket socket = new ServerSocket(0)) { config.setApiPort(socket.getLocalPort()); }
        UpdateStarter starter = new UpdateStarter() {
            @Override public UpdateAntwort starte(Map<String,Object> body) {
                whole++; return UpdateAntwort.beschaeftigt("fixture");
            }
            @Override public boolean providerRefreshSupported() { return supported; }
            @Override public UpdateAntwort starteProvider(long id, String version) {
                if (busy) return UpdateAntwort.beschaeftigt("other job");
                targeted++; selectedId = id; selectedVersion = version;
                job = new UpdateJob("own-" + targeted); job.reservieren();
                return UpdateAntwort.gestartet(job);
            }
            @Override public UpdateJob letzterJob() { return job; }
        };
        server = new RestApiServer(new DatabaseManager(dir.toString()), config, starter); server.start();
        base = "http://127.0.0.1:" + config.getApiPort() + "/api/v1";
    }

    @AfterEach void stop() throws Exception {
        if (server != null) server.stop();
        try (Statement statement = DriverManager.getConnection("jdbc:h2:file:"
                + (dir + "/config/subscribers").replace('\\', '/'), "sa", "").createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private HttpURLConnection request(String path, boolean post, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setRequestMethod(post ? "POST" : "GET");
        if (token != null) connection.setRequestProperty("X-API-Token", token);
        if (post) { connection.setDoOutput(true); connection.getOutputStream().close(); }
        return connection;
    }
    private JsonObject json(HttpURLConnection connection) throws Exception {
        java.io.InputStream stream = connection.getResponseCode() >= 400
                ? connection.getErrorStream() : connection.getInputStream();
        try { return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        finally { stream.close(); connection.disconnect(); }
    }

    @Test void authenticatesAndStartsExactlyOneOwnJobWithExplicitPlatform() throws Exception {
        config.setApiToken("fixture-token");
        assertEquals(401, request("/providers/42/mql5/refresh", true, null).getResponseCode());
        assertEquals(0, targeted);
        HttpURLConnection response = request("/providers/42/mql4/refresh", true, "fixture-token");
        assertEquals(202, response.getResponseCode());
        assertEquals("own-1", json(response).get("jobId").getAsString());
        assertEquals(42, selectedId); assertEquals("mql4", selectedVersion);
        assertEquals(1, targeted); assertEquals(0, whole);
        assertEquals("own-1", json(request("/update/status", false, "fixture-token")).get("jobId").getAsString());
    }

    @Test void unsupportedCapabilityAndWrongPlatformCannotStartOrBorrowAJob() throws Exception {
        supported = false;
        assertFalse(json(request("/health", false, null)).get("providerRefresh").getAsBoolean());
        assertEquals(405, request("/providers/42/mql5/refresh", true, null).getResponseCode());
        supported = true;
        assertTrue(json(request("/health", false, null)).get("providerRefresh").getAsBoolean());
        assertEquals(400, request("/providers/42/pelican/refresh", true, null).getResponseCode());
        assertEquals(400, request("/providers/9999999999999999999999/mql5/refresh", true, null).getResponseCode());
        busy = true;
        assertEquals(409, request("/providers/42/mql5/refresh", true, null).getResponseCode());
        assertEquals(0, targeted); assertEquals(0, whole);
    }

    @Test void legacyStarterDoesNotAdvertiseTargetedRefresh() {
        UpdateStarter legacy = new UpdateStarter() {
            @Override public UpdateAntwort starte(Map<String,Object> body) { return null; }
            @Override public UpdateJob letzterJob() { return null; }
        };
        assertFalse(legacy.providerRefreshSupported());
        assertThrows(UnsupportedOperationException.class, () -> legacy.starteProvider(42, "mql5"));
    }
}
