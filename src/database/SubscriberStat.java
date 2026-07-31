package database;

import java.sql.Timestamp;

public class SubscriberStat {
    private final String signalId;
    private final String mqlVersion;
    private final String signalName;
    private final int subscribers;
    private final int latestChange;
    private final int weekChange;
    private final int monthChange;
    private final Timestamp lastUpdated;
    private final String url;

    public SubscriberStat(String signalId, String mqlVersion, String signalName, int subscribers, int latestChange, int weekChange, int monthChange, Timestamp lastUpdated, String url) {
        this.signalId = signalId;
        this.mqlVersion = mqlVersion;
        this.signalName = signalName;
        this.subscribers = subscribers;
        this.latestChange = latestChange;
        this.weekChange = weekChange;
        this.monthChange = monthChange;
        this.lastUpdated = lastUpdated;
        this.url = (url != null && !url.trim().isEmpty()) ? url : "https://www.mql5.com/en/signals/" + signalId;
    }

    public SubscriberStat(String signalId, String mqlVersion, String signalName, int subscribers, int latestChange, Timestamp lastUpdated, String url) {
        this(signalId, mqlVersion, signalName, subscribers, latestChange, latestChange, latestChange, lastUpdated, url);
    }

    public String getSignalId() {
        return signalId;
    }

    public String getMqlVersion() {
        return mqlVersion;
    }

    public String getSignalName() {
        return signalName;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public int getLatestChange() {
        return latestChange;
    }

    public int getWeekChange() {
        return weekChange;
    }

    public int getMonthChange() {
        return monthChange;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public String getUrl() {
        return url;
    }
}
