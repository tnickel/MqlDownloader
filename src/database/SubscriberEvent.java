package database;

import java.sql.Timestamp;

/**
 * Eine einzelne Abonnenten-Historien-Zeile mit vollständiger Signal-Identität.
 * Wird für den REST-Endpunkt /api/v1/events genutzt.
 */
public class SubscriberEvent {
    private final String signalId;
    private final String mqlVersion;
    private final String signalName;
    private final Timestamp timestamp;
    private final int subscribers;
    private final int changeAmount;
    private final String recordType;

    public SubscriberEvent(String signalId, String mqlVersion, String signalName,
                           Timestamp timestamp, int subscribers, int changeAmount, String recordType) {
        this.signalId = signalId;
        this.mqlVersion = mqlVersion;
        this.signalName = signalName;
        this.timestamp = timestamp;
        this.subscribers = subscribers;
        this.changeAmount = changeAmount;
        this.recordType = recordType;
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

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public int getChangeAmount() {
        return changeAmount;
    }

    public String getRecordType() {
        return recordType;
    }
}
