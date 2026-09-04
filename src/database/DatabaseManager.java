package database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseManager {
    private static final Logger logger = LogManager.getLogger(DatabaseManager.class);
    private static final String LEGACY_MQL_VERSION = "__legacy__";
    private static final String HISTORY_TYPE_BASELINE = "BASELINE";
    private static final String HISTORY_TYPE_EVENT = "EVENT";
    private final String dbUrl;

    public DatabaseManager(String rootDirPath) {
        String dbPath = rootDirPath + "/config/subscribers";
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
                                "signal_id VARCHAR(100) NOT NULL, " +
                                "mql_version VARCHAR(10) NOT NULL, " +
                                "signal_name VARCHAR(200), " +
                                "subscribers INT, " +
                                "last_updated TIMESTAMP, " +
                                "url VARCHAR(500), " +
                                "PRIMARY KEY (signal_id, mql_version)" +
                                ")";
        String alterTableSql = "ALTER TABLE signal_subscribers ADD COLUMN IF NOT EXISTS url VARCHAR(500)";
        String createHistoryTableSql = "CREATE TABLE IF NOT EXISTS subscriber_history (" +
                                       "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                                       "signal_id VARCHAR(100) NOT NULL, " +
                                       "mql_version VARCHAR(10) NOT NULL, " +
                                       "signal_name VARCHAR(200), " +
                                       "subscribers INT, " +
                                       "change_amount INT, " +
                                       "record_type VARCHAR(10) NOT NULL DEFAULT 'EVENT', " +
                                       "timestamp TIMESTAMP" +
                                       ")";
        String alterHistoryTableSql = "ALTER TABLE subscriber_history " +
                                      "ADD COLUMN IF NOT EXISTS record_type VARCHAR(10) " +
                                      "NOT NULL DEFAULT 'EVENT'";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(alterTableSql);
            stmt.execute(createHistoryTableSql);
            stmt.execute(alterHistoryTableSql);
            migrateSignalIdentity(conn);
            migrateHistoryRecordTypes(conn);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sub_hist_signal_version " +
                         "ON subscriber_history(signal_id, mql_version, timestamp)");
            logger.info("Database initialized successfully at URL: " + dbUrl);
        } catch (SQLException e) {
            logger.error("Error initializing database", e);
        }
    }

    /**
     * Labels history rows explicitly. Before record_type existed, the first row
     * for a newly discovered provider was written with change_amount equal to
     * the complete subscriber count. Such a row is recognizable as a baseline
     * only when it is the first row of that exact signal/version identity and
     * the stored change equals the complete subscriber count (including zero).
     * Other legacy rows are deliberately left
     * as events because their original meaning cannot be established safely.
     */
    private void migrateHistoryRecordTypes(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE subscriber_history SET record_type = '" +
                               HISTORY_TYPE_EVENT + "' " +
                               "WHERE record_type IS NULL OR TRIM(record_type) = ''");
            stmt.executeUpdate("UPDATE subscriber_history h SET " +
                               "record_type = '" + HISTORY_TYPE_BASELINE + "', change_amount = 0 " +
                               "WHERE h.record_type = '" + HISTORY_TYPE_EVENT + "' " +
                               "AND h.change_amount = h.subscribers " +
                               "AND h.id = (SELECT MIN(h2.id) FROM subscriber_history h2 " +
                               "WHERE h2.signal_id = h.signal_id " +
                               "AND h2.mql_version = h.mql_version)");
        }
    }

    /**
     * Migrates databases created before signal_id and mql_version formed a
     * composite identity. DDL in H2 commits implicitly, so every statement is
     * deliberately idempotent: a restart can safely finish an interrupted migration.
     */
    private void migrateSignalIdentity(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE signal_subscribers SET mql_version = LOWER(TRIM(mql_version)) " +
                               "WHERE mql_version IS NOT NULL AND TRIM(mql_version) <> ''");
            stmt.executeUpdate("UPDATE signal_subscribers SET mql_version = '" + LEGACY_MQL_VERSION + "' " +
                               "WHERE mql_version IS NULL OR TRIM(mql_version) = ''");
            stmt.execute("ALTER TABLE signal_subscribers ALTER COLUMN mql_version SET NOT NULL");

            if (!hasCompositeSignalPrimaryKey(conn)) {
                if (hasPrimaryKey(conn, "SIGNAL_SUBSCRIBERS")) {
                    stmt.execute("ALTER TABLE signal_subscribers DROP PRIMARY KEY");
                }
                stmt.execute("ALTER TABLE signal_subscribers ADD CONSTRAINT pk_signal_subscribers " +
                             "PRIMARY KEY (signal_id, mql_version)");
            }

            stmt.executeUpdate("UPDATE subscriber_history h SET mql_version = (" +
                               "SELECT MIN(s.mql_version) FROM signal_subscribers s " +
                               "WHERE s.signal_id = h.signal_id) " +
                               "WHERE (h.mql_version IS NULL OR TRIM(h.mql_version) = '') " +
                               "AND (SELECT COUNT(*) FROM signal_subscribers s " +
                               "WHERE s.signal_id = h.signal_id) = 1");
            stmt.executeUpdate("UPDATE subscriber_history SET mql_version = LOWER(TRIM(mql_version)) " +
                               "WHERE mql_version IS NOT NULL AND TRIM(mql_version) <> ''");
            stmt.executeUpdate("UPDATE subscriber_history SET mql_version = '" + LEGACY_MQL_VERSION + "' " +
                               "WHERE mql_version IS NULL OR TRIM(mql_version) = ''");
            stmt.execute("ALTER TABLE subscriber_history ALTER COLUMN mql_version SET NOT NULL");

            // The old key allowed an update for MQL5 to overwrite the MQL4 snapshot (or vice versa).
            // Restore every known identity from its own latest history row before adding hidden versions.
            String latestHistoryId = "SELECT MAX(h2.id) FROM subscriber_history h2 " +
                                     "WHERE h2.signal_id = s.signal_id " +
                                     "AND h2.mql_version = s.mql_version";
            stmt.executeUpdate("UPDATE signal_subscribers s SET " +
                               "signal_name = COALESCE((SELECT h.signal_name FROM subscriber_history h " +
                               "WHERE h.id = (" + latestHistoryId + ")), s.signal_name), " +
                               "subscribers = (SELECT h.subscribers FROM subscriber_history h " +
                               "WHERE h.id = (" + latestHistoryId + ")), " +
                               "last_updated = (SELECT h.timestamp FROM subscriber_history h " +
                               "WHERE h.id = (" + latestHistoryId + ")) " +
                               "WHERE EXISTS (SELECT 1 FROM subscriber_history h " +
                               "WHERE h.id = (" + latestHistoryId + "))");

            stmt.executeUpdate("INSERT INTO signal_subscribers " +
                               "(signal_id, mql_version, signal_name, subscribers, last_updated, url) " +
                               "SELECT h.signal_id, h.mql_version, h.signal_name, h.subscribers, h.timestamp, NULL " +
                               "FROM subscriber_history h " +
                               "WHERE h.id = (SELECT MAX(h2.id) FROM subscriber_history h2 " +
                               "WHERE h2.signal_id = h.signal_id AND h2.mql_version = h.mql_version) " +
                               "AND NOT EXISTS (SELECT 1 FROM signal_subscribers s " +
                               "WHERE s.signal_id = h.signal_id AND s.mql_version = h.mql_version)");
        }
    }

    private boolean hasCompositeSignalPrimaryKey(Connection conn) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet rs = metadata.getPrimaryKeys(null, null, "SIGNAL_SUBSCRIBERS")) {
            while (rs.next()) {
                int sequence = rs.getShort("KEY_SEQ");
                while (columns.size() < sequence) {
                    columns.add(null);
                }
                columns.set(sequence - 1, rs.getString("COLUMN_NAME"));
            }
        }
        return columns.size() == 2
                && "SIGNAL_ID".equalsIgnoreCase(columns.get(0))
                && "MQL_VERSION".equalsIgnoreCase(columns.get(1));
    }

    private boolean hasPrimaryKey(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, tableName)) {
            return rs.next();
        }
    }

    public synchronized String checkAndUpdateSubscribers(String signalId, String mqlVersion, String name, int currentSubscribers) {
        return checkAndUpdateSubscribers(signalId, mqlVersion, name, currentSubscribers, null);
    }

    public synchronized String checkAndUpdateSubscribers(String signalId, String mqlVersion, String name, int currentSubscribers, String url) {
        String normalizedSignalId;
        String normalizedMqlVersion;
        try {
            normalizedSignalId = requireIdentityPart(signalId, "signalId");
            normalizedMqlVersion = normalizeMqlVersion(mqlVersion);
        } catch (IllegalArgumentException e) {
            logger.error("Cannot update subscribers: " + e.getMessage());
            return null;
        }

        String selectSql = "SELECT subscribers FROM signal_subscribers WHERE signal_id = ? AND mql_version = ?";
        String insertSql = "INSERT INTO signal_subscribers (signal_id, mql_version, signal_name, subscribers, last_updated, url) VALUES (?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE signal_subscribers SET subscribers = ?, signal_name = ?, last_updated = ?, url = COALESCE(?, url) WHERE signal_id = ? AND mql_version = ?";
        String insertHistorySql = "INSERT INTO subscriber_history " +
                                  "(signal_id, mql_version, signal_name, subscribers, " +
                                  "change_amount, record_type, timestamp) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                    pstmt.setString(1, normalizedSignalId);
                    pstmt.setString(2, normalizedMqlVersion);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int storedSubscribers = rs.getInt("subscribers");
                            int diff = currentSubscribers - storedSubscribers;
                            try (PreparedStatement updStmt = conn.prepareStatement(updateSql)) {
                                updStmt.setInt(1, currentSubscribers);
                                updStmt.setString(2, name);
                                updStmt.setTimestamp(3, now);
                                updStmt.setString(4, url);
                                updStmt.setString(5, normalizedSignalId);
                                updStmt.setString(6, normalizedMqlVersion);
                                updStmt.executeUpdate();
                            }
                            insertHistory(conn, insertHistorySql, normalizedSignalId,
                                          normalizedMqlVersion, name, currentSubscribers, diff,
                                          HISTORY_TYPE_EVENT, now);
                            conn.commit();
                            if (diff != 0) {
                                String sign = diff > 0 ? "+" : "";
                                return name + " (" + normalizedMqlVersion.toUpperCase(Locale.ROOT) + "): " + sign + diff;
                            }
                            return null;
                        }
                    }
                }

                try (PreparedStatement instStmt = conn.prepareStatement(insertSql)) {
                    instStmt.setString(1, normalizedSignalId);
                    instStmt.setString(2, normalizedMqlVersion);
                    instStmt.setString(3, name);
                    instStmt.setInt(4, currentSubscribers);
                    instStmt.setTimestamp(5, now);
                    instStmt.setString(6, url);
                    instStmt.executeUpdate();
                }
                insertHistory(conn, insertHistorySql, normalizedSignalId,
                              normalizedMqlVersion, name, currentSubscribers, 0,
                              HISTORY_TYPE_BASELINE, now);
                conn.commit();

                if (currentSubscribers > 0) {
                    return "[NEW] " + name + " (" + normalizedMqlVersion.toUpperCase(Locale.ROOT) + "): " + currentSubscribers + " Abonnenten";
                }
                return null;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Error accessing database for signal " + normalizedSignalId +
                         " (" + normalizedMqlVersion + ")", e);
            return null;
        }
    }

    private void insertHistory(Connection conn, String sql, String signalId, String mqlVersion,
                               String name, int subscribers, int changeAmount, String recordType,
                               Timestamp timestamp)
            throws SQLException {
        try (PreparedStatement histStmt = conn.prepareStatement(sql)) {
            histStmt.setString(1, signalId);
            histStmt.setString(2, mqlVersion);
            histStmt.setString(3, name);
            histStmt.setInt(4, subscribers);
            histStmt.setInt(5, changeAmount);
            histStmt.setString(6, recordType);
            histStmt.setTimestamp(7, timestamp);
            histStmt.executeUpdate();
        }
    }

    public synchronized List<SubscriberStat> getAllSubscriberStatistics() {
        List<SubscriberStat> stats = new ArrayList<>();
        // A weekly comparison accepts at most two days of scheduler/manual-run drift.
        // For a 30-day value, four days are required because the normal weekly scan
        // cadence can place the closest observation 3.5 days from the target.
        String weekComparison = comparisonSnapshotSql(7, 2);
        String monthComparison = comparisonSnapshotSql(30, 4);
        String sql = "SELECT s.signal_id, s.mql_version, s.signal_name, s.subscribers, s.last_updated, s.url, " +
                     "COALESCE((SELECT h.change_amount FROM subscriber_history h WHERE h.signal_id = s.signal_id AND h.mql_version = s.mql_version ORDER BY h.timestamp DESC, h.id DESC LIMIT 1), 0) AS latest_change, " +
                     "s.subscribers - (" + weekComparison + ") AS week_change, " +
                     "s.subscribers - (" + monthComparison + ") AS month_change " +
                     "FROM signal_subscribers s " +
                     "ORDER BY month_change DESC NULLS LAST, week_change DESC NULLS LAST, " +
                     "s.subscribers DESC";

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
                Number weekValue = (Number) rs.getObject("week_change");
                Number monthValue = (Number) rs.getObject("month_change");
                Integer weekChange = weekValue == null ? null : weekValue.intValue();
                Integer monthChange = monthValue == null ? null : monthValue.intValue();
                stats.add(new SubscriberStat(signalId, mqlVersion, name, subscribers,
                                              latestChange, weekChange, monthChange, lastUpdated, url));
            }
        } catch (SQLException e) {
            logger.error("Error fetching subscriber statistics", e);
        }
        return stats;
    }

    /**
     * Returns a scalar correlated subquery selecting the observation closest to
     * the requested age. If no observation falls inside the tolerance window,
     * the scalar subquery yields SQL NULL instead of inventing a zero change.
     */
    private String comparisonSnapshotSql(int ageDays, int toleranceDays) {
        int oldestDays = ageDays + toleranceDays;
        int newestDays = ageDays - toleranceDays;
        return "SELECT h.subscribers FROM subscriber_history h " +
               "WHERE h.signal_id = s.signal_id " +
               "AND h.mql_version = s.mql_version " +
               "AND h.timestamp BETWEEN DATEADD('DAY', -" + oldestDays + ", s.last_updated) " +
               "AND DATEADD('DAY', -" + newestDays + ", s.last_updated) " +
               "ORDER BY ABS(DATEDIFF('SECOND', h.timestamp, " +
               "DATEADD('DAY', -" + ageDays + ", s.last_updated))), " +
               "h.timestamp DESC, h.id DESC LIMIT 1";
    }

    public synchronized List<SubscriberHistoryPoint> getSubscriberHistory(String signalId, String mqlVersion) {
        List<SubscriberHistoryPoint> history = new ArrayList<>();
        String normalizedSignalId;
        String normalizedMqlVersion;
        try {
            normalizedSignalId = requireIdentityPart(signalId, "signalId");
            normalizedMqlVersion = normalizeMqlVersion(mqlVersion);
        } catch (IllegalArgumentException e) {
            logger.error("Cannot load subscriber history: " + e.getMessage());
            return history;
        }

        String sql = "SELECT timestamp, subscribers, change_amount FROM subscriber_history " +
                     "WHERE signal_id = ? AND mql_version = ? ORDER BY timestamp ASC, id ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedSignalId);
            stmt.setString(2, normalizedMqlVersion);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new SubscriberHistoryPoint(rs.getTimestamp("timestamp"),
                                                           rs.getInt("subscribers"),
                                                           rs.getInt("change_amount")));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching subscriber history for signal " + normalizedSignalId +
                         " (" + normalizedMqlVersion + ")", e);
        }
        return history;
    }

    /**
     * Loads all subscriber history points in one query, grouped by signal identity.
     * Map keys are {@link #subscriberHistoryKey(String, String)}.
     */
    public synchronized Map<String, List<SubscriberHistoryPoint>> getAllSubscriberHistories() {
        Map<String, List<SubscriberHistoryPoint>> histories = new HashMap<>();
        String sql = "SELECT signal_id, mql_version, timestamp, subscribers, change_amount " +
                     "FROM subscriber_history ORDER BY signal_id, mql_version, timestamp ASC, id ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String signalId = rs.getString("signal_id");
                String mqlVersion = rs.getString("mql_version");
                String key = subscriberHistoryKey(signalId, mqlVersion);
                histories.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new SubscriberHistoryPoint(rs.getTimestamp("timestamp"),
                                                        rs.getInt("subscribers"),
                                                        rs.getInt("change_amount")));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all subscriber histories", e);
            return Collections.emptyMap();
        }
        return histories;
    }

    /** Stable map key for signal_id + mql_version (version lowercased). */
    public static String subscriberHistoryKey(String signalId, String mqlVersion) {
        String id = signalId == null ? "" : signalId.trim();
        String version = mqlVersion == null ? "" : mqlVersion.trim().toLowerCase(Locale.ROOT);
        return id + '\0' + version;
    }

    /** Compatibility bridge that refuses ambiguous identities instead of mixing versions. */
    @Deprecated
    public synchronized List<SubscriberHistoryPoint> getSubscriberHistory(String signalId) {
        List<String> versions = new ArrayList<>();
        String normalizedSignalId;
        try {
            normalizedSignalId = requireIdentityPart(signalId, "signalId");
        } catch (IllegalArgumentException e) {
            logger.error("Cannot load subscriber history: " + e.getMessage());
            return new ArrayList<>();
        }

        String sql = "SELECT DISTINCT mql_version FROM subscriber_history WHERE signal_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedSignalId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    versions.add(rs.getString("mql_version"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error resolving MQL version for signal " + normalizedSignalId, e);
            return new ArrayList<>();
        }

        if (versions.size() == 1) {
            return getSubscriberHistory(normalizedSignalId, versions.get(0));
        }
        logger.error("Cannot load history for signal " + normalizedSignalId +
                     " without mqlVersion: found " + versions.size() + " versions");
        return new ArrayList<>();
    }

    private String requireIdentityPart(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeMqlVersion(String mqlVersion) {
        return requireIdentityPart(mqlVersion, "mqlVersion").toLowerCase(Locale.ROOT);
    }
}
