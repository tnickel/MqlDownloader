package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseManagerTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void shutdownDatabase() throws Exception {
        Path databaseFile = tempDirectory.resolve("config").resolve("subscribers.mv.db");
        if (Files.exists(databaseFile)) {
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void keepsMql4AndMql5SignalsWithTheSameIdSeparate() {
        DatabaseManager manager = new DatabaseManager(tempDirectory.toString());

        assertEquals("[NEW] Vier (MQL4): 10 Abonnenten",
                manager.checkAndUpdateSubscribers("same-id", "MQL4", "Vier", 10, "https://example.test/4"));
        assertEquals("[NEW] Fuenf (MQL5): 20 Abonnenten",
                manager.checkAndUpdateSubscribers("same-id", "mql5", "Fuenf", 20, "https://example.test/5"));
        assertEquals("Vier (MQL4): +2",
                manager.checkAndUpdateSubscribers("same-id", "mql4", "Vier", 12, null));
        assertNull(manager.checkAndUpdateSubscribers("same-id", "MQL4", "Vier", 12, null));

        Map<String, SubscriberStat> byVersion = manager.getAllSubscriberStatistics().stream()
                .collect(Collectors.toMap(SubscriberStat::getMqlVersion, Function.identity()));
        assertEquals(2, byVersion.size());
        assertEquals(12, byVersion.get("mql4").getSubscribers());
        assertEquals(20, byVersion.get("mql5").getSubscribers());
        assertEquals("https://example.test/4", byVersion.get("mql4").getUrl());

        List<SubscriberHistoryPoint> mql4History = manager.getSubscriberHistory("same-id", "MQL4");
        List<SubscriberHistoryPoint> mql5History = manager.getSubscriberHistory("same-id", "mql5");
        assertEquals(3, mql4History.size());
        assertEquals(0, mql4History.get(0).getChangeAmount());
        assertEquals(2, mql4History.get(1).getChangeAmount());
        assertEquals(0, mql4History.get(2).getChangeAmount());
        assertEquals(1, mql5History.size());
        assertEquals(20, mql5History.get(0).getSubscribers());

        Map<String, List<SubscriberHistoryPoint>> allHistories = manager.getAllSubscriberHistories();
        assertEquals(3, allHistories.get(DatabaseManager.subscriberHistoryKey("same-id", "mql4")).size());
        assertEquals(1, allHistories.get(DatabaseManager.subscriberHistoryKey("same-id", "mql5")).size());
    }

    @Test
    void computesChangesFromSnapshotsAtRequestedAges() throws Exception {
        DatabaseManager manager = new DatabaseManager(tempDirectory.toString());
        manager.checkAndUpdateSubscribers("window", "mql5", "Window", 20, "https://example.test/window");

        Timestamp reference = Timestamp.from(Instant.now());
        try (Connection connection = openConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE signal_subscribers SET subscribers = ?, last_updated = ? " +
                    "WHERE signal_id = ? AND mql_version = ?")) {
                update.setInt(1, 20);
                update.setTimestamp(2, reference);
                update.setString(3, "window");
                update.setString(4, "mql5");
                update.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM subscriber_history WHERE signal_id = ? AND mql_version = ?")) {
                delete.setString(1, "window");
                delete.setString(2, "mql5");
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO subscriber_history " +
                    "(signal_id, mql_version, signal_name, subscribers, change_amount, record_type, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                insertSnapshot(insert, "window", 5, 0, reference, 30);
                insertSnapshot(insert, "window", 10, 0, reference, 7);
                insertSnapshot(insert, "window", 20, 0, reference, 0);
            }
        }

        SubscriberStat stat = manager.getAllSubscriberStatistics().stream()
                .filter(candidate -> "window".equals(candidate.getSignalId()))
                .findFirst().orElseThrow(() -> new AssertionError("window provider missing"));
        assertEquals(10, stat.getWeekChange());
        assertEquals(15, stat.getMonthChange());
    }

    @Test
    void leavesUnavailableTimeWindowsUndefined() {
        DatabaseManager manager = new DatabaseManager(tempDirectory.toString());
        manager.checkAndUpdateSubscribers("recent", "mql5", "Recent", 7, null);

        SubscriberStat stat = manager.getAllSubscriberStatistics().stream()
                .filter(candidate -> "recent".equals(candidate.getSignalId()))
                .findFirst().orElseThrow(() -> new AssertionError("recent provider missing"));
        assertNull(stat.getWeekChange());
        assertNull(stat.getMonthChange());
    }

    private void insertSnapshot(PreparedStatement insert, String signalId, int subscribers,
                                int changeAmount, Timestamp reference, int ageDays) throws Exception {
        insert.setString(1, signalId);
        insert.setString(2, "mql5");
        insert.setString(3, "Window");
        insert.setInt(4, subscribers);
        insert.setInt(5, changeAmount);
        insert.setString(6, "EVENT");
        insert.setTimestamp(7, Timestamp.from(reference.toInstant().minusSeconds(ageDays * 86400L)));
        insert.executeUpdate();
    }

    @Test
    void migratesLegacyPrimaryKeyAndRecoversVersionFromHistory() throws Exception {
        Files.createDirectories(tempDirectory.resolve("config"));
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE signal_subscribers (" +
                    "signal_id VARCHAR(100) PRIMARY KEY, mql_version VARCHAR(10), " +
                    "signal_name VARCHAR(200), subscribers INT, last_updated TIMESTAMP)");
            statement.execute("CREATE TABLE subscriber_history (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, signal_id VARCHAR(100), " +
                    "mql_version VARCHAR(10), signal_name VARCHAR(200), subscribers INT, " +
                    "change_amount INT, timestamp TIMESTAMP)");
            statement.executeUpdate("INSERT INTO signal_subscribers VALUES " +
                    "('same-id', 'MQL4', 'Vier', 10, CURRENT_TIMESTAMP())");
            statement.executeUpdate("INSERT INTO subscriber_history " +
                    "(signal_id, mql_version, signal_name, subscribers, change_amount, timestamp) VALUES " +
                    "('same-id', 'MQL4', 'Vier', 10, 10, CURRENT_TIMESTAMP()), " +
                    "('same-id', 'MQL5', 'Fuenf', 25, 25, CURRENT_TIMESTAMP())");
        }

        DatabaseManager manager = new DatabaseManager(tempDirectory.toString());
        Map<String, SubscriberStat> byVersion = manager.getAllSubscriberStatistics().stream()
                .collect(Collectors.toMap(SubscriberStat::getMqlVersion, Function.identity()));

        assertEquals(2, byVersion.size());
        assertEquals(10, byVersion.get("mql4").getSubscribers());
        assertEquals(25, byVersion.get("mql5").getSubscribers());

        List<String> primaryKeyColumns = new ArrayList<>();
        try (Connection connection = openConnection();
             ResultSet result = connection.getMetaData().getPrimaryKeys(null, null, "SIGNAL_SUBSCRIBERS")) {
            while (result.next()) {
                int sequence = result.getShort("KEY_SEQ");
                while (primaryKeyColumns.size() < sequence) {
                    primaryKeyColumns.add(null);
                }
                primaryKeyColumns.set(sequence - 1, result.getString("COLUMN_NAME"));
            }
        }

        assertEquals(2, primaryKeyColumns.size());
        assertEquals("SIGNAL_ID", primaryKeyColumns.get(0));
        assertEquals("MQL_VERSION", primaryKeyColumns.get(1));
        assertNotNull(manager.getSubscriberHistory("same-id", "mql5"));
        assertTrue(manager.getSubscriberHistory("same-id", "mql5").size() >= 1);
    }

    private String databaseUrl() {
        String path = tempDirectory.resolve("config").resolve("subscribers")
                .toString().replace('\\', '/');
        return "jdbc:h2:file:" + path + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(databaseUrl(), "sa", "");
    }
}
