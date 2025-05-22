package utils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Klasse für grundlegende Datenwerte die vom MPDD Calculator benötigt werden.
 * Diese Klasse enthält nur die essentiellen Methoden ohne komplexe Berechnungen.
 */
public class BasicDataProvider {
    private static final Logger LOGGER = Logger.getLogger(BasicDataProvider.class.getName());
    
    private final FileDataReader fileDataReader;
    
    public BasicDataProvider(FileDataReader fileDataReader) {
        this.fileDataReader = fileDataReader;
    }
    
    /**
     * Holt die monatlichen Profit-Prozentsätze für einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Map mit Jahr/Monat als Schlüssel und Profit-Prozentsatz als Wert
     */
    public Map<String, Double> getMonthlyProfitPercentages(String fileName) {
        Map<String, String> data = fileDataReader.getFileData(fileName);
        Map<String, Double> monthlyProfits = new HashMap<>();
        
        if (data.isEmpty()) {
            LOGGER.warning("Keine Daten für " + fileName + " gefunden");
            return monthlyProfits;
        }
        
        String profitData = data.get("MonthProfitProz");
        if (profitData == null || profitData.trim().isEmpty()) {
            return monthlyProfits;
        }
        
        String[] monthEntries = profitData.split(",");
        for (String entry : monthEntries) {
            String[] parts = entry.split("=");
            if (parts.length == 2) {
                try {
                    String yearMonth = parts[0].trim();
                    double profitPercentage = Double.parseDouble(parts[1].trim());
                    monthlyProfits.put(yearMonth, profitPercentage);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Could not parse profit percentage for entry: " + entry);
                }
            }
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
        Map<String, String> data = fileDataReader.getFileData(fileName);
        if (data.isEmpty()) {
            LOGGER.warning("Keine Daten für " + fileName + " gefunden");
            // Standardwert zurückgeben, um Division durch Null zu vermeiden
            return 1.0;
        }
        
        String drawdownStr = data.getOrDefault("EquityDrawdown", "0,00")
                                .replace(",", ".")
                                .replace(" ", "");
        try {
            double value = Double.parseDouble(drawdownStr);
            
            // Stelle sicher, dass der Wert positiv ist (wir erwarten einen positiven Prozentsatz)
            if (value <= 0.0) {
                LOGGER.warning("EquityDrawdown ist 0 oder negativ: " + value + " für " + fileName);
                return 1.0; // Standardwert, um Division durch Null zu vermeiden
            }
            
            return value;
        } catch (NumberFormatException e) {
            LOGGER.warning("Could not parse equity drawdown: " + drawdownStr);
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
        Map<String, String> data = fileDataReader.getFileData(fileName);
        if (data.isEmpty()) {
            LOGGER.warning("Keine Daten für " + fileName + " gefunden");
            return 0.0;
        }
        
        String balanceStr = data.getOrDefault("Balance", "0,00")
                               .replace(",", ".")
                               .replace(" ", "");
        try {
            return Double.parseDouble(balanceStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("Could not parse balance: " + balanceStr);
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
        Map<String, String> data = fileDataReader.getFileData(fileName);
        if (data.isEmpty()) {
            LOGGER.warning("Keine Daten für " + fileName + " gefunden");
            return 0.0;
        }
        
        String drawdownGraphicStr = data.getOrDefault("MaxDDGraphic", "0")
                                   .replace(",", ".")
                                   .replace(" ", "");
        try {
            return Double.parseDouble(drawdownGraphicStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("Could not parse max drawdown graphic: " + drawdownGraphicStr);
            return 0.0;
        }
    }
    
    /**
     * Holt den 3MPDD-Wert direkt aus dem .txt-File
     * 
     * @param fileName Name der Provider-Datei
     * @return 3MPDD-Wert als double
     */
    public double get3MPDD(String fileName) {
        Map<String, String> data = fileDataReader.getFileData(fileName);
        if (data.isEmpty()) {
            LOGGER.warning("Keine Daten für " + fileName + " gefunden");
            return 0.0;
        }
        
        String mpddStr = data.getOrDefault("3MPDD", "0,00")
                            .replace(",", ".")
                            .replace(" ", "");
        try {
            return Double.parseDouble(mpddStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("Could not parse 3MPDD value: " + mpddStr + " for file: " + fileName);
            return 0.0;
        }
    }
    
    /**
     * Holt MPDD-Werte für verschiedene Zeiträume (6, 9, 12 Monate)
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der Monate (6, 9, 12)
     * @return MPDD-Wert als double
     */
    public double getMPDD(String fileName, int months) {
        Map<String, String> data = fileDataReader.getFileData(fileName);
        if (data.isEmpty()) {
            LOGGER.warning("Keine Daten für " + fileName + " gefunden");
            return 0.0;
        }
        
        String key = months + "MPDD";
        String mpddStr = data.getOrDefault(key, "0,00")
                            .replace(",", ".")
                            .replace(" ", "");
        try {
            return Double.parseDouble(mpddStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("Could not parse " + key + " value: " + mpddStr + " for file: " + fileName);
            return 0.0;
        }
    }
    
    /**
     * Erstellt einen einfachen Tooltip für MPDD-Werte 
     * (Da detaillierte Berechnung nicht mehr stattfindet)
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der Monate
     * @return HTML-formatierter Tooltip
     */
    public String getMPDDTooltip(String fileName, int months) {
        double mpddValue;
        if (months == 3) {
            mpddValue = get3MPDD(fileName);
        } else {
            mpddValue = getMPDD(fileName, months);
        }
        
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html><div style='padding: 5px; white-space: nowrap;'>");
        tooltip.append(String.format("<b>%d-Monats-MPDD:</b><br><br>", months));
        tooltip.append(String.format("<b>MPDD-Wert:</b> %.4f<br>", mpddValue));
        tooltip.append("<br><i>Wert wird direkt aus der Datei gelesen</i>");
        tooltip.append("</div></html>");
        
        return tooltip.toString();
    }
}