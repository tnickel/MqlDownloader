package rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest die von {@code converter.HtmlConverter} erzeugten {@code *_root.txt}
 * Dateien aus. Erwartet wird oben ein Block {@code Schluessel=Wert}
 * (Balance, Subscribers, MaxDDGraphic, EquityDrawdown, Average3MonthProfit,
 * StabilityValue, MonthProfitProz, 3MPDD), danach Freitext-Abschnitte. Aus
 * MonthProfitProz werden die Monatswerte einzeln geparst, aus dem Abschnitt
 * "Drawdown Chart Data" die Datenpunkte.
 */
public final class MetricsParser {
    private final Map<String, String> metrics = new LinkedHashMap<>();
    private final Map<String, String> monthProfits = new LinkedHashMap<>();
    private final List<DrawdownPoint> drawdownPoints = new ArrayList<>();

    public static MetricsParser parse(File txtFile) throws IOException {
        MetricsParser parser = new MetricsParser();
        String section = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(txtFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("****") || trimmed.startsWith("----") || trimmed.isEmpty()) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                // Abschnittskopf wie "Drawdown Chart Data=" - aber nicht die
                // Leerwert-Variante von MonthProfitProz (die ist ein Metrik-Key).
                if (eq > 0 && trimmed.endsWith("=") && !trimmed.startsWith("MonthProfitProz")) {
                    section = trimmed.substring(0, eq).trim();
                    continue;
                }
                if (eq > 0 && section == null) {
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    if ("MonthProfitProz".equals(key)) {
                        parser.parseMonthProfits(value);
                    } else {
                        parser.metrics.put(key, value);
                    }
                    continue;
                }
                if ("Drawdown Chart Data".equals(section)) {
                    parser.parseDrawdownLine(trimmed);
                }
                // Andere Freitext-Abschnitte (Last 3 Months Details, Stability
                // Details) bleiben bewusst roh; sie sind über raw verfügbar.
            }
        }
        return parser;
    }

    /** Rohwerte aller geparsten Schluessel (Werte als Text). */
    public Map<String, String> getMetrics() {
        return metrics;
    }

    /** Monat -> Prozentwert, aus MonthProfitProz (Format "2025.06=1.2,2025.07=2.3"). */
    public Map<String, String> getMonthProfits() {
        return monthProfits;
    }

    public List<DrawdownPoint> getDrawdownPoints() {
        return drawdownPoints;
    }

    private void parseMonthProfits(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        for (String entry : value.split(",")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            monthProfits.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
    }

    /** Format: "2025.01.05: 1.23%" bzw. "2025.01.05 : 1.23%". */
    private void parseDrawdownLine(String line) {
        int sep = line.indexOf(':');
        if (sep <= 0) {
            return;
        }
        String date = line.substring(0, sep).trim();
        String value = line.substring(sep + 1).trim();
        if (value.endsWith("%")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (!date.isEmpty() && !value.isEmpty()) {
            drawdownPoints.add(new DrawdownPoint(date, value));
        }
    }

    public static final class DrawdownPoint {
        private final String date;
        private final String value;

        DrawdownPoint(String date, String value) {
            this.date = date;
            this.value = value;
        }

        public String getDate() {
            return date;
        }

        public String getValue() {
            return value;
        }
    }
}
