package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            logger.info("Database initialized successfully at URL: " + dbUrl);
        } catch (SQLException e) {
            logger.error("Error initializing database", e);
        }
    }

    public synchronized String checkAndUpdateSubscribers(String signalId, String mqlVersion, String name, int currentSubscribers) {
        String selectSql = "SELECT subscribers FROM signal_subscribers WHERE signal_id = ?";
        String insertSql = "INSERT INTO signal_subscribers (signal_id, mql_version, signal_name, subscribers, last_updated) VALUES (?, ?, ?, ?, ?)";
        String updateSql = "UPDATE signal_subscribers SET subscribers = ?, signal_name = ?, last_updated = ? WHERE signal_id = ?";

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
                                updStmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                                updStmt.setString(4, signalId);
                                updStmt.executeUpdate();
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
                instStmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                instStmt.executeUpdate();
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
}
