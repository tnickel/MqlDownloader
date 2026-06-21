package downloader;

public interface SubscriberChangeCallback {
    /**
     * Called when a signal's subscriber count changes or a new signal is found.
     * 
     * @param change The formatted string describing the change (e.g. "SignalName: +5")
     */
    void onSubscriberChange(String change);
}
