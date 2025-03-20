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
    
    public double getBalance(String fileName) {
        try {
            String htmlContent = contentCache.getHtmlContent(fileName);
            if (htmlContent == null) {
                String errorMessage = "HTML-Inhalt konnte nicht geladen werden für Datei: " + fileName;
                logger.error(errorMessage);
                showErrorAndExit(errorMessage);
                return 0.0; // Diese Zeile wird nie erreicht
            }
            
            // HTML mit JSoup parsen
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlContent);
            
            // Nach dem Element mit dem Label "Kontostand:" suchen
            org.jsoup.select.Elements elements = doc.select("div.s-list-info__item:contains(Kontostand:) .s-list-info__value");
            
            if (!elements.isEmpty()) {
                String balanceStr = elements.first().text();
                logger.info("Extrahierter Kontostand: " + balanceStr);
                
                // Verbesserte Bereinigung des Werts
                balanceStr = balanceStr.replaceAll("[^0-9.,]", "").trim().replace(",", ".");
                // Entfernt auch Leerzeichen zwischen Zahlen
                balanceStr = balanceStr.replaceAll("\\s+", "");
                
                balance = Double.parseDouble(balanceStr);
                return balance;
            } else {
                // Alternative Suche nach "Balance:" falls "Kontostand:" nicht gefunden wurde
                elements = doc.select("div.s-list-info__item:contains(Balance:) .s-list-info__value");
                
                if (!elements.isEmpty()) {
                    String balanceStr = elements.first().text();
                    logger.info("Extrahierter Balance: " + balanceStr);
                    
                    // Verbesserte Bereinigung des Werts
                    balanceStr = balanceStr.replaceAll("[^0-9.,]", "").trim().replace(",", ".");
                    // Entfernt auch Leerzeichen zwischen Zahlen
                    balanceStr = balanceStr.replaceAll("\\s+", "");
                    
                    balance = Double.parseDouble(balanceStr);
                    return balance;
                } else {
                    String errorMessage = "Balance/Kontostand konnte nicht extrahiert werden für Datei: " + fileName;
                    logger.error(errorMessage);
                    showErrorAndExit(errorMessage);
                    return.0; // Diese Zeile wird nie erreicht
                }
            }
        } catch (Exception e) {
            String errorMessage = "Fehler beim Extrahieren der Balance für " + fileName + ": " + e.getMessage();
            logger.error(errorMessage, e);
            showErrorAndExit(errorMessage);
            return 0.0; // Diese Zeile wird nie erreicht
        }
    }

    // Hilfsmethode zum Anzeigen einer Fehlermeldung und Beenden des Programms
    private void showErrorAndExit(String errorMessage) {
        // Popup-Fenster anzeigen (blockierend)
        javax.swing.JOptionPane.showMessageDialog(
            null,
            errorMessage,
            "Extraktionsfehler",
            javax.swing.JOptionPane.ERROR_MESSAGE
        );
        
        // Prozess beenden
        System.exit(1);
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
    
    public double getEquityDrawdown(String fileName) {
        try {
            String htmlContent = contentCache.getHtmlContent(fileName);
            if (htmlContent == null) return 0.0;

            // Angepasster Ausdruck, der sowohl Werte mit als auch ohne Dezimalstellen erfasst
            Pattern pattern = Pattern.compile("Maximaler Rückgang:</tspan><tspan[^>]*>(\\d+(?:\\.\\d+)?)%</tspan>");
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1);
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert: " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Noch flexiblerer Ausdruck für ähnliche Formate
            pattern = Pattern.compile("Maximaler Rückgang:(?:</tspan>)?(?:<[^>]*>)?(\\d+(?:[,.]\\d+)?)%");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (mit allgemeinerem Pattern): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Debug-Ausgabe für Fehlerbehebung
            logger.debug("HTML-Inhalt um 'Maximaler Rückgang': " + extractContextAroundKeyword(htmlContent, "Maximaler Rückgang"));
            
            // Wenn kein Equity Drawdown gefunden wurde
            String errorMessage = "Equity Drawdown konnte nicht extrahiert werden für Datei: " + fileName;
            logger.error(errorMessage);
            
            // Popup-Fenster anzeigen (blockierend)
            javax.swing.JOptionPane.showMessageDialog(
                null,
                errorMessage,
                "Equity Drawdown Extraktionsfehler",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
            
            // Prozess beenden
            System.exit(1);
            return 0.0; // Diese Zeile wird nie erreicht, aber ist notwendig für die Kompilierung
        } catch (Exception e) {
            String errorMessage = "Fehler beim Extrahieren des Equity Drawdown für " + fileName + ": " + e.getMessage();
            logger.error(errorMessage, e);
            
            // Popup-Fenster anzeigen (blockierend)
            javax.swing.JOptionPane.showMessageDialog(
                null,
                errorMessage,
                "Equity Drawdown Extraktionsfehler",
                javax.swing.JOptionPane.ERROR_MESSAGE
            );
            
            // Prozess beenden
            System.exit(1);
            return 0.0; // Diese Zeile wird nie erreicht, aber ist notwendig für die Kompilierung
        }
    }

    // Hilfsmethode zum Extrahieren des Kontexts um ein Schlüsselwort
    private String extractContextAroundKeyword(String content, String keyword) {
        int index = content.indexOf(keyword);
        if (index != -1) {
            int start = Math.max(0, index - 50);
            int end = Math.min(content.length(), index + keyword.length() + 100);
            return content.substring(start, end);
        }
        return "Schlüsselwort nicht gefunden";
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