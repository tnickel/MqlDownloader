package converter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.HtmlParser;
import utils.ChartPoint;
import utils.StabilityResult;

public class HtmlConverter {
    private static final Logger logger = LogManager.getLogger(HtmlConverter.class);
    private final String downloadPath;
    private final HtmlParser htmlParser;
    private ConversionProgress progressCallback;
    
    public HtmlConverter(String downloadPath) {
        this.downloadPath = downloadPath;
        this.htmlParser = new HtmlParser(downloadPath);
        logger.info("HtmlConverter initialized with path: " + downloadPath);
    }
    
    public void convertAllHtmlFiles() {
        logger.info("Starting conversion process...");
        int totalFiles = 0;
        try {
            totalFiles += countHtmlFiles(Paths.get(downloadPath, "mql4"));
            totalFiles += countHtmlFiles(Paths.get(downloadPath, "mql5"));
        } catch (IOException e) {
            logger.error("Error counting files", e);
        }
        int currentFile = 0;
        Path mql4Path = Paths.get(downloadPath, "mql4");
        currentFile = processDirectory(mql4Path, currentFile, totalFiles);
        Path mql5Path = Paths.get(downloadPath, "mql5");
        currentFile = processDirectory(mql5Path, currentFile, totalFiles);
        updateProgress(100, "Konvertierung abgeschlossen");
    }
    
    public void setProgressCallback(ConversionProgress callback) {
        this.progressCallback = callback;
    }
    
    private int processDirectory(Path directory, int currentFile, int totalFiles) {
        try {
            if (!Files.exists(directory)) {
                return currentFile;
            }
            List<Path> htmlFiles = Files.walk(directory)
                .filter(path -> path.toString().endsWith("_root.html"))
                .collect(Collectors.toList());
            for (Path htmlFile : htmlFiles) {
                convertHtmlFile(htmlFile);
                currentFile++;
                updateProgress(
                    (int)((currentFile / (double)totalFiles) * 100),
                    String.format("Konvertiere Datei %d von %d", currentFile, totalFiles)
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
        return (int) Files.walk(directory)
            .filter(path -> path.toString().endsWith("_root.html"))
            .count();
    }
    
    private void convertHtmlFile(Path htmlFile) throws IOException {
        String htmlFileName = htmlFile.toString();
        String txtFileName = htmlFileName.replace("_root.html", "_root.txt");
        Path txtFile = Paths.get(txtFileName);
        logger.info("Processing file: " + htmlFileName + " to " + txtFileName);
        
        double balance = htmlParser.getBalance(htmlFileName);
        double equityDrawdown = htmlParser.getEquityDrawdown(htmlFileName);
        double avgProfit = htmlParser.getAvr3MonthProfit(htmlFileName);
        List<String> lastMonths = htmlParser.getLastThreeMonthsDetails(htmlFileName);
        List<String> allMonths = htmlParser.getAllMonthsDetails(htmlFileName);
        double stability = htmlParser.getStabilitaetswert(htmlFileName);
        StabilityResult stabilityResult = htmlParser.getStabilitaetswertDetails(htmlFileName);
        String stabilityDetails = (stabilityResult != null) ? stabilityResult.getDetails() : null;
        
        List<ChartPoint> drawdownPoints = htmlParser.getDrawdownChartData(htmlFileName);
        
        StringBuilder output = new StringBuilder();
        output.append("Balance=").append(String.format("%.2f", balance)).append("\n");
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
        output.append("\n********************************\n\n");
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
        
        Files.writeString(txtFile, output.toString());
        logger.info("Successfully converted " + htmlFile.getFileName() + " to " + txtFile.getFileName());
    }
}
