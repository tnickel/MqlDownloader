package utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HtmlContentCache {
    private static final Logger logger = LogManager.getLogger(HtmlContentCache.class);
    private final String rootPath;
    private final Map<String, String> contentCache = new HashMap<>();
    private final Map<String, StabilityResult> stabilityCache = new HashMap<>();
    
    public HtmlContentCache(String rootPath) {
        this.rootPath = rootPath;
    }
    
    public String getHtmlContent(String fileName) {
        // Pr\u00fcfe zuerst, ob der Inhalt bereits im Cache ist
        if (contentCache.containsKey(fileName)) {
            return contentCache.get(fileName);
        }
        
        try {
            Path filePath = Paths.get(fileName);
            if (!Files.exists(filePath)) {
                logger.warn("Datei existiert nicht: " + fileName);
                return null;
            }
            
            String content = null;
            
            // Versuche zuerst mit UTF-8 zu lesen
            try {
                content = Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (MalformedInputException e) {
                // Bei UTF-8-Fehler, versuche andere Kodierungen
                logger.warn("UTF-8 Lesefehler f\u00fcr Datei: " + fileName + ", versuche andere Kodierungen");
                
                // Versuche mit ISO-8859-1 (Latin-1) zu lesen
                try {
                    content = Files.readString(filePath, StandardCharsets.ISO_8859_1);
                } catch (Exception e2) {
                    // Versuche mit Windows-1252 zu lesen
                    try {
                        content = Files.readString(filePath, Charset.forName("windows-1252"));
                    } catch (Exception e3) {
                        // Versuche bin\u00e4r zu lesen und in UTF-8 zu konvertieren
                        try {
                            byte[] bytes = Files.readAllBytes(filePath);
                            content = new String(bytes, StandardCharsets.ISO_8859_1);
                        } catch (Exception e4) {
                            logger.error("Alle Leseversuche fehlgeschlagen f\u00fcr Datei: " + fileName, e4);
                            return null;
                        }
                    }
                }
            }
            
            // F\u00fcge den gelesenen Inhalt zum Cache hinzu
            if (content != null) {
                contentCache.put(fileName, content);
            }
            
            return content;
        } catch (IOException e) {
            logger.error("Fehler beim Lesen der HTML-Datei: " + fileName, e);
            return null;
        }
    }
    
    /**
     * Speichert ein StabilityResult-Objekt im Cache.
     * 
     * @param fileName Der Dateiname als Schl\u00fcssel f\u00fcr den Cache
     * @param result Das zu speichernde StabilityResult-Objekt
     */
    public void cacheStabilityResult(String fileName, StabilityResult result) {
        if (fileName != null && result != null) {
            stabilityCache.put(fileName, result);
            logger.debug("StabilityResult f\u00fcr Datei im Cache gespeichert: " + fileName);
        }
    }
    
    /**
     * Holt ein StabilityResult-Objekt aus dem Cache.
     * 
     * @param fileName Der Dateiname als Schl\u00fcssel f\u00fcr den Cache
     * @return Das gespeicherte StabilityResult-Objekt oder null, wenn keines vorhanden ist
     */
    public StabilityResult getCachedStabilityResult(String fileName) {
        return stabilityCache.get(fileName);
    }
    
    /**
     * Pr\u00fcft, ob ein StabilityResult f\u00fcr den angegebenen Dateinamen im Cache vorhanden ist.
     * 
     * @param fileName Der zu pr\u00fcfende Dateiname
     * @return true, wenn ein Eintrag im Cache existiert, sonst false
     */
    public boolean hasStabilityResultCache(String fileName) {
        return stabilityCache.containsKey(fileName);
    }
    
    /**
     * L\u00f6scht alle zwischengespeicherten Daten.
     */
    public void clearCache() {
        contentCache.clear();
        stabilityCache.clear();
        logger.info("Cache wurde vollst\u00e4ndig geleert");
    }
}