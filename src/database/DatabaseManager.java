package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseManager {
    private static final Logger logger = LogManager.getLogger(DatabaseManager.class);
    private final String dbUrl;

    public DatabaseManager(String rootDirPath) {
        // H2 database path: rootDirPath/config/subscribers
        String dbPath = rootDirPath + "/config/subscribers";
        // Clean backslashes for JDBC url
        dbPath = dbPath.replace("\\", "/");
        this.dbUrl = "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, "sa", "");
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            logger.error("H2 Driver not found in classpath!", e);
            return;
        }

        String createTableSql = "CREATE TABLE IF NOT EXISTS signal_subscribers (" +
                                "signal_id VARCHAR(100) PRIMARY KEY, " +
                                "mql_version VARCHAR(10), " +
                                "signal_name VARCHAR(200), " +
                                "subscribers INT, " +
                                "last_updated TIMESTAMP" +
                                ")";

        String alterTableSql = "ALTER TABLE signal_subscribers ADD COLUMN IF NOT EXISTS url VARCHAR(500)";

        String createHistoryTableSql = "CREATE TABLE IF NOT EXISTS subscriber_history (" +
                                       "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                                       "signal_id VARCHAR(100), " +
                                       "mql_version VARCHAR(10), " +
                                       "signal_name VARCHAR(200), " +
                                       "subscribers INT, " +
                                       "change_amount INT, " +
                                       "timestamp TIMESTAMP" +
                                       ")";

        String createIndexSql = "CREATE INDEX IF NOT EXISTS idx_sub_hist_signal ON subscriber_history(signal_id, timestamp)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(alterTableSql);
            stmt.execute(createHistoryTableSql);
            stmt.execute(createIndexSql);
            logger.info("Database initialized successfully at URL: " + dbUrl);
        } catch (SQLException e) {
            logger.error("Error initializing database", e);
        }
    }

    public synchronized String checkAndUpdateSubscribers(String signalId, String mqlVersion, String name, int currentSubscribers) {
        return checkAndUpdateSubscribers(signalId, mqlVersion, name, currentSubscribers, null);
    }

    public synchronized String checkAndUpdateSubscribers(String signalId, String mqlVersion, String name, int currentSubscribers, String url) {
        String selectSql = "SELECT subscribers FROM signal_subscribers WHERE signal_id = ?";
        String insertSql = "INSERT INTO signal_subscribers (signal_id, mql_version, signal_name, subscribers, last_updated, url) VALUES (?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE signal_subscribers SET subscribers = ?, signal_name = ?, last_updated = ?, url = COALESCE(?, url) WHERE signal_id = ?";
        String insertHistorySql = "INSERT INTO subscriber_history (signal_id, mql_version, signal_name, subscribers, change_amount, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (Connection conn = getConnection()) {
            // Check if signal exists
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, signalId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int storedSubscribers = rs.getInt("subscribers");
                        if (currentSubscribers != storedSubscribers) {
                            int diff = currentSubscribers - storedSubscribers;
                            // Update database
                            try (PreparedStatement updStmt = conn.prepareStatement(updateSql)) {
                                updStmt.setInt(1, currentSubscribers);
                                updStmt.setString(2, name);
                                updStmt.setTimestamp(3, now);
                                updStmt.setString(4, url);
                                updStmt.setString(5, signalId);
                                updStmt.executeUpdate();
                            }
                            // Insert history record
                            try (PreparedStatement histStmt = conn.prepareStatement(insertHistorySql)) {
                                histStmt.setString(1, signalId);
                                histStmt.setString(2, mqlVersion);
                                histStmt.setString(3, name);
                                histStmt.setInt(4, currentSubscribers);
                                histStmt.setInt(5, diff);
                                histStmt.setTimestamp(6, now);
                                histStmt.executeUpdate();
                            }
                            String sign = diff > 0 ? "+" : "";
                            return name + " (" + mqlVersion.toUpperCase() + "): " + sign + diff;
                        }
                        return null; // No change
                    }
                }
            }

            // If it doesn't exist, insert it
            try (PreparedStatement instStmt = conn.prepareStatement(insertSql)) {
                instStmt.setString(1, signalId);
                instStmt.setString(2, mqlVersion);
                instStmt.setString(3, name);
                instStmt.setInt(4, currentSubscribers);
                instStmt.setTimestamp(5, now);
                instStmt.setString(6, url);
                instStmt.executeUpdate();
            }

            // Also record initial history entry
            try (PreparedStatement histStmt = conn.prepareStatement(insertHistorySql)) {
                histStmt.setString(1, signalId);
                histStmt.setString(2, mqlVersion);
                histStmt.setString(3, name);
                histStmt.setInt(4, currentSubscribers);
                histStmt.setInt(5, currentSubscribers);
                histStmt.setTimestamp(6, now);
                histStmt.executeUpdate();
            }

            if (currentSubscribers > 0) {
                return "[NEW] " + name + " (" + mqlVersion.toUpperCase() + "): +" + currentSubscribers;
            }
            return null; // Don't show new signals with 0 subscribers to avoid clutter
        } catch (SQLException e) {
            logger.error("Error accessing database for signal " + signalId, e);
            return null;
        }
    }

    public synchronized List<SubscriberStat> getAllSubscriberStatistics() {
        List<SubscriberStat> stats = new ArrayList<>();
        String sql = "SELECT s.signal_id, s.mql_version, s.signal_name, s.subscribers, s.last_updated, s.url, " +
                     "COALESCE((SELECT h.change_amount FROM subscriber_history h WHERE h.signal_id = s.signal_id ORDER BY h.timestamp DESC, h.id DESC LIMIT 1), 0) AS latest_change, " +
                     "COALESCE((SELECT SUM(h.change_amount) FROM subscriber_history h WHERE h.signal_id = s.signal_id AND h.timestamp >= DATEADD('DAY', -7, CURRENT_TIMESTAMP())), 0) AS week_change, " +
                     "COALESCE((SELECT SUM(h.change_amount) FROM subscriber_history h WHERE h.signal_id = s.signal_id AND h.timestamp >= DATEADD('DAY', -30, CURRENT_TIMESTAMP())), 0) AS month_change " +
                     "FROM signal_subscribers s " +
                     "ORDER BY month_change DESC, week_change DESC, s.subscribers DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String signalId = rs.getString("signal_id");
                String mqlVersion = rs.getString("mql_version");
                String name = rs.getString("signal_name");
                int subscribers = rs.getInt("subscribers");
                Timestamp lastUpdated = rs.getTimestamp("last_updated");
                String url = rs.getString("url");
                int latestChange = rs.getInt("latest_change");
                int weekChange = rs.getInt("week_change");
                int monthChange = rs.getInt("month_change");

                stats.add(new SubscriberStat(signalId, mqlVersion, name, subscribers, latestChange, weekChange, monthChange, lastUpdated, url));
            }
        } catch (SQLException e) {
            logger.error("Error fetching subscriber statistics", e);
        }
        return stats;
    }

    public synchronized List<SubscriberHistoryPoint> getSubscriberHistory(String signalId) {
        List<SubscriberHistoryPoint> history = new ArrayList<>();
        String sql = "SELECT timestamp, subscribers, change_amount FROM subscriber_history WHERE signal_id = ? ORDER BY timestamp ASC, id ASC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, signalId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp timestamp = rs.getTimestamp("timestamp");
                    int subscribers = rs.getInt("subscribers");
                    int changeAmount = rs.getInt("change_amount");

                    history.add(new SubscriberHistoryPoint(timestamp, subscribers, changeAmount));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching subscriber history for signal " + signalId, e);
        }
        return history;
    }
}
