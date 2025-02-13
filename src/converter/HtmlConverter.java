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
import utils.StabilityResult;

public class HtmlConverter {
    private static final Logger logger = LogManager.getLogger(HtmlConverter.class);
    private final String downloadPath;
    private final HtmlParser htmlParser;

    public HtmlConverter(String downloadPath) {
        this.downloadPath = downloadPath;
        this.htmlParser = new HtmlParser(downloadPath);
        logger.info("HtmlConverter initialized with path: " + downloadPath);
    }

    public void convertAllHtmlFiles() {
        logger.info("Starting conversion process...");
        
        // Konvertiere erst MQL4 Dateien
        Path mql4Path = Paths.get(downloadPath, "mql4");
        logger.info("Processing MQL4 directory: " + mql4Path);
        convertFilesInDirectory(mql4Path);
        
        // Dann MQL5 Dateien
        Path mql5Path = Paths.get(downloadPath, "mql5");
        logger.info("Processing MQL5 directory: " + mql5Path);
        convertFilesInDirectory(mql5Path);
        
        logger.info("Conversion process completed.");
    }
    
    private void convertFilesInDirectory(Path directory) {
        try {
            if (!Files.exists(directory)) {
                logger.warn("Directory does not exist: " + directory);
                return;
            }

            logger.info("Searching for HTML files in: " + directory);
            List<Path> htmlFiles = Files.walk(directory)
                    .filter(path -> path.toString().endsWith("_root.html"))
                    .collect(Collectors.toList());

            logger.info("Found " + htmlFiles.size() + " HTML files in " + directory);

            for (Path htmlFile : htmlFiles) {
                try {
                    logger.info("Converting file: " + htmlFile);
                    convertHtmlFile(htmlFile);
                } catch (Exception e) {
                    logger.error("Error converting file: " + htmlFile, e);
                }
            }
        } catch (IOException e) {
            logger.error("Error accessing directory: " + directory, e);
        }
    }

    private void convertHtmlFile(Path htmlFile) throws IOException {
        String htmlFileName = htmlFile.getFileName().toString();
        String txtFileName = htmlFileName.replace(".html", ".txt");
        Path txtFile = htmlFile.getParent().resolve(txtFileName);

        // Get relative path from download directory
        Path downloadDir = Paths.get(downloadPath);
        String relativeHtmlPath = downloadDir.relativize(htmlFile).toString().replace('\\', '/');
        logger.info("Processing file: " + relativeHtmlPath + " to " + txtFileName);

        // Extract values using HtmlParser with relative path
        double balance = htmlParser.getBalance(relativeHtmlPath);
        double equityDrawdown = htmlParser.getEquityDrawdown(relativeHtmlPath);
        double avgProfit = htmlParser.getAvr3MonthProfit(relativeHtmlPath);
        List<String> lastMonths = htmlParser.getLastThreeMonthsDetails(relativeHtmlPath);
        double stability = htmlParser.getStabilitaetswert(relativeHtmlPath);
        StabilityResult stabilityResult = htmlParser.getStabilitaetswertDetails(relativeHtmlPath);
        String stabilityDetails = stabilityResult != null ? stabilityResult.getDetails() : null;

        // Build output string
        StringBuilder output = new StringBuilder();
        output.append("Balance=").append(String.format("%.2f", balance)).append("\n");
        output.append("EquityDrawdown=").append(String.format("%.2f", equityDrawdown)).append("\n");
        output.append("Average3MonthProfit=").append(String.format("%.2f", avgProfit)).append("\n");
        output.append("StabilityValue=").append(String.format("%.2f", stability)).append("\n");
        output.append("********************************\n\n");
        
        output.append("Last 3 Months Details=\n");
        if (lastMonths != null && !lastMonths.isEmpty()) {
            for (String month : lastMonths) {
                output.append(month).append("\n");
            }
        }
        output.append("********************************\n\n");
        
        output.append("Stability Details=\n");
        if (stabilityDetails != null && !stabilityDetails.trim().isEmpty()) {
            String cleanDetails = stabilityDetails.replace("<br>", "\n").replace("- ", " - ")
                    .replaceAll("\\s*:\\s*", ": ").trim();
            output.append(cleanDetails).append("\n");
        } else {
            output.append("Nicht genügend Daten verfügbar für detaillierte Stabilitätsanalyse\n");
            if (lastMonths != null && !lastMonths.isEmpty()) {
                output.append("Gefundene Werte:\n");
                for (String month : lastMonths) {
                    output.append(month).append("\n");
                }
            }
        }
        output.append("********************************");

        // Write to txt file
        Files.writeString(txtFile, output.toString());
        logger.info("Successfully converted " + htmlFile.getFileName() + " to " + txtFile.getFileName());
    }
}