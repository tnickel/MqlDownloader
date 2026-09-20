package rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesMetricsMonthsAndDrawdown() throws Exception {
        String content = "Balance=1234.56\n"
                + "Subscribers=42\n"
                + "MaxDDGraphic=10.00\n"
                + "EquityDrawdown=8.50\n"
                + "Average3MonthProfit=2.25\n"
                + "StabilityValue=0.75\n"
                + "MonthProfitProz=2025.06=1.5,2025.07=-0.5,2025.08=3.25\n"
                + "3MPDD=0.8123\n"
                + "********************************\n"
                + "\n"
                + "Drawdown Chart Data=\n"
                + "2025.01.05: 1.23%\n"
                + "2025.02.05: 0.50%\n"
                + "-----------------\n"
                + "\n"
                + "********************************\n"
                + "\n"
                + "Last 3 Months Details=\n"
                + "2025.06: +1.5\n"
                + "********************************";
        File txt = tempDir.resolve("P_123_root.txt").toFile();
        Files.write(txt.toPath(), content.getBytes(StandardCharsets.UTF_8));

        MetricsParser parser = MetricsParser.parse(txt);
        Map<String, String> metrics = parser.getMetrics();
        assertEquals("1234.56", metrics.get("Balance"));
        assertEquals("42", metrics.get("Subscribers"));
        assertEquals("8.50", metrics.get("EquityDrawdown"));
        assertEquals("0.8123", metrics.get("3MPDD"));

        assertEquals(3, parser.getMonthProfits().size());
        assertEquals("1.5", parser.getMonthProfits().get("2025.06"));
        assertEquals("-0.5", parser.getMonthProfits().get("2025.07"));

        assertEquals(2, parser.getDrawdownPoints().size());
        assertEquals("2025.01.05", parser.getDrawdownPoints().get(0).getDate());
        assertEquals("1.23", parser.getDrawdownPoints().get(0).getValue());
    }

    @Test
    void handlesEmptyDrawdownSection() throws Exception {
        String content = "Balance=1.00\n"
                + "********************************\n"
                + "\n"
                + "Drawdown Chart Data=\n"
                + "Keine roten Drawdown-Pfad-Daten gefunden\n";
        File txt = tempDir.resolve("P_9_root.txt").toFile();
        Files.write(txt.toPath(), content.getBytes(StandardCharsets.UTF_8));

        MetricsParser parser = MetricsParser.parse(txt);
        assertTrue(parser.getDrawdownPoints().isEmpty());
        assertEquals("1.00", parser.getMetrics().get("Balance"));
    }
}
