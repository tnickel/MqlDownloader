package utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    /**
     * Überprüft und korrigiert die Dateinummern, indem immer die Nummer der HTML-Datei
     * als die korrekte Nummer verwendet wird.
     * @param directoryPath Verzeichnis, in dem die Dateien überprüft werden sollen
     * @return Liste der korrigierten Dateien
     */
    public static List<String> correctFileNumbers(String directoryPath) {
        List<String> correctedFiles = new ArrayList<>();
        File directory = new File(directoryPath);
        
        if (!directory.exists() || !directory.isDirectory()) {
            logger.warn("Verzeichnis existiert nicht: {}", directoryPath);
            return correctedFiles;
        }
        
        // Sammle alle HTML-Dateien und ihre Nummern
        Map<String, String> htmlFileNumbers = new HashMap<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith("_root.html"));
        
        if (files == null || files.length == 0) {
            logger.info("Keine HTML-Dateien gefunden in: {}", directoryPath);
            return correctedFiles;
        }
        
        // Extrahiere Basisnamen und Nummern aus HTML-Dateien
        Pattern numberPattern = Pattern.compile("_(\\d+)_root\\.html$");
        for (File htmlFile : files) {
            String fileName = htmlFile.getName();
            Matcher matcher = numberPattern.matcher(fileName);
            
            if (matcher.find()) {
                String number = matcher.group(1);
                String baseName = fileName.substring(0, fileName.length() - matcher.group(0).length());
                htmlFileNumbers.put(baseName, number);
            }
        }
        
        // Überprüfe CSV- und TXT-Dateien und korrigiere sie
        correctFileType(directory, htmlFileNumbers, "csv", correctedFiles);
        correctFileType(directory, htmlFileNumbers, "txt", correctedFiles);
        
        return correctedFiles;
    }
    
    /**
     * Korrigiert Dateien eines bestimmten Typs basierend auf den HTML-Dateinummern.
     */
    private static void correctFileType(File directory, Map<String, String> htmlFileNumbers, 
                                        String fileType, List<String> correctedFiles) {
        String suffix = fileType.equals("txt") ? "_root" : "";
        String fileExtension = "." + fileType;
        
        File[] files = directory.listFiles((dir, name) -> name.endsWith(fileExtension));
        if (files == null) return;
        
        Pattern numberPattern = Pattern.compile("_(\\d+)" + (suffix.isEmpty() ? "" : Pattern.quote(suffix)) + "\\" + fileExtension + "$");
        
        for (File file : files) {
            String fileName = file.getName();
            Matcher matcher = numberPattern.matcher(fileName);
            
            if (matcher.find()) {
                String currentNumber = matcher.group(1);
                String baseName = fileName.substring(0, fileName.length() - matcher.group(0).length());
                
                // Überprüfe, ob wir eine HTML-Datei mit dem gleichen Basisnamen haben
                if (htmlFileNumbers.containsKey(baseName)) {
                    String correctNumber = htmlFileNumbers.get(baseName);
                    
                    // Wenn die Nummer anders ist, korrigiere sie
                    if (!currentNumber.equals(correctNumber)) {
                        String newFileName = baseName + "_" + correctNumber + suffix + fileExtension;
                        File newFile = new File(directory, newFileName);
                        
                        try {
                            Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            String message = String.format("Dateinummer korrigiert: %s -> %s", 
                                                         fileName, newFileName);
                            correctedFiles.add(message);
                            logger.info(message);
                        } catch (Exception e) {
                            logger.error("Fehler beim Umbenennen von {}: {}", fileName, e.getMessage());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Überprüft und korrigiert die Dateinummern in allen MQL-Verzeichnissen.
     * @param baseDownloadPath Basis-Downloadpfad
     * @return Liste der korrigierten Dateien
     */
    public static List<String> correctAllDirectories(String baseDownloadPath) {
        List<String> allCorrected = new ArrayList<>();
        
        // MQL4-Verzeichnis
        String mql4Path = baseDownloadPath + "/mql4";
        allCorrected.addAll(correctFileNumbers(mql4Path));
        
        // MQL5-Verzeichnis
        String mql5Path = baseDownloadPath + "/mql5";
        allCorrected.addAll(correctFileNumbers(mql5Path));
        
        return allCorrected;
    }
}