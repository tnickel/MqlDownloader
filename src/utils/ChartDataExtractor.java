package utils;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ChartDataExtractor {
    private static final Logger logger = LogManager.getLogger(ChartDataExtractor.class);
    
    // Verbesserte Pattern für rote Linie im Drawdown-Chart
    private static final Pattern RED_DRAWNDOWN_CHART_PATTERN = Pattern.compile(
        "<path[^>]*class=\"s-path-line[^\"]*\"[^>]*d=\"([^\"]+)\"[^>]*style=\"[^\"]*stroke:\\s*var\\(--c-chart-red\\)[^\"]*\"",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Alternative Muster für die rote Linie
    private static final Pattern ALT_RED_LINE_PATTERN = Pattern.compile(
        "<path[^>]*class=\"s-path-line c-1906qq7\"[^>]*d=\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Muster für Y-Achsenbeschriftungen (Drawdown-Prozentwerte)
    private static final Pattern Y_AXIS_TICK_PATTERN = Pattern.compile(
        "<g class=\"s-tick[^\"]*\"[^>]*transform=\"translate\\(0,\\s*([\\d.]+)\\)\"[^>]*>\\s*<text[^>]*>([\\d.]+)%</text>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Muster für X-Achsenbeschriftungen (Datumswerte)
    private static final Pattern X_AXIS_TICK_PATTERN = Pattern.compile(
        "<g class=\"s-tick[^\"]*\"[^>]*transform=\"translate\\(([\\d.]+),\\s*320\\)\"[^>]*>\\s*<text[^>]*>([A-Za-z]+)\\s+(\\d{4})</text>",
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
            logger.warn("HTML content is null for " + fileName);
            return chartData;
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
            // Fallback-Werte verwenden
            yScale = new double[]{32.132, 249.41, 0.0, 40.0}; // topY, bottomY, topPercent, bottomPercent
            logger.info("Verwende Fallback-Y-Achsen-Skala");
        }
        
        // X-Achsen-Daten extrahieren (für die Datumswerte)
        LocalDate[] dateRange = extractXAxisDates(svgContent);
        if (dateRange == null) {
            logger.warn("Konnte keinen Datumsbereich aus dem SVG extrahieren");
            // Fallback-Werte verwenden (Februar 2025)
            dateRange = new LocalDate[]{
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28)
            };
            logger.info("Verwende Fallback-Datumsbereich: " + dateRange[0] + " bis " + dateRange[1]);
        }
        
        // Roten Pfad mit verbessertem Pattern extrahieren
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
        
        // Bereichsgrenzen für X-Koordinaten bestimmen
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        for (double[] point : pathPoints) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
        }
        
        logger.info(String.format("X-Bereich: minX=%.2f, maxX=%.2f", minX, maxX));
        
        // Tagesabstand berechnen
        long daysBetween = ChronoUnit.DAYS.between(dateRange[0], dateRange[1]);
        
        // Punkte in ChartPoint-Objekte umwandeln
        for (double[] point : pathPoints) {
            double x = point[0];
            double y = point[1];
            
            // X-Koordinate in Datum umwandeln
            double fractionX = (x - minX) / (maxX - minX);
            fractionX = Math.min(1.0, Math.max(0.0, fractionX)); // Auf [0,1] begrenzen
            long daysToAdd = Math.round(fractionX * daysBetween);
            LocalDate date = dateRange[0].plusDays(daysToAdd);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Y-Koordinate in Drawdown-Prozent umwandeln
            double drawdownPercent = transformYToValue(y, yScale);
            
            chartData.add(new ChartPoint(dateStr, drawdownPercent));
        }
        
        logger.info("Extrahierte Punkte: " + chartData.size());
        return chartData;
    }
    
    private String extractRedPathData(String svgContent) {
        // Primäres Pattern versuchen
        Matcher matcher = RED_DRAWNDOWN_CHART_PATTERN.matcher(svgContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Alternative Pattern versuchen
        matcher = ALT_RED_LINE_PATTERN.matcher(svgContent);
        if (matcher.find()) {
            // Überprüfen, ob es sich um die rote Linie handelt (anhand des style-Attributs)
            String fullMatch = matcher.group(0);
            if (fullMatch.contains("stroke: var(--c-chart-red)") || 
                fullMatch.contains("stroke:var(--c-chart-red)")) {
                return matcher.group(1);
            }
        }
        
        // Direktes Suchen des Pfades mit der Klasse und dem Style
        Document doc = Jsoup.parse(svgContent);
        Elements paths = doc.select("path[style*=stroke:var(--c-chart-red)], path[style*=stroke: var(--c-chart-red)]");
        if (!paths.isEmpty()) {
            return paths.first().attr("d");
        }
        
        return null;
    }
    
    private List<double[]> parsePathData(String pathData) {
        List<double[]> points = new ArrayList<>();
        
        // SVG-Pfad-Befehle: M (move to), L (line to), C (curve to), etc.
        String[] segments = pathData.split("(?=[MLCQAZHVmlcqazhv])");
        
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            
            char command = segment.charAt(0);
            String params = segment.substring(1).trim();
            
            if (command == 'M' || command == 'L') {
                // Format: M x,y or L x,y
                String[] coords = params.split("[,\\s]+");
                if (coords.length >= 2) {
                    try {
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);
                        points.add(new double[]{x, y});
                    } catch (NumberFormatException e) {
                        logger.warn("Fehler beim Parsen der Koordinaten: " + params);
                    }
                }
            }
        }
        
        return points;
    }
    
    private double[] extractYAxisScale(String svgContent) {
        // Muster: Y-Achsenbeschriftungen mit Prozentwerten extrahieren
        Matcher matcher = Y_AXIS_TICK_PATTERN.matcher(svgContent);
        
        double topY = -1;
        double topPercent = -1;
        double bottomY = -1;
        double bottomPercent = -1;
        
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
    
    private LocalDate[] extractXAxisDates(String svgContent) {
        Matcher matcher = X_AXIS_TICK_PATTERN.matcher(svgContent);
        
        LocalDate startDate = null;
        LocalDate endDate = null;
        double startX = -1;
        double endX = -1;
        
        while (matcher.find()) {
            try {
                double x = Double.parseDouble(matcher.group(1));
                String month = matcher.group(2);
                int year = Integer.parseInt(matcher.group(3));
                
                int monthNum = getMonthNumber(month);
                if (monthNum > 0) {
                    LocalDate date = LocalDate.of(year, monthNum, 1);
                    
                    if (startDate == null || x < startX) {
                        startDate = date;
                        startX = x;
                    }
                    
                    if (endDate == null || x > endX) {
                        endDate = date.plusMonths(1).minusDays(1); // Letzter Tag des Monats
                        endX = x;
                    }
                }
            } catch (Exception e) {
                logger.warn("Fehler beim Parsen der X-Achsen-Ticks: " + e.getMessage());
            }
        }
        
        if (startDate != null && endDate != null) {
            logger.info("Datumsbereich extrahiert: " + startDate + " bis " + endDate);
            return new LocalDate[]{startDate, endDate};
        }
        
        // Direktes Extrahieren mit JSoup
        try {
            Document doc = Jsoup.parse(svgContent);
            Elements ticks = doc.select("g.s-tick text");
            
            for (Element tick : ticks) {
                String text = tick.text();
                if (text.matches("[A-Za-z]+\\s+\\d{4}")) {
                    String[] parts = text.split("\\s+");
                    String month = parts[0];
                    int year = Integer.parseInt(parts[1]);
                    
                    Element parent = tick.parent();
                    String transform = parent.attr("transform");
                    if (transform.startsWith("translate(")) {
                        String xStr = transform.substring("translate(".length(), transform.indexOf(","));
                        double x = Double.parseDouble(xStr);
                        
                        int monthNum = getMonthNumber(month);
                        if (monthNum > 0) {
                            LocalDate date = LocalDate.of(year, monthNum, 1);
                            
                            if (startDate == null || x < startX) {
                                startDate = date;
                                startX = x;
                            }
                            
                            if (endDate == null || x > endX) {
                                endDate = date.plusMonths(1).minusDays(1);
                                endX = x;
                            }
                        }
                    }
                }
            }
            
            if (startDate != null && endDate != null) {
                logger.info("Datumsbereich mit JSoup extrahiert: " + startDate + " bis " + endDate);
                return new LocalDate[]{startDate, endDate};
            }
        } catch (Exception e) {
            logger.warn("Fehler bei JSoup-Extraktion des Datumsbereichs: " + e.getMessage());
        }
        
        return null;
    }
    
    private int getMonthNumber(String monthName) {
        monthName = monthName.toLowerCase();
        switch (monthName) {
            case "jan": return 1;
            case "feb": return 2;
            case "mar": return 3;
            case "apr": return 4;
            case "may": return 5;
            case "jun": return 6;
            case "jul": return 7;
            case "aug": return 8;
            case "sep": return 9;
            case "oct": return 10;
            case "nov": return 11;
            case "dec": return 12;
            default: return -1;
        }
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
        return topPercent + fraction * (bottomPercent - topPercent);
    }
}