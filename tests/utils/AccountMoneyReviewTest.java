package utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccountMoneyReviewTest {
    @TempDir Path dir;
    Path html(String name, String rows) throws Exception {
        Path file = dir.resolve(name + ".html"); Files.write(file, rows.getBytes(StandardCharsets.UTF_8)); return file;
    }
    String metric(String label, String value) {
        return "<div class='s-list-info__item'><div class='s-list-info__label'>" + label
                + "</div><div class='s-list-info__value'>" + value + "</div></div>";
    }
    DataExtractor extractor() { return new DataExtractor(new HtmlContentCache(dir.toString()), null); }

    @Test void groupedGermanAndEnglishAmountsAndSignsArePreserved() throws Exception {
        assertEquals(23456.78, extractor().getBalance(html("de", metric("Kontostand:", "23.456,78 EUR")).toString()));
        assertEquals(23456.78, extractor().getBalance(html("en", metric("Balance:", "23,456.78 USD")).toString()));
        assertEquals(-12.50, extractor().getBalance(html("minus", metric("Balance:", "−12,50 USD")).toString()));
        assertEquals(1234.56, extractor().getBalance(html("space", metric("Balance:", "1&#160;234.56 USC")).toString()));
    }

    @Test void currencyComesFromExactAccountRowAndIsNeverInvented() throws Exception {
        for (String currency : new String[]{"USD", "USC", "EUR"}) {
            Path p = html(currency, metric("Balance:", "1234.56 " + currency));
            assertEquals(currency, DataExtractor.class.getMethod("getCurrency", String.class).invoke(extractor(), p.toString()));
        }
        for (String value : new String[]{"1234.56", "1234.56 USD / EUR"}) {
            Path p = html("unknown" + value.length(), metric("Balance:", value));
            assertEquals("unknown", DataExtractor.class.getMethod("getCurrency", String.class).invoke(extractor(), p.toString()));
        }
    }

    @Test void proseMentioningBalanceCannotReplaceTheActualMetric() throws Exception {
        Path p = html("prose", metric("Description:", "Balance: 99999 EUR") + metric("Balance:", "100 USD"));
        assertEquals(100, extractor().getBalance(p.toString()));
    }

    @Test void accountLabelDoesNotRequireAnInventedCssClass() throws Exception {
        Path p = html("labelmarkup", "<div class='s-list-info__item'>Balance: "
                + "<span class='s-list-info__value'>42 USD</span></div>");
        assertEquals(42, extractor().getBalance(p.toString()));
        assertEquals("USD", extractor().getCurrency(p.toString()));
    }

    @Test void conversionPersistsNativeUnitAndActualMonthCount() throws Exception {
        Files.createDirectories(dir.resolve("mql4"));
        Files.createDirectories(dir.resolve("mql5"));
        String months = "<table><tr><td>2026</td><td>8%</td><td>12%</td><td>20%</td>"
                + "<td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>";
        Path source = dir.resolve("mql5/Synthetic_123_root.html");
        Files.write(source, (metric("Balance:", "1,234.56 USC") + months
                + "Maximaler Rückgang: 5%").getBytes(StandardCharsets.UTF_8));
        varConverter(dir);
        Path metrics = dir.resolve("mql5/Synthetic_123_root.txt");
        assertTrue(Files.exists(metrics));
        varMetrics(metrics);
    }

    private void varConverter(Path path) {
        converter.HtmlConverter converter = new converter.HtmlConverter(path.toString());
        converter.convertAllHtmlFiles();
        assertEquals(0, converter.getFailedProvidersCount());
    }

    private void varMetrics(Path path) throws Exception {
        java.util.Map<String, String> metrics = rest.MetricsParser.parse(path.toFile()).getMetrics();
        assertEquals("USC", metrics.get("Currency"));
        assertEquals(1234.56, Double.parseDouble(metrics.get("Balance").replace(',', '.')));
        assertEquals("2", metrics.get("Average3MonthProfitMonthsIncluded"));
        assertEquals(10., Double.parseDouble(metrics.get("Average3MonthProfit").replace(',', '.')));
    }
}
