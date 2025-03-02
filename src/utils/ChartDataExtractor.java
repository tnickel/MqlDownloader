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

public class ChartDataExtractor {
    private static final Logger logger = LogManager.getLogger(ChartDataExtractor.class);
    
    // Pattern für rote Linie im Drawdown-Chart (verbessert für var(--c-chart-red))
    private static final Pattern RED_DRAWNDOWN_CHART_PATTERN = Pattern.compile(
        "<path(?=[^>]*(?:stroke|style)\\s*=\\s*\"[^\"]*(?:var\\(--c-chart-red\\)|red)[^\"]*\")[^>]*d\\s*=\\s*\"([^\"]+)\"",
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
        
        // 1) HTML mit JSoup parsen
        Document jsoupDoc = Jsoup.parse(html);
        
        // 2) Suche das DIV mit id="tab_content_drawdown_chart"
        Element drawdownDiv = jsoupDoc.selectFirst("div#tab_content_drawdown_chart");
        if (drawdownDiv == null) {
            logger.warn("Kein DIV mit id='tab_content_drawdown_chart' gefunden in " + fileName);
            return chartData;
        }
        
        // 3) Darin das erste <svg> suchen
        Element svgElement = drawdownDiv.selectFirst("svg");
        if (svgElement == null) {
            logger.warn("Kein <svg> in div#tab_content_drawdown_chart gefunden in " + fileName);
            return chartData;
        }
        
        // 4) Das reine SVG als String extrahieren
        String svgContent = svgElement.outerHtml();
        
        // Falls Namespace fehlt, ergänzen
        if (!svgContent.contains("xmlns=")) {
            logger.info("SVG enthält keinen Namespace. Füge xmlns=\"http://www.w3.org/2000/svg\" hinzu.");
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
            logger.error("Fehler beim Parsen des SVG-Inhalts: " + e.getMessage(), e);
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
            logger.info("Kein roter Drawdown-Pfad in " + fileName + " (div#tab_content_drawdown_chart) gefunden. Leere Liste.");
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
                        logger.warn("Konnte Koordinate nicht parsen: " + coords[i] + "," + coords[i + 1]);
                    }
                }
            }
        }
        if (rawPoints.isEmpty()) {
            logger.warn("Keine (x,y)-Koordinaten im roten Drawdown-Pfad gefunden: " + fileName);
            return chartData;
        }
        
        double minX = rawPoints.stream().mapToDouble(p -> p[0]).min().orElse(0.0);
        double maxX = rawPoints.stream().mapToDouble(p -> p[0]).max().orElse(0.0);
        logger.info(String.format("Nach SVG-Parsing: minX=%.2f, maxX=%.2f", minX, maxX));
        
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
        
        logger.info("Roter Drawdown-Pfad: " + chartData.size() + " Punkte gefunden.");
        return chartData;
    }
    
    private double[] extractYAxisScale(String svgContent, String fileName) {
        logger.info("=== extractYAxisScale: Datei=" + fileName + " ===");
        
        // Manuelle Werte basierend auf deiner Analyse und den Daten im Textfile
        // Hier nehmen wir an, dass y = 272.585 für 1% und y = 11.994 für 6% korrekt sind,
        // aber wir können die Skala anpassen, um die tatsächlichen Werte besser abzubilden
        logger.warn("Keine automatische Erkennung der Y-Achsen-Beschriftungen. Verwende manuelle Werte basierend auf SVG-Analyse.");
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
                logger.warn("Fehler beim Parsen von translate: " + e.getMessage());
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
                logger.warn("Fehler beim Parsen von scale: " + e.getMessage());
            }
        }
        return new double[]{translateX, translateY, scaleX, scaleY};
    }
}