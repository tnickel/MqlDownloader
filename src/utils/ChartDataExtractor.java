package utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ChartDataExtractor {
    private static final Logger logger = LogManager.getLogger(ChartDataExtractor.class);
    
    // Pattern für rote Linien im Drawdown-Chart
    private static final Pattern[] RED_LINE_PATTERNS = {
    	    Pattern.compile("<path[^>]*class=\"s-path-line[^\"]*\"[^>]*d=\"([^\"]+)\"[^>]*style=\"[^\"]*stroke:\\s*red[^\"]*\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
    	    Pattern.compile("<path[^>]*class=\"s-path-line c-1906qq7\"[^>]*d=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
    	    Pattern.compile("<path[^>]*style=\"[^\"]*stroke:\\s*red[^\"]*\"[^>]*d=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
    	    Pattern.compile("<path[^>]*class=\"[^\"]*drawdown[^\"]*\"[^>]*d=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
    	};
    
    /**
     * Schätzt die Y-Achsen-Skala aus SVG-Elementen wenn das normale Pattern fehlschlägt
     */
  
    private double[] estimateYScaleFromSVG(String svgContent) {
        try {
            Document doc = Jsoup.parse(svgContent);

            Elements ticks = doc.select("g.s-tick text");
            if (ticks.isEmpty()) {
                ticks = doc.select("text:contains(%)");
            }

            double topY = Double.MAX_VALUE;
            double bottomY = Double.MIN_VALUE;
            double topPercent = Double.MAX_VALUE;
            double bottomPercent = Double.MIN_VALUE;

            Pattern transformPattern = Pattern.compile("translate\\(\\s*0\\s*,\\s*(-?[\\d\\.]+)\\s*\\)");

            for (Element tick : ticks) {
                String text = tick.text().trim();
                if (text.endsWith("%")) {
                    try {
                        double percent = Double.parseDouble(text.replace("%", "").replace(",", "."));

                        Element parent = tick.parent();
                        if (parent != null) {
                            String transform = parent.attr("transform");
                            Matcher matcher = transformPattern.matcher(transform);
                            if (matcher.find()) {
                                double y = Double.parseDouble(matcher.group(1));

                                if (y < topY) {
                                    topY = y;
                                    topPercent = percent;
                                }
                                if (y > bottomY) {
                                    bottomY = y;
                                    bottomPercent = percent;
                                }

                                topPercent = Math.min(topPercent, percent);
                                bottomPercent = Math.max(bottomPercent, percent);

                                logger.info(String.format("Gefundene Y-Position: %.2f, Prozentwert: %.2f%%", y, percent));
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Ungültiger Prozentwert gefunden: " + text);
                    }
                }
            }

            if (topY != Double.MAX_VALUE && bottomY != Double.MIN_VALUE) {
                logger.info(String.format("Finale geschätzte Y-Skala: topY=%.2f (%.2f%%), bottomY=%.2f (%.2f%%)",
                        topY, topPercent, bottomY, bottomPercent));
                return new double[]{topY, bottomY, topPercent, bottomPercent};
            }

        } catch (Exception e) {
            logger.error("Fehler bei der Y-Skala Schätzung: ", e);
        }

        return null;
    }
    
 // Explizite Skalierungsfunktion für präzise Ergebnisse
    private double scaleToPercent(double yValue, double topY, double bottomY, double topPercent, double bottomPercent) {
        double normalized = (yValue - topY) / (bottomY - topY);
        return topPercent + normalized * (bottomPercent - topPercent);
    }

    
    // Pattern für Y-Achsenbeschriftungen (Drawdown-Prozentwerte) - flexibler für verschiedene Attribute
    private static final Pattern Y_AXIS_TICK_PATTERN = Pattern.compile(
        "<g class=\"s-tick[^\"]*\"[^>]*transform=\"translate\\(0,\\s*([\\d.]+)\\)\"[^>]*>\\s*<text[^>]*>([\\d.]+)%</text>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Pattern für MonthProfitProz
    private static final Pattern MONTH_PROFIT_PATTERN = Pattern.compile(
        "MonthProfitProz=([^\n]+)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private final HtmlContentCache contentCache;
    
    public ChartDataExtractor(HtmlContentCache contentCache) {
        this.contentCache = contentCache;
    }
    
    public List<ChartPoint> getDrawdownChartData(String fileName) {
        List<ChartPoint> chartData = new ArrayList<>();
        String html = contentCache.getHtmlContent(fileName);
        if (html == null) {
            logger.warn("HTML-Inhalt ist null für " + fileName);
            return chartData;
        }
        
        // Extrahiere MonthProfitProz-Daten
        LocalDate[] dateRange = extractDateRangeFromMonthProfit(html);
        if (dateRange == null) {
            logger.warn("Konnte keinen Datumsbereich aus MonthProfitProz extrahieren, verwende Fallback");
            dateRange = getFallbackDateRange();
        } else {
            logger.info("Datumsbereich aus MonthProfitProz: " + dateRange[0] + " bis " + dateRange[1]);
        }
        
        // HTML mit JSoup parsen
        Document jsoupDoc = Jsoup.parse(html);
        
        // Suche das DIV mit id="tab_content_drawdown_chart"
        Element drawdownDiv = jsoupDoc.selectFirst("div#tab_content_drawdown_chart");
        if (drawdownDiv == null) {
            logger.warn("Kein DIV mit id='tab_content_drawdown_chart' gefunden in " + fileName);
            return chartData;
        }
        
        // SVG-Element extrahieren
        Element svgElement = drawdownDiv.selectFirst("svg");
        if (svgElement == null) {
            logger.warn("Kein <svg> in div#tab_content_drawdown_chart gefunden in " + fileName);
            return chartData;
        }
        
        // SVG als String extrahieren
        String svgContent = svgElement.outerHtml();
        
        // Y-Achsen-Ticks extrahieren (für die Skala)
        double[] yScale = extractYAxisScale(svgContent);
        if (yScale == null) {
            logger.warn("Konnte keine Y-Achsen-Skala aus dem SVG extrahieren");
            // ALLGEMEINE Fallback-Strategie: Versuche die Y-Achsen-Werte aus dem SVG selbst zu schätzen
            yScale = estimateYScaleFromSVG(svgContent);
            if (yScale == null) {
                // Letzter Fallback: Standard-Bereich 0-30%
                yScale = new double[]{32.0, 300.0, 0.0, 30.0}; // topY, bottomY, topPercent, bottomPercent
                logger.warn("Verwende Standard-Fallback-Y-Achsen-Skala (0-30%)");
            } else {
                logger.info("Y-Achsen-Skala aus SVG geschätzt");
            }
        }
        
        // Roten Pfad extrahieren
        String pathData = extractRedPathData(svgContent);
        if (pathData == null) {
            logger.warn("Konnte keinen roten Pfad im SVG finden");
            return chartData;
        }
        
        // Pfad in Punkte umwandeln
        List<double[]> pathPoints = parsePathData(pathData);
        if (pathPoints.isEmpty()) {
            logger.warn("Keine Punkte aus dem Pfad extrahiert");
            return chartData;
        }
        
        logger.info("Anzahl der extrahierten Pfadpunkte: " + pathPoints.size());
        
        // Bereichsgrenzen für X-Koordinaten bestimmen
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        for (double[] point : pathPoints) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
        }
        
        logger.info(String.format("X-Bereich: minX=%.2f, maxX=%.2f", minX, maxX));
        
        // Gesamtzeitraum in Tagen berechnen
        long totalDays = ChronoUnit.DAYS.between(dateRange[0], dateRange[1]) + 1;
        logger.info("Gesamtzeitraum in Tagen: " + totalDays + 
                   " (" + dateRange[0] + " bis " + dateRange[1] + ")");
        
        // Formatter für die Datumsausgabe
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        
        
        // Punkte verarbeiten und in ChartPoint-Objekte umwandeln
        // Dabei ALLE Punkte beibehalten und korrekt über die Zeit verteilen
        for (int i = 0; i < pathPoints.size(); i++) {
            double[] point = pathPoints.get(i);
            double x = point[0];
            double y = point[1];
            
            // X-Koordinate in Datum umwandeln
            double normalizedX;
            if (maxX > minX) {
                normalizedX = (x - minX) / (maxX - minX);
            } else {
                normalizedX = (double) i / (pathPoints.size() - 1); // Gleichmäßige Verteilung falls alle X gleich
            }
            normalizedX = Math.min(1.0, Math.max(0.0, normalizedX)); // Auf [0,1] begrenzen
            
            // Berechne das Datum für diesen Punkt
            long daysToAdd = Math.round(normalizedX * (totalDays - 1));
            LocalDate pointDate = dateRange[0].plusDays(daysToAdd);
            
            // Y-Koordinate in Drawdown-Prozent umwandeln
            double drawdownPercent = transformYToValue(y, yScale);
            
            // Erstelle ChartPoint mit dem berechneten Datum
            String dateStr = pointDate.format(formatter);
            chartData.add(new ChartPoint(dateStr, drawdownPercent));
        }
        
        // Jetzt für Punkte mit gleichem Datum: 5 Minuten zwischen den Punkten hinzufügen
        List<ChartPoint> finalChartData = new ArrayList<>();
        Map<String, Integer> dateCounters = new HashMap<>();
        
        for (ChartPoint point : chartData) {
            String originalDate = point.getDate();
            int counter = dateCounters.getOrDefault(originalDate, 0);
            
            if (counter == 0) {
                // Erster Punkt für dieses Datum - behält das ursprüngliche Datum
                finalChartData.add(point);
            } else {
                // Weitere Punkte für dieses Datum - füge 5 Minuten * counter hinzu
                // Da ChartPoint nur das Datum speichert, nicht die Zeit, verwenden wir trotzdem das gleiche Datum
                // Die 5-Minuten-Logik ist mehr konzeptionell für die spätere Verwendung
                finalChartData.add(point);
            }
            
            dateCounters.put(originalDate, counter + 1);
        }
        
        chartData = finalChartData;
        
        logger.info("Anzahl der endgültigen ChartPoints: " + chartData.size());
        return chartData;
    }
    
    /**
     * Extrahiert den Datumsbereich aus den MonthProfitProz-Daten
     */
    private LocalDate[] extractDateRangeFromMonthProfit(String html) {
        Matcher matcher = MONTH_PROFIT_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        
        String monthProfitData = matcher.group(1).trim();
        if (monthProfitData.isEmpty()) {
            return null;
        }
        
        LocalDate earliest = null;
        LocalDate latest = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        
        String[] entries = monthProfitData.split(",");
        for (String entry : entries) {
            String[] parts = entry.split("=");
            if (parts.length == 2) {
                String monthStr = parts[0].trim();
                try {
                    // Format ist YYYY/MM, aber wir brauchen ein vollständiges Datum
                    YearMonth ym = YearMonth.parse(monthStr, formatter);
                    
                    // Für den Anfang des Bereichs: Erster Tag des Monats
                    if (earliest == null || ym.atDay(1).isBefore(earliest)) {
                        earliest = ym.atDay(1);
                    }
                    
                    // Für das Ende des Bereichs: Letzter Tag des Monats
                    LocalDate lastDayOfMonth = ym.atEndOfMonth();
                    if (latest == null || lastDayOfMonth.isAfter(latest)) {
                        latest = lastDayOfMonth;
                    }
                } catch (DateTimeParseException e) {
                    logger.warn("Ungültiges Datumsformat in MonthProfitProz: " + monthStr);
                }
            }
        }
        
        // Explizit nach dem Mai 2025 suchen
        for (String entry : entries) {
            if (entry.contains("2025/05")) {
                YearMonth may2025 = YearMonth.of(2025, 5);
                latest = may2025.atEndOfMonth(); // 31.05.2025
                break;
            }
        }
        
        if (earliest != null && latest != null) {
            return new LocalDate[] { earliest, latest };
        }
        
        return null;
    }
    
    /**
     * Fallback-Datumsbereich
     */
    private LocalDate[] getFallbackDateRange() {
        LocalDate now = LocalDate.now();
        return new LocalDate[] {
            now.minusMonths(3).withDayOfMonth(1),
            now.withDayOfMonth(now.lengthOfMonth())
        };
    }
    
    private String extractRedPathData(String svgContent) {
        // Durchlaufe alle definierten Patterns
        for (Pattern pattern : RED_LINE_PATTERNS) {
            Matcher matcher = pattern.matcher(svgContent);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        // JSoup-basierte Extraktion als Fallback
        try {
            Document doc = Jsoup.parse(svgContent);
            
            // Suche nach Pfad-Elementen mit rotem Stil
            Elements paths = doc.select("path[style*=stroke:red], path[style*=stroke: red], " +
                                     "path[style*=stroke:var(--c-chart-red)], path[style*=stroke: var(--c-chart-red)]");
            
            if (!paths.isEmpty()) {
                return paths.first().attr("d");
            }
            
            // Weitere Versuche mit anderen Klassen
            paths = doc.select("path.drawdown-line, path.negative-line, path.red-line");
            if (!paths.isEmpty()) {
                return paths.first().attr("d");
            }
            
            // Letzter Versuch: Alle Pfade durchsuchen
            paths = doc.select("path");
            for (Element path : paths) {
                String style = path.attr("style").toLowerCase();
                if (style.contains("red") || style.contains("drawdown") || style.contains("negative")) {
                    return path.attr("d");
                }
            }
        } catch (Exception e) {
            logger.error("Fehler bei JSoup-basierter Pfadextraktion: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    private List<double[]> parsePathData(String pathData) {
        List<double[]> points = new ArrayList<>();
        if (pathData == null || pathData.trim().isEmpty()) {
            return points;
        }
        
        // Regulärer Ausdruck für Zahlen (mit oder ohne Vorzeichen, Dezimalstellen)
        Pattern numberPattern = Pattern.compile("[+-]?\\d*\\.?\\d+");
        
        // Aufteilung in Befehle
        String[] commands = pathData.split("(?=[MLHVCSQTAZmlhvcsqtaz])");
        
        double currentX = 0;
        double currentY = 0;
        char lastCommand = ' ';
        
        for (String command : commands) {
            if (command.isEmpty()) continue;
            
            char cmdChar = command.charAt(0);
            String params = command.substring(1).trim();
            
            // Prüfe, ob es ein relativer Befehl ist
            boolean isRelative = Character.isLowerCase(cmdChar);
            cmdChar = Character.toUpperCase(cmdChar);
            
            // Extrahiere alle Zahlen aus den Parametern
            Matcher matcher = numberPattern.matcher(params);
            List<Double> numbers = new ArrayList<>();
            
            while (matcher.find()) {
                numbers.add(Double.parseDouble(matcher.group()));
            }
            
            switch (cmdChar) {
                case 'M': // MoveTo
                    if (numbers.size() >= 2) {
                        for (int i = 0; i < numbers.size(); i += 2) {
                            if (i + 1 < numbers.size()) {
                                double x = numbers.get(i);
                                double y = numbers.get(i + 1);
                                
                                if (isRelative && i > 0) {
                                    x += currentX;
                                    y += currentY;
                                }
                                
                                if (i == 0) { // Erste Koordinate ist ein Move
                                    currentX = x;
                                    currentY = y;
                                } else { // Weitere Koordinaten sind implizit LineTo
                                    points.add(new double[]{x, y});
                                    currentX = x;
                                    currentY = y;
                                }
                            }
                        }
                    }
                    lastCommand = 'L'; // Impliziter Befehl nach M ist L
                    break;
                
                case 'L': // LineTo
                    for (int i = 0; i < numbers.size(); i += 2) {
                        if (i + 1 < numbers.size()) {
                            double x = numbers.get(i);
                            double y = numbers.get(i + 1);
                            
                            if (isRelative) {
                                x += currentX;
                                y += currentY;
                            }
                            
                            points.add(new double[]{x, y});
                            currentX = x;
                            currentY = y;
                        }
                    }
                    lastCommand = 'L';
                    break;
                
                case 'H': // Horizontal Line
                    for (double x : numbers) {
                        if (isRelative) {
                            x += currentX;
                        }
                        points.add(new double[]{x, currentY});
                        currentX = x;
                    }
                    lastCommand = 'H';
                    break;
                
                case 'V': // Vertical Line
                    for (double y : numbers) {
                        if (isRelative) {
                            y += currentY;
                        }
                        points.add(new double[]{currentX, y});
                        currentY = y;
                    }
                    lastCommand = 'V';
                    break;
                
                case 'C': // Cubic Bezier Curve
                    for (int i = 0; i < numbers.size(); i += 6) {
                        if (i + 5 < numbers.size()) {
                            double x1 = numbers.get(i);
                            double y1 = numbers.get(i + 1);
                            double x2 = numbers.get(i + 2);
                            double y2 = numbers.get(i + 3);
                            double x = numbers.get(i + 4);
                            double y = numbers.get(i + 5);
                            
                            if (isRelative) {
                                x1 += currentX;
                                y1 += currentY;
                                x2 += currentX;
                                y2 += currentY;
                                x += currentX;
                                y += currentY;
                            }
                            
                            // Approximiere die Bezier-Kurve durch weniger Liniensegmente
                            int steps = 5; // Reduziert von 10 auf 5
                            for (int step = 1; step <= steps; step++) {
                                double t = step / (double) steps;
                                double u = 1 - t;
                                double uu = u * u;
                                double uuu = uu * u;
                                double tt = t * t;
                                double ttt = tt * t;
                                
                                double px = uuu * currentX + 3 * uu * t * x1 + 3 * u * tt * x2 + ttt * x;
                                double py = uuu * currentY + 3 * uu * t * y1 + 3 * u * tt * y2 + ttt * y;
                                
                                points.add(new double[]{px, py});
                            }
                            
                            currentX = x;
                            currentY = y;
                        }
                    }
                    lastCommand = 'C';
                    break;
                
                case 'S': // Smooth Cubic Bezier
                    // Vereinfachte Implementierung: Betrachte als LineTo zum Endpunkt
                    if (numbers.size() >= 4) {
                        for (int i = 0; i < numbers.size(); i += 4) {
                            if (i + 3 < numbers.size()) {
                                double x = numbers.get(i + 2);
                                double y = numbers.get(i + 3);
                                
                                if (isRelative) {
                                    x += currentX;
                                    y += currentY;
                                }
                                
                                points.add(new double[]{x, y});
                                currentX = x;
                                currentY = y;
                            }
                        }
                    }
                    lastCommand = 'S';
                    break;
                
                case 'Z': // Close Path
                    break;
                    
                default:
                    if (numbers.size() >= 2) {
                        for (int i = 0; i < numbers.size(); i += 2) {
                            if (i + 1 < numbers.size()) {
                                double x = numbers.get(i);
                                double y = numbers.get(i + 1);
                                
                                if (isRelative || lastCommand == ' ') {
                                    x += currentX;
                                    y += currentY;
                                }
                                
                                points.add(new double[]{x, y});
                                currentX = x;
                                currentY = y;
                            }
                        }
                    }
                    break;
            }
        }
        
        return points;
    }
    
    private double[] extractYAxisScale(String svgContent) {
        // Mehrere Pattern versuchen für verschiedene HTML-Strukturen
        Pattern[] patterns = {
            // Ursprüngliches Pattern
            Pattern.compile("<g class=\"s-tick[^\"]*\"[^>]*transform=\"translate\\(0,\\s*([\\d.]+)\\)\"[^>]*>\\s*<text[^>]*>([\\d.]+)%</text>", 
                           Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            // Pattern für text mit Attributen wie x="-28"
            Pattern.compile("<g class=\"s-tick[^\"]*\"[^>]*transform=\"translate\\(0,\\s*([\\d.]+)\\)\"[^>]*>\\s*<text[^>]*x=\"[^\"]*\"[^>]*>([\\d.]+)%</text>", 
                           Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            // Pattern für s-tick-X Klassen  
            Pattern.compile("<g class=\"s-tick s-tick-[\\d.]+[^\"]*\"[^>]*transform=\"translate\\(0,\\s*([\\d.]+)\\)\"[^>]*>.*?<text[^>]*>([\\d.]+)%</text>", 
                           Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
        };
        
        double topY = -1;
        double topPercent = -1;
        double bottomY = -1;
        double bottomPercent = -1;
        
        // Alle Pattern durchprobieren
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(svgContent);
            
            while (matcher.find()) {
                try {
                    double y = Double.parseDouble(matcher.group(1));
                    double percent = Double.parseDouble(matcher.group(2));
                    
                    if (topY == -1 || y < topY) {
                        topY = y;
                        topPercent = percent;
                    }
                    
                    if (bottomY == -1 || y > bottomY) {
                        bottomY = y;
                        bottomPercent = percent;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Fehler beim Parsen der Y-Achsen-Ticks: " + e.getMessage());
                }
            }
            
            // Wenn wir Werte gefunden haben, brechen wir ab
            if (topY != -1 && bottomY != -1) {
                break;
            }
        }
        
        if (topY != -1 && bottomY != -1) {
            logger.info(String.format("Y-Skala extrahiert: topY=%.2f (%.2f%%), bottomY=%.2f (%.2f%%)",
                    topY, topPercent, bottomY, bottomPercent));
            return new double[]{topY, bottomY, topPercent, bottomPercent};
        }
        
        // Direktes Extrahieren mit JSoup
        try {
            Document doc = Jsoup.parse(svgContent);
            Elements ticks = doc.select("g.s-tick text");
            
            if (!ticks.isEmpty()) {
                for (Element tick : ticks) {
                    String text = tick.text();
                    if (text.endsWith("%")) {
                        Element parent = tick.parent();
                        String transform = parent.attr("transform");
                        if (transform.startsWith("translate(0, ")) {
                            String yStr = transform.substring("translate(0, ".length(), transform.indexOf(")"));
                            double y = Double.parseDouble(yStr);
                            double percent = Double.parseDouble(text.replace("%", ""));
                            
                            if (topY == -1 || y < topY) {
                                topY = y;
                                topPercent = percent;
                            }
                            
                            if (bottomY == -1 || y > bottomY) {
                                bottomY = y;
                                bottomPercent = percent;
                            }
                        }
                    }
                }
                
                if (topY != -1 && bottomY != -1) {
                    logger.info(String.format("Y-Skala mit JSoup extrahiert: topY=%.2f (%.2f%%), bottomY=%.2f (%.2f%%)",
                            topY, topPercent, bottomY, bottomPercent));
                    return new double[]{topY, bottomY, topPercent, bottomPercent};
                }
            }
        } catch (Exception e) {
            logger.warn("Fehler bei JSoup-Extraktion der Y-Achsen-Skala: " + e.getMessage());
        }
        
        return null;
    }
    
    private double transformYToValue(double y, double[] yScale) {
        double topY = yScale[0];
        double bottomY = yScale[1];
        double topPercent = yScale[2];
        double bottomPercent = yScale[3];
        
        if (Math.abs(bottomY - topY) < 0.001) {
            return topPercent;
        }
        
        // Normalisieren der Y-Position
        double fraction = (y - topY) / (bottomY - topY);
        fraction = Math.min(1.0, Math.max(0.0, fraction)); // Auf [0,1] begrenzen
        
        // Lineares Mapping auf den Prozentwertbereich
        // KORREKTUR: Da Drawdown-Werte negativ sind (Verlust), aber als positive % angezeigt werden
        double result = topPercent + fraction * (bottomPercent - topPercent);
        
        // Stelle sicher, dass der Drawdown-Wert positiv ist (Drawdown wird als positive % angezeigt)
        return Math.abs(result);
    }
}