package downloader;

public interface ProgressCallback {
    /**
     * Called when a signal provider has been processed.
     * 
     * @param count The current number of processed providers
     */
    void onProgress(int count);
}