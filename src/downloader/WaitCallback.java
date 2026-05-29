package downloader;

public interface WaitCallback {
    void onWait(int elapsedMs, int totalMs);
    void onWaitFinished();
    default void onStatusUpdate(String status) {}
}
