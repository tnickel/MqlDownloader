package rest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderAgeTest {
    @TempDir Path tempDir;

    private static String profile(String id, String mt, String weeks) {
        return "<link rel='canonical' href='https://www.mql5.com/en/signals/" + id + "'>"
                + "<div data-id='" + id + "' data-mt='" + mt + "'></div>" + indicator(weeks);
    }

    private static String indicator(String weeks) {
        return "<div class='s-indicators__item s-indicators__item_weeks'>"
                + "<div class='s-indicators__item-wrapper'>"
                + "<span class='s-indicators__item-desc s-indicators__item-desc_weeks'>"
                + "<span>" + weeks + "</span> Wochen</span></div></div>";
    }

    @Test void readsStoredWeeksInBothPlatformsAndKeepsGenuinelyYoungSignalsYoung() {
        assertEquals(87, ProviderAge.parse(Jsoup.parse(profile("123", "4", "87")), "123", "mql4"));
        assertEquals(6, ProviderAge.parse(Jsoup.parse(profile("123", "5", "6")), "123", "mql5"));
        assertEquals(0, ProviderAge.parse(Jsoup.parse(profile("123", "5", "0")), "123", "mql5"));
    }

    @Test void rejectsMismatchedMissingOrContradictoryIdentity() {
        String[] invalid = {
                profile("999", "5", "80"), profile("123", "4", "80"), indicator("80"),
                "<div data-id='123' data-mt='5'></div>" + indicator("80"),
                profile("123", "5", "80") + "<div data-id='123' data-mt='4'></div>",
                "<title>MetaTrader 4</title>" + profile("123", "5", "80"),
                "<link rel='canonical' href='/en/signals/999'>" + profile("123", "5", "80")
        };
        for (String html : invalid) assertNull(ProviderAge.parse(Jsoup.parse(html), "123", "mql5"));
    }

    @Test void rejectsUnknownMalformedOverflowAndMultipleIndicators() {
        for (String value : new String[]{"", "unknown", "-1", "26.5", "NaN", "2147483648", "26 Weeks"}) {
            assertNull(ProviderAge.parse(Jsoup.parse(profile("123", "5", value)), "123", "mql5"));
        }
        assertNull(ProviderAge.parse(Jsoup.parse(profile("123", "5", "80") + indicator("90")), "123", "mql5"));
        assertNull(ProviderAge.parse(Jsoup.parse(profile("123", "5", "<b>80</b>")), "123", "mql5"));
    }

    @Test void arbitraryNumbersLabelsOrRecommendedCardAttributesAreNotAgeEvidence() {
        String page = "<title>Weeks: 999</title><div data-id='123' data-mt='5'></div>"
                + "<div data-id='999' data-mt='5' data-weeks='800'></div><div>Weeks: 600</div>";
        assertNull(ProviderAge.parse(Jsoup.parse(page), "123", "mql5"));
    }

    @Test void fileTimeNeverAdvancesAgeAndChangedEvidenceInvalidatesCache() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("mql5"));
        Path file = dir.resolve("Strategy_123_root.html");
        Files.write(file, profile("123", "5", "6").getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(946684800000L));
        ProviderAge reader = new ProviderAge();
        assertEquals(6, reader.weeks(tempDir.toFile(), "123", "mql5"));
        Files.write(file, profile("123", "5", "27").getBytes(StandardCharsets.UTF_8));
        assertEquals(27, reader.weeks(tempDir.toFile(), "123", "mql5"));
        Files.write(file, profile("123", "4", "27").getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 1000));
        assertNull(reader.weeks(tempDir.toFile(), "123", "mql5"));
        Files.delete(file);
        assertNull(reader.weeks(tempDir.toFile(), "123", "mql5"));
    }

    @Test void exactFilenameIdAndVersionAreRequiredAndProfilesNeedNoMetrics() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("mql5"));
        Files.write(dir.resolve("Account_1_123_root.html"), profile("123", "5", "80").getBytes(StandardCharsets.UTF_8));
        ProviderAge reader = new ProviderAge();
        assertEquals(80, reader.weeks(tempDir.toFile(), "123", "mt5"));
        assertNull(reader.weeks(tempDir.toFile(), "1", "mql5"));
        assertNull(reader.weeks(tempDir.toFile(), "123", "mql4"));
        assertNull(reader.weeks(tempDir.toFile(), "../123", "mql5"));
    }

    @Test void archivedProfileKeepsExistingUniqueIdentityAndFormatGate() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("archive"));
        Files.write(dir.resolve("P_123_root.html"), profile("123", "5", "80").getBytes(StandardCharsets.UTF_8));
        ProviderAge reader = new ProviderAge();
        assertNull(reader.weeks(tempDir.toFile(), "123", "mql5"));
        String header = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n";
        Files.write(dir.resolve("P_123.csv"), header.getBytes(StandardCharsets.UTF_8));
        assertEquals(80, reader.weeks(tempDir.toFile(), "123", "mql5"));
        assertNull(reader.weeks(tempDir.toFile(), "123", "mql4"));
    }
}
