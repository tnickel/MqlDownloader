package converter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import calculators.MPDDCalculator;
import utils.BasicDataProvider;
import utils.ChartPoint;
import utils.FileDataReader;
import utils.FileUtils;
import utils.HtmlDatabase;
import utils.HtmlParser;
import utils.StabilityResult;

public class HtmlConverter {
    private static final Logger logger = LogManager.getLogger(HtmlConverter.class);
    private final String downloadPath;
    private final HtmlParser htmlParser;
    private final HtmlDatabase htmlDatabase;
    private final FileDataReader fileDataReader;
    private final BasicDataProvider basicDataProvider;
    private final MPDDCalculator mpddCalculator;
    private ConversionProgress progressCallback;
    private Path conversionLogPath;
    private int deletedProvidersCount = 0;
    private int processedProvidersCount = 0;
    
    public HtmlConverter(String downloadPath) {
        this.downloadPath = downloadPath;
        this.htmlParser = new HtmlParser(downloadPath);
        this.htmlDatabase = new HtmlDatabase(htmlParser);
        this.fileDataReader = new FileDataReader(downloadPath);
        this.basicDataProvider = new BasicDataProvider(fileDataReader);
        this.mpddCalculator = new MPDDCalculator(htmlDatabase);
        this.conversionLogPath = Paths.get(downloadPath, "conversionLog.txt");
        logger.info("HtmlConverter initialized with path: " + downloadPath);
    }
    
    public void convertAllHtmlFiles() {
        logger.info("Starting conversion process...");
        
        // Konvertierungs-Log initialisieren
        initializeConversionLog();
        
        // Zähler zurücksetzen
        deletedProvidersCount = 0;
        processedProvidersCount = 0;
        
        // Zuerst die Dateinummern korrigieren
        List<String> correctedFiles = FileUtils.correctAllDirectories(downloadPath);
        if (!correctedFiles.isEmpty()) {
            logger.info("Dateinummern wurden korrigiert: {} Dateien", correctedFiles.size());
            for (String message : correctedFiles) {
                logger.info(message);
            }
        }
        
        int totalFiles = 0;
        try {
            totalFiles += countHtmlFiles(Paths.get(downloadPath, "mql4"));
            totalFiles += countHtmlFiles(Paths.get(downloadPath, "mql5"));
            logger.info("Insgesamt " + totalFiles + " HTML-Dateien zu verarbeiten");
        } catch (IOException e) {
            logger.error("Error counting files", e);
        }
        int currentFile = 0;
        
        // MQL4 Verzeichnis verarbeiten
        Path mql4Path = Paths.get(downloadPath, "mql4");
        logger.info("Starte Verarbeitung von MQL4-Dateien...");
        currentFile = processDirectory(mql4Path, currentFile, totalFiles);
        
        // MQL5 Verzeichnis verarbeiten
        Path mql5Path = Paths.get(downloadPath, "mql5");
        logger.info("Starte Verarbeitung von MQL5-Dateien...");
        currentFile = processDirectory(mql5Path, currentFile, totalFiles);
        
        // Abschließende Log-Einträge
        finalizeConversionLog();
        
        updateProgress(100, "Konvertierung abgeschlossen - " + processedProvidersCount + " Provider verarbeitet, " + deletedProvidersCount + " Provider gelöscht (3MPDD < 0.5)");
    }
    
    public void setProgressCallback(ConversionProgress callback) {
        this.progressCallback = callback;
    }
    
    private int processDirectory(Path directory, int currentFile, int totalFiles) {
        try {
            if (!Files.exists(directory)) {
                logger.info("Verzeichnis existiert nicht: " + directory);
                return currentFile;
            }
            
            // NUR direkte Dateien im Verzeichnis, KEINE Unterverzeichnisse
            List<Path> htmlFiles = Files.list(directory)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().endsWith("_root.html"))
                .collect(Collectors.toList());
                
            logger.info("Verarbeite Verzeichnis: " + directory + " - " + htmlFiles.size() + " HTML-Dateien gefunden");
            
            for (Path htmlFile : htmlFiles) {
                convertHtmlFile(htmlFile);
                currentFile++;
                updateProgress(
                    (int)((currentFile / (double)totalFiles) * 100),
                    String.format("Konvertiere Datei %d von %d (%s)", currentFile, totalFiles, htmlFile.getFileName())
                );
            }
        } catch (IOException e) {
            logger.error("Error processing directory: " + directory, e);
        }
        return currentFile;
    }
    
    private void updateProgress(int percentage, String message) {
        if (progressCallback != null) {
            progressCallback.onProgress(percentage, message);
        }
    }
    
    private int countHtmlFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }
        // NUR direkte Dateien zählen, KEINE Unterverzeichnisse
        int count = (int) Files.list(directory)
            .filter(path -> Files.isRegularFile(path))
            .filter(path -> path.toString().endsWith("_root.html"))
            .count();
        logger.info("Gefunden: " + count + " HTML-Dateien in " + directory);
        return count;
    }
    
    private void convertHtmlFile(Path htmlFile) throws IOException {
        String htmlFileName = htmlFile.toString();
        String txtFileName = htmlFileName.replace("_root.html", "_root.txt");
        Path txtFile = Paths.get(txtFileName);
        
        // Provider-Name aus Dateiname extrahieren
        String providerName = extractProviderName(htmlFileName);
        
        logger.info("Processing file: " + htmlFileName + " to " + txtFileName);
        
        // OPTIMIERUNG: Zuerst 3MPDD berechnen und prüfen ob < 0.5
        double mpdd3 = calculate3MPDD(htmlFileName);
        
        if (mpdd3 < 0.5) {
            logger.info("3MPDD zu niedrig (" + String.format("%.4f", mpdd3) + " < 0.5) für " + htmlFileName + " - Dateien werden gelöscht");
            deleteRelatedFiles(htmlFileName);
            
            // Log-Eintrag für gelöschten Provider mit Dateipfad
            logProviderAction(providerName, mpdd3, "GELÖSCHT - 3MPDD < 0.5", htmlFileName);
            deletedProvidersCount++;
            
            return; // Keine weitere Verarbeitung
        }
        
        logger.info("3MPDD OK (" + String.format("%.4f", mpdd3) + " >= 0.5) für " + htmlFileName + " - Vollständige Verarbeitung");
        
        double balance = htmlParser.getBalance(htmlFileName);
        double equityDrawdownGraphic = htmlParser.getEquityDrawdownGraphic(htmlFileName);
        double equityDrawdown = htmlParser.getEquityDrawdown(htmlFileName);
        double avgProfit = htmlParser.getAvr3MonthProfit(htmlFileName);
        List<String> lastMonths = htmlParser.getLastThreeMonthsDetails(htmlFileName);
        List<String> allMonths = htmlParser.getAllMonthsDetails(htmlFileName);
        double stability = htmlParser.getStabilitaetswert(htmlFileName);
        StabilityResult stabilityResult = htmlParser.getStabilitaetswertDetails(htmlFileName);
        String stabilityDetails = (stabilityResult != null) ? stabilityResult.getDetails() : null;
        
        List<ChartPoint> drawdownPoints = htmlParser.getDrawdownChartData(htmlFileName);
        
        // Erstelle die vollständige .txt-Datei
        StringBuilder output = new StringBuilder();
        output.append("Balance=").append(String.format("%.2f", balance)).append("\n");
        output.append("MaxDDGraphic=").append(String.format("%.2f", equityDrawdownGraphic)).append("\n");
        output.append("EquityDrawdown=").append(String.format("%.2f", equityDrawdown)).append("\n");
        output.append("Average3MonthProfit=").append(String.format("%.2f", avgProfit)).append("\n");
        output.append("StabilityValue=").append(String.format("%.2f", stability)).append("\n");
        output.append("MonthProfitProz=");
        if (!allMonths.isEmpty()) {
            String monthValues = allMonths.stream()
                .map(month -> {
                    String[] parts = month.split(":");
                    String date = parts[0];
                    String value = parts[1].trim();
                    return date + "=" + value;
                })
                .collect(Collectors.joining(","));
            output.append(monthValues);
        }
        output.append("\n");
        
        // Füge 3MPDD hinzu (bereits berechnet)
        output.append("3MPDD=").append(String.format("%.4f", mpdd3)).append("\n");
        
        // Füge den Rest hinzu
        output.append("********************************\n\n");
        output.append("Drawdown Chart Data=\n");
        if (drawdownPoints.isEmpty()) {
            output.append("Keine roten Drawdown-Pfad-Daten gefunden\n");
        } else {
            for (ChartPoint cp : drawdownPoints) {
                output.append(cp.getDate())
                      .append(": ")
                      .append(String.format("%.2f%%", cp.getValue()))
                      .append("\n");
            }
        }
        output.append("-----------------\n");
        output.append("\n********************************\n\n");
        output.append("Last 3 Months Details=\n");
        for (String month : lastMonths) {
            output.append(month).append("\n");
        }
        output.append("********************************\n\n");
        output.append("Stability Details=\n");
        if (stabilityDetails != null && !stabilityDetails.trim().isEmpty()) {
            String cleanDetails = stabilityDetails.replace("<br>", "\n")
                                                 .replace("- ", " - ")
                                                 .replaceAll("\\s*:\\s*", ": ")
                                                 .trim();
            output.append(cleanDetails).append("\n");
        } else {
            output.append("Nicht genügend Daten verfügbar für detaillierte Stabilitätsanalyse\n");
        }
        output.append("********************************");
        
        // Schreibe die vollständige Datei mit 3MPDD
        Files.writeString(txtFile, output.toString());
        
        // Log-Eintrag für verarbeiteten Provider mit Dateipfad
        logProviderAction(providerName, mpdd3, "OK - Vollständig verarbeitet", htmlFileName);
        processedProvidersCount++;
        
        logger.info("Successfully converted " + htmlFile.getFileName() + " to " + txtFile.getFileName() + " with 3MPDD: " + String.format("%.4f", mpdd3));
    }
    
    /**
     * Berechnet den 3MPDD-Wert für eine HTML-Datei
     * 
     * @param htmlFileName Pfad zur HTML-Datei
     * @return 3MPDD-Wert
     */
    private double calculate3MPDD(String htmlFileName) {
        try {
            // Verwende den MPDDCalculator um 3MPDD zu berechnen
            double mpdd = mpddCalculator.calculate3MPDD(htmlFileName);
            
            logger.info("3MPDD berechnet für " + htmlFileName + ": " + String.format("%.4f", mpdd));
            return mpdd;
            
        } catch (Exception e) {
            logger.error("Fehler beim Berechnen von 3MPDD für " + htmlFileName + ": " + e.getMessage(), e);
            return 0.0;
        }
    }
    
    /**
     * Löscht alle zugehörigen Dateien eines Signalproviders (.html, .csv, .txt)
     * 
     * @param htmlFileName Pfad zur HTML-Datei
     */
    private void deleteRelatedFiles(String htmlFileName) {
        try {
            Path htmlPath = Paths.get(htmlFileName);
            
            // Pfade für entsprechende CSV- und TXT-Dateien erzeugen
            String baseName = htmlFileName.replace("_root.html", "");
            Path csvPath = Paths.get(baseName + ".csv");
            Path txtPath = Paths.get(baseName + "_root.txt");
            
            // Dateien löschen, wenn sie existieren
            int deletedCount = 0;
            
            if (Files.exists(htmlPath)) {
                Files.delete(htmlPath);
                logger.info("Gelöscht: " + htmlPath);
                deletedCount++;
            }
            
            if (Files.exists(csvPath)) {
                Files.delete(csvPath);
                logger.info("Gelöscht: " + csvPath);
                deletedCount++;
            }
            
            if (Files.exists(txtPath)) {
                Files.delete(txtPath);
                logger.info("Gelöscht: " + txtPath);
                deletedCount++;
            }
            
            logger.info("Signalprovider mit schlechtem 3MPDD entfernt: " + deletedCount + " Dateien gelöscht für " + baseName);
            
        } catch (IOException e) {
            logger.error("Fehler beim Löschen der Dateien für " + htmlFileName + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Getter für den MPDDCalculator (für externe Verwendung)
     */
    public MPDDCalculator getMPDDCalculator() {
        return mpddCalculator;
    }
    
    /**
     * Getter für den BasicDataProvider (für externe Verwendung)
     */
    public BasicDataProvider getBasicDataProvider() {
        return basicDataProvider;
    }
    
    /**
     * Initialisiert das Konvertierungs-Logfile mit erweitertem Format
     */
    private void initializeConversionLog() {
        try {
            StringBuilder logHeader = new StringBuilder();
            logHeader.append("=".repeat(120)).append("\n");
            logHeader.append("CONVERSION LOG - ").append(java.time.LocalDateTime.now().toString()).append("\n");
            logHeader.append("=".repeat(120)).append("\n");
            logHeader.append("HINWEIS: Provider mit 3MPDD < 0.5 werden automatisch gelöscht\n");
            logHeader.append("=".repeat(120)).append("\n");
            logHeader.append(String.format("%-35s | %-10s | %-25s | %s\n", "PROVIDER NAME", "3MPDD", "AKTION", "DATEIPFAD"));
            logHeader.append("-".repeat(120)).append("\n");
            
            Files.writeString(conversionLogPath, logHeader.toString());
            logger.info("Conversion log initialisiert: " + conversionLogPath);
            
        } catch (IOException e) {
            logger.error("Fehler beim Initialisieren des Conversion Logs: " + e.getMessage(), e);
        }
    }
    
    /**
     * Schreibt einen Eintrag in das Konvertierungs-Logfile mit Dateipfad
     */
    private void logProviderAction(String providerName, double mpdd3, String action, String filePath) {
        try {
            // Relativen Pfad erstellen für bessere Lesbarkeit
            String relativePath = filePath.replace(downloadPath, "").replace("\\", "/");
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            
            String logEntry = String.format("%-35s | %-10.4f | %-25s | %s\n", 
                                          providerName.length() > 35 ? providerName.substring(0, 32) + "..." : providerName,
                                          mpdd3, 
                                          action,
                                          relativePath);
            
            Files.writeString(conversionLogPath, logEntry, java.nio.file.StandardOpenOption.APPEND);
            
        } catch (IOException e) {
            logger.error("Fehler beim Schreiben ins Conversion Log: " + e.getMessage(), e);
        }
    }
    
    /**
     * Schreibt die abschließenden Statistiken in das Logfile
     */
    private void finalizeConversionLog() {
        try {
            StringBuilder logFooter = new StringBuilder();
            logFooter.append("-".repeat(120)).append("\n");
            logFooter.append("ZUSAMMENFASSUNG:\n");
            logFooter.append("Provider verarbeitet: ").append(processedProvidersCount).append("\n");
            logFooter.append("Provider gelöscht: ").append(deletedProvidersCount).append(" (3MPDD < 0.5)\n");
            logFooter.append("Gesamt Provider: ").append(processedProvidersCount + deletedProvidersCount).append("\n");
            logFooter.append("=".repeat(120)).append("\n");
            
            Files.writeString(conversionLogPath, logFooter.toString(), java.nio.file.StandardOpenOption.APPEND);
            logger.info("Conversion log finalisiert mit " + (processedProvidersCount + deletedProvidersCount) + " Providern");
            
        } catch (IOException e) {
            logger.error("Fehler beim Finalisieren des Conversion Logs: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extrahiert den Provider-Namen aus dem Dateinamen
     */
    private String extractProviderName(String htmlFileName) {
        try {
            Path path = Paths.get(htmlFileName);
            String fileName = path.getFileName().toString();
            
            // Entferne "_root.html" und eventuelle Nummer am Ende
            String providerName = fileName.replace("_root.html", "");
            
            // Entferne Nummer am Ende (z.B. "_123456")
            if (providerName.matches(".*_\\d+$")) {
                int lastUnderscore = providerName.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    providerName = providerName.substring(0, lastUnderscore);
                }
            }
            
            return providerName;
            
        } catch (Exception e) {
            logger.warn("Fehler beim Extrahieren des Provider-Namens aus " + htmlFileName + ": " + e.getMessage());
            return "UNKNOWN_PROVIDER";
        }
    }
    
    /**
     * Getter für die Anzahl der gelöschten Provider
     */
    public int getDeletedProvidersCount() {
        return deletedProvidersCount;
    }
    
    /**
     * Getter für die Anzahl der verarbeiteten Provider
     */
    public int getProcessedProvidersCount() {
        return processedProvidersCount;
    }
}