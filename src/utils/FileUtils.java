package utils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * \u00dcberpr\u00fcft und korrigiert die Dateinummern, indem immer die Nummer der HTML-Datei
     * als die korrekte Nummer verwendet wird.
     * @param directoryPath Verzeichnis, in dem die Dateien \u00fcberpr\u00fcft werden sollen
     * @return Liste der korrigierten Dateien
     */
    public static List<String> correctFileNumbers(String directoryPath) {
        List<String> correctedFiles = new ArrayList<>();
        File directory = new File(directoryPath);
        
        if (!directory.exists() || !directory.isDirectory()) {
            logger.warn("Verzeichnis existiert nicht: {}", directoryPath);
            return correctedFiles;
        }
        
        // Ein Anzeigename identifiziert kein Signal. Mehrere HTML-IDs unter
        // demselben Namen duerfen niemals auf die zuletzt gefundene ID fallen.
        Map<String, Set<String>> numbersByName = new HashMap<>();
        Map<String, Set<String>> namesByNumber = new HashMap<>();
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
                numbersByName.computeIfAbsent(baseName, key -> new HashSet<>()).add(number);
                namesByNumber.computeIfAbsent(number, key -> new HashSet<>()).add(baseName);
            }
        }
        
        // Nur eine bijektive HTML-Dateinamen-Zuordnung erlaubt die bisherige
        // Nummernkorrektur. Ambige Gruppen bleiben vollstaendig unveraendert.
        Map<String, String> htmlFileNumbers = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : numbersByName.entrySet()) {
            if (entry.getValue().size() != 1) {
                logger.warn("Keine Dateinummernkorrektur fuer mehrdeutigen Namen {}: {}",
                        entry.getKey(), entry.getValue());
                continue;
            }
            String number = entry.getValue().iterator().next();
            if (namesByNumber.get(number).size() != 1) {
                logger.warn("Keine Dateinummernkorrektur fuer mehrfach benannte HTML-ID {}: {}",
                        number, namesByNumber.get(number));
                continue;
            }
            htmlFileNumbers.put(entry.getKey(), number);
        }

        // \u00dcberpr\u00fcfe CSV- und TXT-Dateien und korrigiere sie
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

        Map<String, Integer> filesByName = new HashMap<>();
        for (File file : files) {
            if (!file.isFile()) continue;
            Matcher matcher = numberPattern.matcher(file.getName());
            if (matcher.find()) {
                String baseName = file.getName().substring(0,
                        file.getName().length() - matcher.group(0).length());
                filesByName.put(baseName, filesByName.getOrDefault(baseName, 0) + 1);
            }
        }
        
        for (File file : files) {
            if (!file.isFile()) continue;
            String fileName = file.getName();
            Matcher matcher = numberPattern.matcher(fileName);
            
            if (matcher.find()) {
                String currentNumber = matcher.group(1);
                String baseName = fileName.substring(0, fileName.length() - matcher.group(0).length());
                
                // \u00dcberpr\u00fcfe, ob wir eine HTML-Datei mit dem gleichen Basisnamen haben
                if (htmlFileNumbers.containsKey(baseName)) {
                    String correctNumber = htmlFileNumbers.get(baseName);
                    
                    // Wenn die Nummer anders ist, korrigiere sie
                    if (!currentNumber.equals(correctNumber)) {
                        if (filesByName.get(baseName) != 1) {
                            logger.warn("Keine Dateinummernkorrektur fuer mehrere {}-Dateien mit Namen {}",
                                    fileType, baseName);
                            continue;
                        }
                        String newFileName = baseName + "_" + correctNumber + suffix + fileExtension;
                        File newFile = new File(directory, newFileName);

                        if (newFile.exists()) {
                            logger.warn("Dateinummernkorrektur wuerde vorhandenes Ziel ueberschreiben: {}",
                                    newFile.getAbsolutePath());
                            continue;
                        }
                        
                        try {
                            // Auch bei einem zwischenzeitlich angelegten Ziel
                            // muss der Move scheitern, niemals Daten ersetzen.
                            Files.move(file.toPath(), newFile.toPath());
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
     * \u00dcberpr\u00fcft und korrigiert die Dateinummern in allen MQL-Verzeichnissen.
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
        
        // MQL4MQL5-Verzeichnis
        String mql4mql5Path = baseDownloadPath + "/mql4mql5";
        File mql4mql5Dir = new File(mql4mql5Path);
        if (mql4mql5Dir.exists() && mql4mql5Dir.isDirectory()) {
            allCorrected.addAll(correctFileNumbers(mql4mql5Path));
        }

        // Direktes Verzeichnis
        allCorrected.addAll(correctFileNumbers(baseDownloadPath));

        return allCorrected;
    }
}
