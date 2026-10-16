package rest;

import config.ConfigurationManager;
import database.DatabaseManager;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SubscriberDefaultTest {
    @TempDir Path dir;

    @Test void catalogDefaultsToSubscribersButExplicitArchiveAndDetailsRemainAccessible() throws Exception {
        ConfigurationManager config = new ConfigurationManager(dir.toString());
        config.initializeDirectories();
        DatabaseManager database = new DatabaseManager(dir.toString());
        database.checkAndUpdateSubscribers("900", "mql5", "Zero subscriber", 0, null);
        database.checkAndUpdateSubscribers("901", "mql5", "One subscriber", 1, null);
        try (ServerSocket socket = new ServerSocket(0)) { config.setApiPort(socket.getLocalPort()); }
        RestApiServer server = new RestApiServer(database, config);
        server.start();
        try {
            String base = "http://localhost:" + config.getApiPort() + "/api/v1";
            String json = get(base + "/providers");
            assertTrue(json.contains("\"total\":1"));
            assertTrue(json.contains("\"signalId\":\"901\""));
            assertFalse(json.contains("\"signalId\":\"900\""));
            String csv = get(base + "/providers?format=csv");
            assertTrue(csv.contains("One subscriber"));
            assertFalse(csv.contains("Zero subscriber"));
            assertTrue(get(base + "/providers?minSubscribers=0").contains("\"total\":2"));
            assertTrue(get(base + "/providers?minSubscribers=").contains("\"total\":1"));
            assertTrue(get(base + "/providers?minSubscribers=2147483648").contains("\"total\":0"));
            assertTrue(get(base + "/providers/900").contains("Zero subscriber"));
        } finally {
            server.stop();
            String dbPath = (dir + "/config/subscribers").replace("\\", "/");
            try (java.sql.Connection connection = DriverManager.getConnection("jdbc:h2:file:" + dbPath, "sa", "");
                 java.sql.Statement statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        try {
            assertEquals(200, connection.getResponseCode());
            try (java.io.InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
