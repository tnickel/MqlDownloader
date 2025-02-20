package utils;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileUtils {
    private static final Logger logger = LogManager.getLogger(FileUtils.class);

    public static void clearDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            logger.info("Directory does not exist: {}", directoryPath);
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        logger.warn("Could not delete file: {}", file.getAbsolutePath());
                    }
                }
            }
        }
        logger.info("Cleared directory: {}", directoryPath);
    }

    public static void clearMqlDirectories(String baseDownloadPath) {
        clearDirectory(baseDownloadPath + "/mql4");
        clearDirectory(baseDownloadPath + "/mql5");
    }
}