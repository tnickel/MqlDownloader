package calculators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import utils.HtmlDatabase;

/**
 * Klasse zur Berechnung von MPDD-Werten (Month Profit Divided by Drawdown)
 * Diese Klasse kapselt alle Berechnungslogik f\u00fcr MPDD-Werte und kann 
 * in verschiedenen Teilen der Anwendung wiederverwendet werden.
 */
public class MPDDCalculator {
    private static final Logger LOGGER = Logger.getLogger(MPDDCalculator.class.getName());
    
    private final HtmlDatabase htmlDatabase;
    
    /**
     * Konstruktor mit HtmlDatabase-Dependency
     * @param htmlDatabase Die HtmlDatabase-Instanz f\u00fcr Datenzugriff
     */
    public MPDDCalculator(HtmlDatabase htmlDatabase) {
        this.htmlDatabase = htmlDatabase;
    }
    
    /**
     * Berechnet den 3-Monats-MPDD-Wert f\u00fcr einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Der berechnete 3MPDD-Wert
     */
    public double calculate3MPDD(String fileName) {
        return calculateMPDD(fileName, 3);
    }
    
    /**
     * Berechnet den MPDD-Wert f\u00fcr einen Provider \u00fcber n Monate
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der Monate (3, 6, 9, 12)
     * @return Der berechnete MPDD-Wert
     */
    public double calculateMPDD(String fileName, int months) {
        if (months <= 0) {
            LOGGER.warning("Ung\u00fcltige Monatsanzahl: " + months);
            return 0.0;
        }
        
        try {
            // 1. Durchschnittlichen monatlichen Profit der letzten n Monate berechnen
            double averageMonthlyProfit = calculateAverageMonthlyProfit(fileName, months);
            
            // 2. Equity Drawdown holen
            double equityDrawdown = getEquityDrawdown(fileName);
            
            // 3. MPDD berechnen (Durchschnittsprofit / Equity Drawdown)
            double mpdd = calculateFinalMPDD(averageMonthlyProfit, equityDrawdown);
            
            LOGGER.fine(String.format("MPDD%d f\u00fcr %s berechnet: %.4f (Profit: %.2f%%, Drawdown: %.2f%%)", 
                       months, fileName, mpdd, averageMonthlyProfit, equityDrawdown));
            
            return mpdd;
            
        } catch (Exception e) {
            LOGGER.severe("Fehler bei MPDD-Berechnung f\u00fcr " + fileName + ": " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Berechnet den durchschnittlichen monatlichen Profit der letzten n Monate
     * (ohne den aktuellen Monat)
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der zu ber\u00fccksichtigenden Monate
     * @return Durchschnittlicher monatlicher Profit in Prozent
     */
    public double calculateAverageMonthlyProfit(String fileName, int months) {
        Map<String, Double> monthlyProfits = getMonthlyProfitPercentages(fileName);
        if (monthlyProfits.isEmpty()) {
            LOGGER.warning("Keine monatlichen Profite f\u00fcr " + fileName + " gefunden");
            return 0.0;
        }
        
        List<String> sortedMonths = new ArrayList<>(monthlyProfits.keySet());
        if (sortedMonths.isEmpty()) {
            LOGGER.warning("Keine sortierten Monate f\u00fcr " + fileName + " verf\u00fcgbar");
            return 0.0;
        }
        
        // Absteigend sortieren (neueste Monate zuerst)
        sortedMonths.sort((a, b) -> b.compareTo(a));
        
        // Debug-Ausgabe
        LOGGER.fine("Berechne " + months + "-Monats-Durchschnitt f\u00fcr " + fileName);
        LOGGER.fine("Verf\u00fcgbare Monate: " + sortedMonths);
        
        // \u00dcberpr\u00fcfe, ob gen\u00fcgend Monate vorhanden sind
        if (sortedMonths.size() < 2) { // Mindestens aktueller Monat + 1 weiterer
            LOGGER.warning("Zu wenige Monate f\u00fcr " + fileName + ": " + sortedMonths.size());
            return 0.0;
        }
        
        // Nach Abzug des aktuellen Monats verf\u00fcgbare Monate
        int availableMonths = sortedMonths.size() - 1; // -1 f\u00fcr aktuellen Monat
        
        // F\u00fcr 3MPDD: Verwende verf\u00fcgbare Monate bis maximal 3
        int monthsToUse;
        if (months == 3) {
            monthsToUse = Math.min(months, availableMonths);
            if (monthsToUse <= 0) {
                LOGGER.warning("Keine Monate f\u00fcr 3MPDD verf\u00fcgbar");
                return 0.0;
            }
        } else {
            // F\u00fcr 6, 9 und 12 MPDD: Nur berechnen wenn gen\u00fcgend Monate verf\u00fcgbar
            int requiredMonths = months + 1; // +1 weil aktueller Monat nicht ber\u00fccksichtigt wird
            if (sortedMonths.size() < requiredMonths) {
                LOGGER.warning("Nicht genug Monate f\u00fcr " + months + "-MPDD: " + 
                              sortedMonths.size() + "/" + requiredMonths);
                return 0.0;
            }
            monthsToUse = months;
        }
        
        // Summe der Profite der letzten n Monate (ohne aktuellen Monat)
        double sum = 0.0;
        for (int i = 0; i < monthsToUse; i++) {
            String month = sortedMonths.get(i + 1); // +1 um aktuellen Monat zu \u00fcberspringen
            double profit = monthlyProfits.get(month);
            sum += profit;
            LOGGER.fine("Monat " + month + ": " + profit + "%");
        }
        
        // Durchschnitt berechnen
        double average = sum / monthsToUse;
        LOGGER.fine("Durchschnitt \u00fcber " + monthsToUse + " Monate: " + average + "%");
        
        return average;
    }
    
    /**
     * Holt die monatlichen Profit-Prozents\u00e4tze f\u00fcr einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Map mit Jahr/Monat als Schl\u00fcssel und Profit-Prozentsatz als Wert
     */
    public Map<String, Double> getMonthlyProfitPercentages(String fileName) {
        if (htmlDatabase != null) {
            return htmlDatabase.getMonthlyProfitPercentages(fileName);
        } else {
            LOGGER.warning("HtmlDatabase ist null - kann monatliche Profite nicht laden");
            return new HashMap<>();
        }
    }
    
    /**
     * Holt den Equity Drawdown f\u00fcr einen Provider
     * 
     * @param fileName Name der Provider-Datei
     * @return Equity Drawdown in Prozent
     */
    public double getEquityDrawdown(String fileName) {
        if (htmlDatabase != null) {
            double drawdown = htmlDatabase.getEquityDrawdown(fileName);
            
            // Stelle sicher, dass der Drawdown-Wert positiv ist
            if (drawdown <= 0.0) {
                LOGGER.warning("Equity Drawdown ist 0 oder negativ: " + drawdown + " f\u00fcr " + fileName);
                return 1.0; // Standardwert, um Division durch Null zu vermeiden
            }
            
            return drawdown;
        } else {
            LOGGER.warning("HtmlDatabase ist null - kann Equity Drawdown nicht laden");
            return 1.0; // Standardwert
        }
    }
    
    /**
     * F\u00fchrt die finale MPDD-Berechnung durch (Durchschnittsprofit / Equity Drawdown)
     * 
     * @param averageProfit Durchschnittlicher monatlicher Profit in Prozent
     * @param equityDrawdown Equity Drawdown in Prozent
     * @return Der berechnete MPDD-Wert
     */
    public double calculateFinalMPDD(double averageProfit, double equityDrawdown) {
        if (equityDrawdown <= 0.0) {
            LOGGER.warning("Equity Drawdown ist 0 oder negativ: " + equityDrawdown);
            return 0.0;
        }
        
        double mpdd = averageProfit / equityDrawdown;
        return mpdd;
    }
    
    /**
     * Erstellt detaillierte Berechnungsdetails f\u00fcr Debugging oder Tooltips
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der Monate
     * @return Formatierte Berechnungsdetails als String
     */
    public String getCalculationDetails(String fileName, int months) {
        StringBuilder details = new StringBuilder();
        details.append(String.format("%d-Monats-MPDD Berechnung f\u00fcr %s:\n\n", months, fileName));
        
        try {
            Map<String, Double> monthlyProfits = getMonthlyProfitPercentages(fileName);
            if (monthlyProfits.isEmpty()) {
                details.append("Keine monatlichen Profit-Daten verf\u00fcgbar.\n");
                return details.toString();
            }
            
            List<String> sortedMonths = new ArrayList<>(monthlyProfits.keySet());
            sortedMonths.sort((a, b) -> b.compareTo(a));
            
            details.append("Verwendete Monate (vor aktuellem Monat):\n");
            
            double sum = 0.0;
            int monthsToUse = Math.min(months, sortedMonths.size() - 1);
            
            for (int i = 0; i < monthsToUse; i++) {
                String month = sortedMonths.get(i + 1); // +1 um aktuellen Monat zu \u00fcberspringen
                double profit = monthlyProfits.get(month);
                sum += profit;
                details.append(String.format("- %s: %.2f%%\n", month, profit));
            }
            
            double averageProfit = sum / monthsToUse;
            double equityDrawdown = getEquityDrawdown(fileName);
            double mpdd = calculateFinalMPDD(averageProfit, equityDrawdown);
            
            details.append(String.format("\nDurchschnitt \u00fcber %d Monate: %.2f%%\n", monthsToUse, averageProfit));
            details.append(String.format("Equity Drawdown: %.2f%%\n", equityDrawdown));
            details.append(String.format("MPDD-Wert: %.4f\n", mpdd));
            
        } catch (Exception e) {
            details.append("Fehler bei der Berechnung: ").append(e.getMessage()).append("\n");
        }
        
        return details.toString();
    }
    
    /**
     * Erstellt einen HTML-formatierten Tooltip f\u00fcr die MPDD-Berechnung
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der Monate
     * @return HTML-formatierter Tooltip-String
     */
    public String getHTMLTooltip(String fileName, int months) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html><div style='padding: 5px; white-space: nowrap;'>");
        tooltip.append(String.format("<b>%d-Monats-MPDD Berechnung:</b><br><br>", months));
        
        try {
            Map<String, Double> monthlyProfits = getMonthlyProfitPercentages(fileName);
            if (monthlyProfits.isEmpty()) {
                tooltip.append("Keine Berechnungsdetails verf\u00fcgbar<br>");
                tooltip.append("</div></html>");
                return tooltip.toString();
            }
            
            List<String> sortedMonths = new ArrayList<>(monthlyProfits.keySet());
            sortedMonths.sort((a, b) -> b.compareTo(a));
            
            tooltip.append("<b>Verwendete Monate:</b><br>");
            
            double sum = 0.0;
            int monthsToUse = Math.min(months, sortedMonths.size() - 1);
            
            for (int i = 0; i < monthsToUse; i++) {
                String month = sortedMonths.get(i + 1); // +1 um aktuellen Monat zu \u00fcberspringen
                double profit = monthlyProfits.get(month);
                sum += profit;
                tooltip.append(String.format("&nbsp;&nbsp;- %s: %.2f%%<br>", month, profit));
            }
            
            double averageProfit = sum / monthsToUse;
            double equityDrawdown = getEquityDrawdown(fileName);
            double mpdd = calculateFinalMPDD(averageProfit, equityDrawdown);
            
            tooltip.append("<br>");
            tooltip.append(String.format("<b>Durchschnitt:</b> %.2f%%<br>", averageProfit));
            tooltip.append(String.format("<b>Equity Drawdown:</b> %.2f%%<br>", equityDrawdown));
            tooltip.append(String.format("<b>MPDD-Wert:</b> %.4f<br>", mpdd));
            
        } catch (Exception e) {
            tooltip.append("Fehler bei der Berechnung<br>");
        }
        
        tooltip.append("</div></html>");
        return tooltip.toString();
    }
    
    /**
     * Validiert, ob f\u00fcr einen Provider gen\u00fcgend Daten f\u00fcr eine MPDD-Berechnung vorhanden sind
     * 
     * @param fileName Name der Provider-Datei
     * @param months Anzahl der ben\u00f6tigten Monate
     * @return true wenn gen\u00fcgend Daten vorhanden sind
     */
    public boolean hasEnoughDataForCalculation(String fileName, int months) {
        try {
            Map<String, Double> monthlyProfits = getMonthlyProfitPercentages(fileName);
            if (monthlyProfits.isEmpty()) {
                return false;
            }
            
            // F\u00fcr 3MPDD reicht schon 1 Monat (au\u00dfer aktuellem)
            if (months == 3) {
                return monthlyProfits.size() >= 2; // Aktueller + mindestens 1 weiterer
            } else {
                // F\u00fcr andere MPDD-Werte m\u00fcssen gen\u00fcgend Monate vorhanden sein
                return monthlyProfits.size() >= (months + 1); // months + aktueller Monat
            }
            
        } catch (Exception e) {
            LOGGER.warning("Fehler bei Datenvalidierung f\u00fcr " + fileName + ": " + e.getMessage());
            return false;
        }
    }
}