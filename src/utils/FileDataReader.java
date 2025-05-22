package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Klasse zum Lesen von Daten aus .txt-Dateien, die vom HtmlConverter erstellt wurden
 */
public class FileDataReader {
    private static final Logger LOGGER = Logger.getLogger(FileDataReader.class.getName());
    
    private final String rootPath;
    
    public FileDataReader(String rootPath) {
        this.rootPath = rootPath;
    }
    
    /**
     * Liest die Daten aus einer .txt-Datei und gibt sie als Map zurück
     * 
     * @param fileName Name der Provider-HTML-Datei (wird zu .txt konvertiert)
     * @return Map mit Schlüssel-Wert-Paaren aus der .txt-Datei
     */
    public Map<String, String> getFileData(String fileName) {
        Map<String, String> data = new HashMap<>();
        
        try {
            // Konvertiere HTML-Dateinamen zu TXT-Dateinamen
            String txtFileName = fileName.replace("_root.html", "_root.txt");
            Path txtPath = Paths.get(txtFileName);
            
            if (!Files.exists(txtPath)) {
                LOGGER.warning("TXT-Datei existiert nicht: " + txtPath);
                return data;
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(txtPath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    // Überspringe leere Zeilen und Kommentarzeilen
                    if (line.isEmpty() || line.startsWith("*") || line.startsWith("-")) {
                        continue;
                    }
                    
                    // Parse Schlüssel=Wert Paare
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            data.put(key, value);
                        }
                    }
                }
            }
            
            LOGGER.fine("Daten aus " + txtPath + " gelesen: " + data.size() + " Einträge");
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Lesen der Datei " + fileName + ": " + e.getMessage());
        }
        
        return data;
    }
    
    /**
     * Prüft, ob eine .txt-Datei für den gegebenen HTML-Dateinamen existiert
     * 
     * @param fileName Name der Provider-HTML-Datei
     * @return true wenn die entsprechende .txt-Datei existiert
     */
    public boolean fileExists(String fileName) {
        String txtFileName = fileName.replace("_root.html", "_root.txt");
        Path txtPath = Paths.get(txtFileName);
        return Files.exists(txtPath);
    }
    
    /**
     * Holt einen spezifischen Wert aus der .txt-Datei
     * 
     * @param fileName Name der Provider-HTML-Datei
     * @param key Der Schlüssel des gewünschten Werts
     * @return Der Wert oder null wenn nicht gefunden
     */
    public String getValue(String fileName, String key) {
        Map<String, String> data = getFileData(fileName);
        return data.get(key);
    }
    
    /**
     * Holt einen Wert als Double aus der .txt-Datei
     * 
     * @param fileName Name der Provider-HTML-Datei
     * @param key Der Schlüssel des gewünschten Werts
     * @param defaultValue Standardwert wenn nicht gefunden oder nicht parsebar
     * @return Der Wert als Double
     */
    public double getDoubleValue(String fileName, String key, double defaultValue) {
        String value = getValue(fileName, key);
        if (value != null) {
            try {
                return Double.parseDouble(value.replace(",", "."));
            } catch (NumberFormatException e) {
                LOGGER.warning("Konnte Wert nicht zu Double konvertieren: " + value + " für Schlüssel: " + key);
            }
        }
        return defaultValue;
    }
}