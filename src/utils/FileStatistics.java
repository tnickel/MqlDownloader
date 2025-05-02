package utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Klasse zur Analyse des Alters von Dateien in einem Verzeichnis.
 */
public class FileStatistics {
    private static final Logger logger = LogManager.getLogger(FileStatistics.class);
    
    /**
     * Analysiert das Alter von HTML-Dateien in einem Verzeichnis.
     * 
     * @param directory Das zu analysierende Verzeichnis
     * @param maxDays Die maximale Anzahl von Tagen für die Analyse
     * @return Eine Map mit dem Alter in Tagen als Schlüssel und der Anzahl der Dateien als Wert
     */
    public static Map<Integer, Integer> analyzeFileAge(String directory, int maxDays) {
        logger.info("Analysiere Verzeichnis: {}", directory);
        Map<Integer, Integer> ageDistribution = new HashMap<>();
        
        // Initialisiere Verteilung mit 0 für jeden Tag
        for (int i = 0; i <= maxDays; i++) {
            ageDistribution.put(i, 0);
        }
        
        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Verzeichnis existiert nicht oder ist kein Verzeichnis: {}", directory);
            return ageDistribution;
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith("_root.html"));
        if (files == null || files.length == 0) {
            logger.info("Keine HTML-Dateien gefunden in: {}", directory);
            return ageDistribution;
        }
        
        long currentTime = System.currentTimeMillis();
        int totalFiles = 0;
        
        for (File file : files) {
            long fileAge = currentTime - file.lastModified();
            int ageInDays = (int) (fileAge / (1000 * 60 * 60 * 24));
            
            // Begrenzen auf maxDays
            int bucket = Math.min(ageInDays, maxDays);
            
            // Zähler erhöhen
            ageDistribution.put(bucket, ageDistribution.get(bucket) + 1);
            totalFiles++;
        }
        
        logger.info("Analysiert: {} HTML-Dateien in {}", totalFiles, directory);
        return ageDistribution;
    }
}