package utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Adapter-Klasse, die HtmlParser-Funktionalität für MPDDCalculator bereitstellt
 */
public class HtmlDatabase {
    private static final Logger LOGGER = Logger.getLogger(HtmlDatabase.class.getName());
    
    private final HtmlParser htmlParser;
    
    public HtmlDatabase(HtmlParser htmlParser) {
        this.htmlParser = htmlParser;
    }
    
    /**
     * Holt die monatlichen Profit-Prozentsätze für einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Map mit Jahr/Monat als Schlüssel und Profit-Prozentsatz als Wert
     */
    public Map<String, Double> getMonthlyProfitPercentages(String fileName) {
        Map<String, Double> monthlyProfits = new HashMap<>();
        
        try {
            // Alle Monatsdetails vom HtmlParser holen
        	 List<String> allMonthsDetails  = htmlParser.getAllMonthsDetails(fileName);
            
            if (allMonthsDetails.isEmpty()) {
                LOGGER.warning("Keine Monatsdetails für " + fileName + " gefunden");
                return monthlyProfits;
            }
            
            // Details parsen und in Map umwandeln
            for (String monthDetail : allMonthsDetails) {
                String[] parts = monthDetail.split(":");
                if (parts.length == 2) {
                    try {
                        String yearMonth = parts[0].trim();
                        double profitPercentage = Double.parseDouble(parts[1].trim());
                        monthlyProfits.put(yearMonth, profitPercentage);
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Could not parse profit percentage for entry: " + monthDetail);
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Laden der monatlichen Profite für " + fileName + ": " + e.getMessage());
        }
        
        return monthlyProfits;
    }
    
    /**
     * Holt den Equity Drawdown für einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Equity Drawdown in Prozent
     */
    public double getEquityDrawdown(String fileName) {
        try {
            double drawdown = htmlParser.getEquityDrawdown(fileName);
            
            // Stelle sicher, dass der Wert positiv ist (wir erwarten einen positiven Prozentsatz)
            if (drawdown <= 0.0) {
                LOGGER.warning("EquityDrawdown ist 0 oder negativ: " + drawdown + " für " + fileName);
                return 1.0; // Standardwert, um Division durch Null zu vermeiden
            }
            
            return drawdown;
        } catch (Exception e) {
            LOGGER.warning("Could not get equity drawdown for " + fileName + ": " + e.getMessage());
            return 1.0; // Standardwert
        }
    }
    
    /**
     * Holt die Balance für einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Balance als double-Wert
     */
    public double getBalance(String fileName) {
        try {
            return htmlParser.getBalance(fileName);
        } catch (Exception e) {
            LOGGER.warning("Could not get balance for " + fileName + ": " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Holt den grafischen Equity Drawdown für einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Equity Drawdown Graphic als double-Wert
     */
    public double getEquityDrawdownGraphic(String fileName) {
        try {
            return htmlParser.getEquityDrawdownGraphic(fileName);
        } catch (Exception e) {
            LOGGER.warning("Could not get equity drawdown graphic for " + fileName + ": " + e.getMessage());
            return 0.0;
        }
    }
}