package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataExtractor {
    private static final Logger LOGGER = Logger.getLogger(DataExtractor.class.getName());
    
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
        "<div class=\"s-list-info__item\">\\s*" +
        "<div class=\"s-list-info__label\">(Balance|Kontostand):\\s*</div>\\s*" +
        "<div class=\"s-list-info__value\">([\\d\\s\\.]+)\\s*[A-Z]{3}</div>\\s*" +
        "</div>"
    );
    
    private static final Pattern DRAWNDOWN_PATTERN = Pattern.compile(
        "Maximaler[^%]*?([0-9]+(?:[.,][0-9]+)?)%",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private final HtmlContentCache contentCache;
    private final ChartDataExtractor chartExtractor;
    
    public DataExtractor(HtmlContentCache contentCache, ChartDataExtractor chartExtractor) {
        this.contentCache = contentCache;
        this.chartExtractor = chartExtractor;
    }
    
    public double getBalance(String fileName) {
        if (contentCache.hasBalance(fileName)) {
            return contentCache.getBalanceFromCache(fileName);
        }
        String htmlContent = contentCache.getHtmlContent(fileName);
        if (htmlContent == null) return 0.0;
        Matcher matcher = BALANCE_PATTERN.matcher(htmlContent);
        if (matcher.find()) {
            String balanceStr = matcher.group(2).replaceAll("\\s+", "").replace(",", ".");
            try {
                double balance = Double.parseDouble(balanceStr);
                contentCache.cacheBalance(fileName, balance);
                return balance;
            } catch (NumberFormatException e) {
                LOGGER.warning("Could not parse balance number: " + balanceStr);
            }
        }
        LOGGER.warning("No balance value found for " + fileName);
        return 0.0;
    }
    
    public double getEquityDrawdownGraphic(String fileName) {
        if (contentCache.hasEquityDrawdownGraphic(fileName)) {
            return contentCache.getEquityDrawdownGraphicFromCache(fileName);
        }
        
        // Get the drawdown chart data from the SVG
        List<ChartPoint> chartData = chartExtractor.getDrawdownChartData(fileName);
        if (chartData.isEmpty()) {
            LOGGER.warning("No drawdown chart data found for " + fileName);
            return 0.0;
        }
        
        // Find the maximum drawdown (highest percentage value, since drawdown is reported as a positive percentage)
        double maxDrawdown = 0.0; // Initialize to 0, we'll look for the maximum value
        for (ChartPoint point : chartData) {
            double drawdownValue = point.getValue(); // Percentage value from ChartPoint
            // Drawdown is the maximum drop, so we take the highest value (maximum percentage)
            if (drawdownValue > maxDrawdown) {
                maxDrawdown = drawdownValue;
            }
        }
        
        // Ensure the value is positive for reporting (already positive, but for consistency)
        maxDrawdown = Math.abs(maxDrawdown);
        
        // Cache the result
        contentCache.cacheEquityDrawdownGraphic(fileName, maxDrawdown);
        return maxDrawdown;
    }
    
    public double getEquityDrawdown(String fileName) {
        // Cache-Check
        if (contentCache.hasEquityDrawdown(fileName)) {
            return contentCache.getEquityDrawdownFromCache(fileName);
        }
        
        String htmlContent = contentCache.getHtmlContent(fileName);
        if (htmlContent == null) {
            LOGGER.warning("HTML content is null for " + fileName);
            return 0.0;
        }
        
        Matcher matcher = DRAWNDOWN_PATTERN.matcher(htmlContent);
        if (matcher.find()) {
            String drawdownStr = matcher.group(1).replace(",", ".");
            try {
                double drawdown = Double.parseDouble(drawdownStr);
                contentCache.cacheEquityDrawdown(fileName, drawdown);
                return drawdown;
            } catch (NumberFormatException e) {
                LOGGER.warning("Could not parse drawdown number: " + drawdownStr);
            }
        }
        
        LOGGER.warning("No drawdown value found for " + fileName);
        return 0.0;
    }
    
    public double getAvr3MonthProfit(String fileName, MonthDetailsExtractor monthExtractor) {
        if (contentCache.hasAverageProfit(fileName)) {
            return contentCache.getAverageProfitFromCache(fileName);
        }
        List<String> lastMonthsDetails = monthExtractor.getLastThreeMonthsDetails(fileName);
        if (lastMonthsDetails.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (String detail : lastMonthsDetails) {
            try {
                String valueStr = detail.split(":")[1].trim().replace("%", "").replace(",", ".");
                double profit = Double.parseDouble(valueStr);
                sum += profit;
                count++;
            } catch (NumberFormatException e) {
                LOGGER.warning("Error parsing profit value from: " + detail);
            }
        }
        if (count == 0) return 0.0;
        double average = sum / count;
        contentCache.cacheAverageProfit(fileName, average);
        return average;
    }
    
    public void writeEquityDrawdownToFile(String fileName, String outputFilePath) {
        double equityDrawdownGraphic = getEquityDrawdownGraphic(fileName);
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath, true))) {
            writer.println("File: " + fileName + " - EquityDrawdownGraphic: " + String.format("%.2f%%", equityDrawdownGraphic));
            LOGGER.info("EquityDrawdownGraphic für " + fileName + " wurde in " + outputFilePath + " geschrieben: " + equityDrawdownGraphic + "%");
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Schreiben in die Datei " + outputFilePath + ": " + e.getMessage());
        }
    }
}