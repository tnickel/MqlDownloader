package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataExtractor {
    private static final Logger logger = LogManager.getLogger(DataExtractor.class);
    private final HtmlContentCache contentCache;
    private final ChartDataExtractor chartExtractor;
    
    // Eigenschaften des DataExtractor
    private double balance;
    private double equityDrawdownGraphic;
    private double equityDrawdown;
    private double avr3MonthProfit;
    private List<ChartPoint> drawdownChartData;
    
    public DataExtractor(HtmlContentCache contentCache, ChartDataExtractor chartExtractor) {
        this.contentCache = contentCache;
        this.chartExtractor = chartExtractor;
        this.balance = 0.0;
        this.equityDrawdownGraphic = 0.0;
        this.equityDrawdown = 0.0;
        this.avr3MonthProfit = 0.0;
        this.drawdownChartData = new ArrayList<>();
    }
    
    // Getter für Balance
    public double getBalance(String fileName) {
        try {
            String htmlContent = contentCache.getHtmlContent(fileName);
            if (htmlContent == null) return 0.0;
            
            Pattern pattern = Pattern.compile("Balance:\\s*(?:€|\\$)?\\s*([\\d,.]+)");
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String balanceStr = matcher.group(1).replaceAll("[^\\d.]", "");
                balance = Double.parseDouble(balanceStr);
                return balance;
            }
        } catch (Exception e) {
            logger.error("Fehler beim Extrahieren der Balance für " + fileName, e);
        }
        return 0.0;
    }
    
    // Getter für Equity Drawdown aus der Grafik
    public double getEquityDrawdownGraphic(String fileName) {
        try {
            List<ChartPoint> data = chartExtractor.getDrawdownChartData(fileName);
            if (data != null && !data.isEmpty()) {
                double maxDrawdown = data.stream()
                    .mapToDouble(ChartPoint::getValue)
                    .min()
                    .orElse(0.0);
                equityDrawdownGraphic = Math.abs(maxDrawdown);
                return equityDrawdownGraphic;
            }
        } catch (Exception e) {
            logger.warn("No drawdown chart data found for " + fileName);
        }
        return 0.0;
    }
    
    // Getter für Equity Drawdown aus der Texttabelle
    public double getEquityDrawdown(String fileName) {
        try {
            String htmlContent = contentCache.getHtmlContent(fileName);
            if (htmlContent == null) return 0.0;
            
            Pattern pattern = Pattern.compile("Drawdown(?:\\(%\\))?:?\\s*([\\d,.]+)");
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                return equityDrawdown;
            }
        } catch (Exception e) {
            logger.error("Fehler beim Extrahieren des Equity Drawdown für " + fileName, e);
        }
        return 0.0;
    }
    
    // Getter für durchschnittlichen 3-Monats-Profit
    public double getAvr3MonthProfit(String fileName, MonthDetailsExtractor monthExtractor) {
        try {
            List<String> lastMonths = monthExtractor.getLastThreeMonthsDetails(fileName);
            if (lastMonths.isEmpty()) return 0.0;
            
            double sum = 0.0;
            for (String month : lastMonths) {
                String[] parts = month.split(":");
                if (parts.length > 1) {
                    String valueStr = parts[1].trim().replace(",", ".");
                    try {
                        sum += Double.parseDouble(valueStr);
                    } catch (NumberFormatException e) {
                        logger.warn("Ungültiger Monatswert: " + valueStr);
                    }
                }
            }
            avr3MonthProfit = lastMonths.isEmpty() ? 0.0 : sum / lastMonths.size();
            return avr3MonthProfit;
        } catch (Exception e) {
            logger.error("Fehler beim Berechnen des durchschnittlichen 3-Monats-Profits für " + fileName, e);
        }
        return 0.0;
    }
    
    // Methode zum Schreiben des Equity Drawdown in eine Datei
    public void writeEquityDrawdownToFile(String fileName, String outputFilePath) {
        try {
            double drawdown = getEquityDrawdown(fileName);
            Path outputPath = Paths.get(outputFilePath);
            Files.writeString(outputPath, String.format("Equity Drawdown: %.2f%%", drawdown));
            logger.info("Equity Drawdown in Datei geschrieben: " + outputFilePath);
        } catch (IOException e) {
            logger.error("Fehler beim Schreiben des Equity Drawdown in Datei: " + outputFilePath, e);
        }
    }
    
    // Getter und Setter für Eigenschaften
    public void setBalance(double balance) {
        this.balance = balance;
    }
    
    public double getBalance() {
        return balance;
    }
    
    public void setEquityDrawdownGraphic(double equityDrawdownGraphic) {
        this.equityDrawdownGraphic = equityDrawdownGraphic;
    }
    
    public double getEquityDrawdownGraphic() {
        return equityDrawdownGraphic;
    }
    
    public void setEquityDrawdown(double equityDrawdown) {
        this.equityDrawdown = equityDrawdown;
    }
    
    public double getEquityDrawdown() {
        return equityDrawdown;
    }
    
    public void setAvr3MonthProfit(double avr3MonthProfit) {
        this.avr3MonthProfit = avr3MonthProfit;
    }
    
    public double getAvr3MonthProfit() {
        return avr3MonthProfit;
    }
    
    public void setDrawdownChartData(List<ChartPoint> drawdownChartData) {
        this.drawdownChartData = drawdownChartData;
    }
    
    public List<ChartPoint> getDrawdownChartData() {
        return drawdownChartData;
    }
}