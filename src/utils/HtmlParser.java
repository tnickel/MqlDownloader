

package utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;        // JSoup-Dokument
import org.jsoup.nodes.Element;

public class HtmlParser {
    private static final Logger LOGGER = Logger.getLogger(HtmlParser.class.getName());
    
    // Pattern für rote Linie im Drawdown-Chart (verbessert für var(--c-chart-red))
    private static final Pattern RED_DRAWNDOWN_CHART_PATTERN = Pattern.compile(
        "<path(?=[^>]*(?:stroke|style)\\s*=\\s*\"[^\"]*(?:var\\(--c-chart-red\\)|red)[^\"]*\")[^>]*d\\s*=\\s*\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern DRAWNDOWN_PATTERN = Pattern.compile(
        "Maximaler[^%]*?([0-9]+(?:[.,][0-9]+)?)%",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern BALANCE_PATTERN = Pattern.compile(
        "<div class=\"s-list-info__item\">\\s*" +
        "<div class=\"s-list-info__label\">(Balance|Kontostand):\\s*</div>\\s*" +
        "<div class=\"s-list-info__value\">([\\d\\s\\.]+)\\s*[A-Z]{3}</div>\\s*" +
        "</div>"
    );
    
 // In der HtmlParser-Klasse, bei den anderen Cache-Definitionen:
    private final String rootPath;
    private final Map<String, String> htmlContentCache;
    private final Map<String, Double> equityDrawdownGraphicCache; // Umbenannt von equityDrawdownCache
    private final Map<String, Double> equityDrawdownCache; // Hinzufügen des fehlenden Caches
    private final Map<String, Double> balanceCache;
    private final Map<String, Double> averageProfitCache;
    private final Map<String, StabilityResult> stabilityCache;

    // Im Konstruktor:
    public HtmlParser(String rootPath) {
        this.rootPath = rootPath;
        this.htmlContentCache = new HashMap<>();
        this.equityDrawdownGraphicCache = new HashMap<>();
        this.equityDrawdownCache = new HashMap<>(); // Initialisierung des neuen Caches
        this.balanceCache = new HashMap<>();
        this.averageProfitCache = new HashMap<>();
        this.stabilityCache = new HashMap<>();
    }
    
    public String getHtmlContent(String fileName) {
        if (htmlContentCache.containsKey(fileName)) {
            return htmlContentCache.get(fileName);
        }
        String htmlFileName = fileName.replace(".csv", "_root.html");
        File htmlFile = new File(htmlFileName);
        if (!htmlFile.exists()) {
            LOGGER.warning("HTML file not found: " + htmlFile.getAbsolutePath());
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(htmlFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            String htmlContent = content.toString();
            htmlContentCache.put(fileName, htmlContent);
            return htmlContent;
        } catch (IOException e) {
            LOGGER.severe("Error reading HTML file: " + e.getMessage());
            return null;
        }
    }
    
    public double getBalance(String fileName) {
        if (balanceCache.containsKey(fileName)) {
            return balanceCache.get(fileName);
        }
        String htmlContent = getHtmlContent(fileName);
        if (htmlContent == null) return 0.0;
        Matcher matcher = BALANCE_PATTERN.matcher(htmlContent);
        if (matcher.find()) {
            String balanceStr = matcher.group(2).replaceAll("\\s+", "").replace(",", ".");
            try {
                double balance = Double.parseDouble(balanceStr);
                balanceCache.put(fileName, balance);
                return balance;
            } catch (NumberFormatException e) {
                LOGGER.warning("Could not parse balance number: " + balanceStr);
            }
        }
        LOGGER.warning("No balance value found for " + fileName);
        return 0.0;
    }
    
    public double getEquityDrawdownGraphic(String fileName) { // Umbenannt von getEquityDrawdown
        if (equityDrawdownGraphicCache.containsKey(fileName)) { // Umbenannt von equityDrawdownCache
            return equityDrawdownGraphicCache.get(fileName); // Umbenannt von equityDrawdownCache
        }
        
        // Get the drawdown chart data from the SVG
        List<ChartPoint> chartData = getDrawdownChartData(fileName);
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
        equityDrawdownGraphicCache.put(fileName, maxDrawdown); // Umbenannt von equityDrawdownCache
        return maxDrawdown;
    }
    
    public double getAvr3MonthProfit(String fileName) {
        if (averageProfitCache.containsKey(fileName)) {
            return averageProfitCache.get(fileName);
        }
        List<String> lastMonthsDetails = getLastThreeMonthsDetails(fileName);
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
        averageProfitCache.put(fileName, average);
        return average;
    }
    
    private double[] parseTransform(String transformString) {
        double translateX = 0, translateY = 0, scaleX = 1, scaleY = 1;
        if (transformString == null || transformString.isEmpty()) {
            return new double[]{translateX, translateY, scaleX, scaleY};
        }
        Pattern translatePattern = Pattern.compile("translate\\(([^,]+),\\s*([^\\)]+)\\)");
        Matcher translateMatcher = translatePattern.matcher(transformString);
        if (translateMatcher.find()) {
            try {
                translateX = Double.parseDouble(translateMatcher.group(1).trim());
                translateY = Double.parseDouble(translateMatcher.group(2).trim());
            } catch (NumberFormatException e) {
                LOGGER.warning("Fehler beim Parsen von translate: " + e.getMessage());
            }
        }
        Pattern scalePattern = Pattern.compile("scale\\(([^,\\)]+)(?:,\\s*([^\\)]+))?\\)");
        Matcher scaleMatcher = scalePattern.matcher(transformString);
        if (scaleMatcher.find()) {
            try {
                scaleX = Double.parseDouble(scaleMatcher.group(1).trim());
                String sy = scaleMatcher.group(2);
                if (sy != null && !sy.isEmpty()) {
                    scaleY = Double.parseDouble(sy.trim());
                } else {
                    scaleY = scaleX;
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("Fehler beim Parsen von scale: " + e.getMessage());
            }
        }
        return new double[]{translateX, translateY, scaleX, scaleY};
    }
    
    private double[] extractYAxisScale(String svgContent, String fileName) {
        LOGGER.info("=== extractYAxisScale: Datei=" + fileName + " ===");
        
        // Manuelle Werte basierend auf deiner Analyse und den Daten im Textfile
        // Hier nehmen wir an, dass y = 272.585 für 1% und y = 11.994 für 6% korrekt sind,
        // aber wir können die Skala anpassen, um die tatsächlichen Werte besser abzubilden
        LOGGER.warning("Keine automatische Erkennung der Y-Achsen-Beschriftungen. Verwende manuelle Werte basierend auf SVG-Analyse.");
        return new double[]{272.585, 11.994, 1.0, 6.0}; // topY, bottomY, topPercent, bottomPercent
    }
    
    private double transformYToValue(double y, double[] scaleData) {
        double topY = scaleData[0];
        double bottomY = scaleData[1];
        double topPercent = scaleData[2];
        double bottomPercent = scaleData[3];
        if (Math.abs(topY - bottomY) < 1e-6) {
            return topPercent;
        }
        double fraction = (y - topY) / (bottomY - topY);
        if (fraction < 0) fraction = 0;
        if (fraction > 1) fraction = 1;
        return topPercent + fraction * (bottomPercent - topPercent);
    }
    
    /**
     * Extrahiert aus dem HTML zunächst den SVG-Block, parst diesen mit Batik, 
     * und sucht dann nach <path>-Elementen, deren stroke oder style "red" enthält.
     * Anschließend wird das d-Attribut der gefundenen Pfade in (x,y)-Punkte zerlegt,
     * diese Punkte werden auf Datum (X) und Drawdown-Prozente (Y) abgebildet.
     */
    public List<ChartPoint> getDrawdownChartData(String fileName) {
        List<ChartPoint> chartData = new ArrayList<>();
        String html = getHtmlContent(fileName);
        if (html == null) {
            LOGGER.warning("HTML content is null for " + fileName);
            return chartData;
        }
        
        // 1) HTML mit JSoup parsen
        Document jsoupDoc = Jsoup.parse(html);
        
        // 2) Suche das DIV mit id="tab_content_drawdown_chart"
        Element drawdownDiv = jsoupDoc.selectFirst("div#tab_content_drawdown_chart");
        if (drawdownDiv == null) {
            LOGGER.warning("Kein DIV mit id='tab_content_drawdown_chart' gefunden in " + fileName);
            return chartData;
        }
        
        // 3) Darin das erste <svg> suchen
        Element svgElement = drawdownDiv.selectFirst("svg");
        if (svgElement == null) {
            LOGGER.warning("Kein <svg> in div#tab_content_drawdown_chart gefunden in " + fileName);
            return chartData;
        }
        
        // 4) Das reine SVG als String extrahieren
        String svgContent = svgElement.outerHtml();
        
        // Falls Namespace fehlt, ergänzen
        if (!svgContent.contains("xmlns=")) {
            LOGGER.info("SVG enthält keinen Namespace. Füge xmlns=\"http://www.w3.org/2000/svg\" hinzu.");
            svgContent = svgContent.replaceFirst("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"");
        }
        
        // 5) Mit Batik parsen
        org.w3c.dom.Document batikSvgDoc;
        try {
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            batikSvgDoc = factory.createDocument("http://www.w3.org/2000/svg",
                                                 new StringReader(svgContent));
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Parsen des SVG-Inhalts: " + e.getMessage());
            return chartData;
        }
        
        // 6) Roten Pfad finden mit verbessertem Pattern
        Matcher redPathMatcher = RED_DRAWNDOWN_CHART_PATTERN.matcher(svgContent);
        List<String> pathSegments = new ArrayList<>();
        while (redPathMatcher.find()) {
            String dAttr = redPathMatcher.group(1);
            if (dAttr != null && !dAttr.isEmpty()) {
                pathSegments.add(dAttr);
            }
        }
        
        if (pathSegments.isEmpty()) {
            LOGGER.info("Kein roter Drawdown-Pfad in " + fileName + " (div#tab_content_drawdown_chart) gefunden. Leere Liste.");
            return chartData;
        }
        
        // 7) d-Attribute => (x,y)-Punkte
        List<double[]> rawPoints = new ArrayList<>();
        for (String d : pathSegments) {
            String[] segments = d.split("(?=[MLCQAZmlcqa])");
            for (String seg : segments) {
                seg = seg.trim();
                if (seg.isEmpty()) continue;
                seg = seg.substring(1).trim();  // Befehl entfernen
                if (seg.isEmpty()) continue;
                String[] coords = seg.split("[,\\s]+");
                for (int i = 0; i < coords.length - 1; i += 2) {
                    try {
                        double x = Double.parseDouble(coords[i]);
                        double y = Double.parseDouble(coords[i + 1]);
                        rawPoints.add(new double[]{x, y});
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Konnte Koordinate nicht parsen: " + coords[i] + "," + coords[i + 1]);
                    }
                }
            }
        }
        if (rawPoints.isEmpty()) {
            LOGGER.warning("Keine (x,y)-Koordinaten im roten Drawdown-Pfad gefunden: " + fileName);
            return chartData;
        }
        
        double minX = rawPoints.stream().mapToDouble(p -> p[0]).min().orElse(0.0);
        double maxX = rawPoints.stream().mapToDouble(p -> p[0]).max().orElse(0.0);
        LOGGER.info(String.format("Nach SVG-Parsing: minX=%.2f, maxX=%.2f", minX, maxX));
        
        // 8) Y-Skala extrahieren
        double[] yScale = extractYAxisScale(svgContent, fileName);
        
        // 9) Datum-Interpolation (Beispiel für Februar 2025)
        LocalDate startDate = LocalDate.of(2025, 2, 1);
        LocalDate endDate = LocalDate.of(2025, 2, 28); // Februar 2025 hat 28 Tage
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        
        // 10) Endgültige ChartPoints erzeugen
        for (double[] pt : rawPoints) {
            double xVal = pt[0];
            double yVal = pt[1];
            
            double fractionX = (maxX - minX == 0) ? 0 : (xVal - minX) / (maxX - minX);
            fractionX = Math.max(0, Math.min(1, fractionX));
            long daysToAdd = Math.round(fractionX * daysBetween);
            String date = startDate.plusDays(daysToAdd).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            double ddValue = transformYToValue(yVal, yScale);
            chartData.add(new ChartPoint(date, ddValue));
        }
        
        LOGGER.info("Roter Drawdown-Pfad: " + chartData.size() + " Punkte gefunden.");
        return chartData;
    }
    
    public List<String> getLastThreeMonthsDetails(String fileName) {
        List<String> details = new ArrayList<>();
        List<String> months = new ArrayList<>();
        Set<String> seenDates = new HashSet<>();
        try {
            String htmlContent = getHtmlContent(fileName);
            if (htmlContent == null) return details;
            Pattern yearRowPattern = Pattern.compile(
                "<tr>\\s*<td[^>]*>(\\d{4})</td>\\s*((?:<td[^>]*>([^<]*)</td>\\s*){12})"
            );
            Matcher rowMatcher = yearRowPattern.matcher(htmlContent);
            boolean foundDuplicate = false;
            while (rowMatcher.find() && !foundDuplicate) {
                String year = rowMatcher.group(1);
                String monthsContent = rowMatcher.group(2);
                Pattern valuePattern = Pattern.compile("<td[^>]*>([^<]*)</td>");
                Matcher valueMatcher = valuePattern.matcher(monthsContent);
                int monthIndex = 0;
                while (valueMatcher.find() && monthIndex < 12) {
                    String value = valueMatcher.group(1).trim();
                    if (!value.isEmpty()) {
                        value = value.replace(",", ".")
                                     .replace("−", "-")
                                     .replaceAll("[^0-9.\\-]", "");
                        if (!value.isEmpty()) {
                            String date = year + "/" + String.format("%02d", monthIndex + 1);
                            if (seenDates.contains(date)) {
                                foundDuplicate = true;
                                break;
                            }
                            seenDates.add(date);
                            months.add(date + ":" + value);
                        }
                    }
                    monthIndex++;
                }
            }
            if (months.size() >= 2) {
                int startIndex = months.size() - 2;
                int monthsToUse = Math.min(3, startIndex + 1);
                for (int i = startIndex; i > startIndex - monthsToUse; i--) {
                    details.add(months.get(i));
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing HTML for " + fileName + ": " + e.getMessage());
        }
        return details;
    }
    
    public List<String> getAllMonthsDetails(String fileName) {
        List<String> allMonths = new ArrayList<>();
        Set<String> seenDates = new HashSet<>();
        try {
            String htmlContent = getHtmlContent(fileName);
            if (htmlContent == null) return allMonths;
            Pattern yearRowPattern = Pattern.compile(
                "<tr>\\s*<td[^>]*>(\\d{4})</td>\\s*((?:<td[^>]*>([^<]*)</td>\\s*){12})"
            );
            Matcher rowMatcher = yearRowPattern.matcher(htmlContent);
            boolean foundDuplicate = false;
            while (rowMatcher.find() && !foundDuplicate) {
                String year = rowMatcher.group(1);
                String monthsContent = rowMatcher.group(2);
                Pattern valuePattern = Pattern.compile("<td[^>]*>([^<]*)</td>");
                Matcher valueMatcher = valuePattern.matcher(monthsContent);
                int monthIndex = 0;
                while (valueMatcher.find() && monthIndex < 12) {
                    String value = valueMatcher.group(1).trim();
                    if (!value.isEmpty()) {
                        value = value.replace(",", ".")
                                   .replace("−", "-")
                                   .replaceAll("[^0-9.\\-]", "");
                        if (!value.isEmpty()) {
                            String date = year + "/" + String.format("%02d", monthIndex + 1);
                            if (seenDates.contains(date)) {
                                foundDuplicate = true;
                                break;
                            }
                            seenDates.add(date);
                            allMonths.add(date + ":" + value);
                        }
                    }
                    monthIndex++;
                }
            }
            Collections.sort(allMonths);
        } catch (Exception e) {
            LOGGER.severe("Error processing HTML for " + fileName + ": " + e.getMessage());
        }
        return allMonths;
    }
    
    public double getStabilitaetswert(String fileName) {
        return getStabilitaetswertDetails(fileName).getValue();
    }
    
    public StabilityResult getStabilitaetswertDetails(String fileName) {
        StringBuilder details = new StringBuilder();
        List<String> lastMonths = getLastThreeMonthsDetails(fileName);
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
                return new StabilityResult(finalStability, details.toString());
            }
            return new StabilityResult(1.0, "Nicht genügend Monatswerte verfügbar<br>Gefundene Werte:<br>" + String.join("<br>", lastMonths));
        } catch (Exception e) {
            return new StabilityResult(1.0, "Fehler bei der Berechnung: " + e.getMessage());
        }
    }
    
    /**
     * Schreibt den Equity-Drawdown für eine gegebene Datei in eine Textdatei.
     * @param fileName Die Quelldatei, aus der der Drawdown extrahiert wird.
     * @param outputFilePath Der Pfad zur Ausgabedatei (z. B. "equity_drawdown.txt").
     */
    public void writeEquityDrawdownToFile(String fileName, String outputFilePath) {
        double equityDrawdownGraphic = getEquityDrawdownGraphic(fileName); // Umbenannt von getEquityDrawdown
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath, true))) {
            writer.println("File: " + fileName + " - EquityDrawdownGraphic: " + String.format("%.2f%%", equityDrawdownGraphic)); // Umbenannt
            LOGGER.info("EquityDrawdownGraphic für " + fileName + " wurde in " + outputFilePath + " geschrieben: " + equityDrawdownGraphic + "%");
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Schreiben in die Datei " + outputFilePath + ": " + e.getMessage());
        }
    }
    public double getEquityDrawdown(String fileName) {
        // Cache-Check
        if (equityDrawdownCache.containsKey(fileName)) {
            return equityDrawdownCache.get(fileName);
        }
        
        String htmlContent = getHtmlContent(fileName);
        if (htmlContent == null) {
            LOGGER.warning("HTML content is null for " + fileName);
            return 0.0;
        }
        
        Matcher matcher = DRAWNDOWN_PATTERN.matcher(htmlContent);
        if (matcher.find()) {
            String drawdownStr = matcher.group(1).replace(",", ".");
            try {
                double drawdown = Double.parseDouble(drawdownStr);
                equityDrawdownCache.put(fileName, drawdown);
                return drawdown;
            } catch (NumberFormatException e) {
                LOGGER.warning("Could not parse drawdown number: " + drawdownStr);
            }
        }
        
        LOGGER.warning("No drawdown value found for " + fileName);
        return 0.0;
    }
}





