package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class StabilityCalculator {
    private static final Logger LOGGER = Logger.getLogger(StabilityCalculator.class.getName());
    private final MonthDetailsExtractor monthExtractor;
    private final HtmlContentCache contentCache;
    
    public StabilityCalculator(MonthDetailsExtractor monthExtractor, HtmlContentCache contentCache) {
        this.monthExtractor = monthExtractor;
        this.contentCache = contentCache;
    }
    
    public double getStabilitaetswert(String fileName) {
        return getStabilitaetswertDetails(fileName).getValue();
    }
    
    public StabilityResult getStabilitaetswertDetails(String fileName) {
        if (contentCache.hasStabilityResult(fileName)) {
            return contentCache.getStabilityResultFromCache(fileName);
        }
        
        StringBuilder details = new StringBuilder();
        List<String> lastMonths = monthExtractor.getLastThreeMonthsDetails(fileName);
        List<Double> profitValues = new ArrayList<>();
        try {
            for (String monthDetail : lastMonths) {
                String valueStr = monthDetail.split(":")[1].trim().replace("%", "").replace(",", ".");
                double profit = Double.parseDouble(valueStr);
                profitValues.add(profit);
            }
            details.append("Verwendete Monatswerte:<br>");
            for (String month : lastMonths) {
                details.append("- ").append(month).append("<br>");
            }
            if (profitValues.size() >= 2) {
                double mean = profitValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                details.append("<br>Mittelwert: ").append(String.format("%.2f%%", mean)).append("<br>");
                double variance = profitValues.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
                double stdDeviation = Math.sqrt(variance);
                details.append("Standardabweichung: ").append(String.format("%.2f", stdDeviation)).append("<br>");
                double relativeStdDev = Math.abs(mean) < 0.0001 ? 1.0 : stdDeviation / (Math.abs(mean) + 0.0001);
                details.append("Relative Standardabweichung: ").append(String.format("%.2f", relativeStdDev)).append("<br>");
                double baseStability = Math.max(1.0, 100.0 * (1.0 - relativeStdDev));
                details.append("Basis-Stabilitätswert: ").append(String.format("%.2f", baseStability)).append("<br>");
                double dataQualityFactor = profitValues.size() / 3.0;
                details.append("Datenqualitätsfaktor: ").append(String.format("%.2f", dataQualityFactor)).append("<br>");
                double finalStability = Math.max(1.0, Math.min(100.0, baseStability * (0.7 + 0.3 * dataQualityFactor)));
                StabilityResult result = new StabilityResult(finalStability, details.toString());
                contentCache.cacheStabilityResult(fileName, result);
                return result;
            }
            StabilityResult result = new StabilityResult(1.0, "Nicht genügend Monatswerte verfügbar<br>Gefundene Werte:<br>" + String.join("<br>", lastMonths));
            contentCache.cacheStabilityResult(fileName, result);
            return result;
        } catch (Exception e) {
            StabilityResult result = new StabilityResult(1.0, "Fehler bei der Berechnung: " + e.getMessage());
            contentCache.cacheStabilityResult(fileName, result);
            return result;
        }
    }
}