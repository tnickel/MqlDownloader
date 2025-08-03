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
                if (showErrorAndAskForDeletion(errorMessage, fileName)) {
                    return 0.0;  // Datei wurde gelöscht, wir geben 0.0 zurück
                } else {
                    // Benutzer möchte nicht löschen, aber wir können nicht fortfahren
                    System.exit(1);
                    return 0.0;  // Diese Zeile wird nie erreicht
                }
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
                    if (showErrorAndAskForDeletion(errorMessage, fileName)) {
                        return 0.0;  // Datei wurde gelöscht, wir geben 0.0 zurück
                    } else {
                        // Benutzer möchte nicht löschen, aber wir können nicht fortfahren
                        System.exit(1);
                        return 0.0;  // Diese Zeile wird nie erreicht
                    }
                }
            }
        } catch (Exception e) {
            String errorMessage = "Fehler beim Extrahieren der Balance für " + fileName + ": " + e.getMessage();
            logger.error(errorMessage, e);
            if (showErrorAndAskForDeletion(errorMessage, fileName)) {
                return 0.0;  // Datei wurde gelöscht, wir geben 0.0 zurück
            } else {
                // Benutzer möchte nicht löschen, aber wir können nicht fortfahren
                System.exit(1);
                return 0.0;  // Diese Zeile wird nie erreicht
            }
        }
    }

    // Ersetzte Methode: Zeigt Fehlermeldung und fragt, ob die Datei gelöscht werden soll
    private boolean showErrorAndAskForDeletion(String errorMessage, String fileName) {
        // Optionen für den Dialog
        String[] options = {"Datei löschen und fortfahren", "Abbrechen"};
        
        // Dialog anzeigen und Auswahl des Benutzers erhalten
        int choice = javax.swing.JOptionPane.showOptionDialog(
            null,
            errorMessage + "\n\nSoll die Datei gelöscht und mit der Konvertierung fortgefahren werden?",
            "Extraktionsfehler",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[1]  // Standardauswahl ist "Abbrechen"
        );
        
        // Wenn Benutzer "Datei löschen und fortfahren" wählt
        if (choice == 0) {
            if (deleteRelatedFiles(fileName)) {
                logger.info("Dateien wurden gelöscht, Konvertierung wird fortgesetzt.");
                return true;
            } else {
                logger.error("Fehler beim Löschen der Dateien.");
                return false;
            }
        }
        
        // Benutzer hat abgebrochen
        return false;
    }
    
    // Neue Methode: Löscht die zugehörigen Dateien (root.html, .csv und .txt)
    private boolean deleteRelatedFiles(String fileName) {
        try {
            Path htmlPath = Paths.get(fileName);
            
            // Pfade für entsprechende CSV- und TXT-Dateien erzeugen
            String baseName = fileName.replace("_root.html", "");
            Path csvPath = Paths.get(baseName + ".csv");
            Path txtPath = Paths.get(baseName + "_root.txt");
            
            // Dateien löschen, wenn sie existieren
            boolean success = true;
            
            if (Files.exists(htmlPath)) {
                Files.delete(htmlPath);
                logger.info("Gelöscht: " + htmlPath);
            }
            
            if (Files.exists(csvPath)) {
                Files.delete(csvPath);
                logger.info("Gelöscht: " + csvPath);
            }
            
            if (Files.exists(txtPath)) {
                Files.delete(txtPath);
                logger.info("Gelöscht: " + txtPath);
            }
            
            return success;
        } catch (IOException e) {
            logger.error("Fehler beim Löschen der Dateien: " + e.getMessage(), e);
            return false;
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

            // Pattern 1: Originalformat mit korrektem "Rückgang"
            Pattern pattern = Pattern.compile("Maximaler Rückgang:</tspan><tspan[^>]*>(\\d+(?:\\.\\d+)?)%</tspan>");
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1);
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 1): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Pattern 2: Flexiblerer Ausdruck für ähnliche Formate
            pattern = Pattern.compile("Maximaler Rückgang:(?:</tspan>)?(?:<[^>]*>)?(\\d+(?:[,.]\\d+)?)%");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 2): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Pattern 3: Suche nach verkrüppeltem Format "Maximaler...Rüg: XX.X%"
            pattern = Pattern.compile("Maximaler[^<]*</tspan><tspan[^>]*>R.g:\\s*(\\d+(?:[,.]\\d+)?)%</tspan>");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 3 - verkrüppelt): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Pattern 4: NEU - Robustes Pattern für UTF-8-Kodierungsprobleme
            // Sucht nach "Maximaler" gefolgt von beliebigen Zeichen bis zum ":" und dann Prozentwert
            pattern = Pattern.compile("Maximaler[^<]*</tspan><tspan[^>]*>[^:]*:\\s*(\\d+(?:[,.]\\d+)?)%</tspan>");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 4 - UTF-8 robust): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Pattern 5: NEU - Sehr flexibles Pattern, das beliebige Zeichen zwischen "Maximaler" und ":" akzeptiert
            pattern = Pattern.compile("Maximaler.*?:\\s*(\\d+(?:[,.]\\d+)?)%");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 5 - sehr flexibel): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Pattern 6: NEU - Spezifisch für das beobachtete HTML-Format
            // >Maximaler</tspan><tspan dy="17" x="75">Rückgang: 14.1%</tspan>
            pattern = Pattern.compile(">Maximaler</tspan><tspan[^>]*>[^:]*:\\s*(\\d+(?:[,.]\\d+)?)%</tspan>");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 6 - spezifisches HTML-Format): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Pattern 7: NEU - Fallback für Fälle wo nur der Prozentwert nach "Maximaler" relevant ist
            // Sucht nach "Maximaler" und dann dem ersten Prozentwert in der Nähe
            pattern = Pattern.compile("Maximaler[\\s\\S]{0,200}?(\\d+(?:[,.]\\d+)?)%");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String ddStr = matcher.group(1).replace(",", ".");
                equityDrawdown = Double.parseDouble(ddStr);
                logger.info("Equity Drawdown erfolgreich extrahiert (Pattern 7 - Fallback): " + equityDrawdown);
                return equityDrawdown;
            }
            
            // Debug-Ausgabe für Fehlerbehebung - erweitert um mehr Kontext
            String context = extractContextAroundKeyword(htmlContent, "Maximaler");
            logger.debug("HTML-Inhalt um 'Maximaler': " + context);
            
            // Zusätzliche Debug-Ausgabe: Suche nach beliebigen Prozentzeichen in der Nähe von "Maximaler"
            pattern = Pattern.compile("Maximaler[\\s\\S]{0,500}");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                logger.debug("Erweiterte Kontext um 'Maximaler': " + matcher.group(0));
            }
            
            // Wenn kein Equity Drawdown gefunden wurde
            String errorMessage = "Equity Drawdown konnte nicht extrahiert werden für Datei: " + fileName;
            logger.error(errorMessage);
            
            // Anstatt sofort zu beenden, fragen wir den Benutzer
            if (showErrorAndAskForDeletion(errorMessage, fileName)) {
                return 0.0;  // Datei wurde gelöscht, wir geben 0.0 zurück
            } else {
                // Benutzer möchte nicht löschen, aber wir können nicht fortfahren
                System.exit(1);
                return 0.0;  // Diese Zeile wird nie erreicht, aber ist notwendig für die Kompilierung
            }
        } catch (Exception e) {
            String errorMessage = "Fehler beim Extrahieren des Equity Drawdown für " + fileName + ": " + e.getMessage();
            logger.error(errorMessage, e);
            
            // Anstatt sofort zu beenden, fragen wir den Benutzer
            if (showErrorAndAskForDeletion(errorMessage, fileName)) {
                return 0.0;  // Datei wurde gelöscht, wir geben 0.0 zurück
            } else {
                // Benutzer möchte nicht löschen, aber wir können nicht fortfahren
                System.exit(1);
                return 0.0;  // Diese Zeile wird nie erreicht, aber ist notwendig für die Kompilierung
            }
        }
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
            String errorMessage = "Fehler beim Berechnen des durchschnittlichen 3-Monats-Profits für " + fileName + ": " + e.getMessage();
            logger.error(errorMessage, e);
            if (showErrorAndAskForDeletion(errorMessage, fileName)) {
                return 0.0;  // Datei wurde gelöscht, wir geben 0.0 zurück
            } else {
                // Benutzer möchte nicht löschen, aber wir können nicht fortfahren
                System.exit(1);
                return 0.0;  // Diese Zeile wird nie erreicht
            }
        }
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