package database;

import java.sql.Timestamp;

public class SubscriberHistoryPoint {
    private final Timestamp timestamp;
    private final int subscribers;
    private final int changeAmount;

    public SubscriberHistoryPoint(Timestamp timestamp, int subscribers, int changeAmount) {
        this.timestamp = timestamp;
        this.subscribers = subscribers;
        this.changeAmount = changeAmount;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public int getChangeAmount() {
        return changeAmount;
    }
}
