package rest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParserTest {

    @Test
    void parsesSemicolonSeparatedRows() {
        List<String> lines = Arrays.asList(
                "Open Time;Symbol;Type;Volume",
                "2025.01.02 10:00:00;EURUSD;buy;0.10",
                "2025.01.03 11:30:00;GBPUSD;sell;0.20");
        CsvParser parser = CsvParser.parseLines(new ArrayList<>(lines));

        assertEquals(';', parser.getDelimiter());
        assertEquals(4, parser.getHeader().size());
        assertEquals("Open Time", parser.getHeader().get(0));
        assertEquals(2, parser.getRows().size());
        Map<String, String> first = parser.getRows().get(0);
        assertEquals("2025.01.02 10:00:00", first.get("Open Time"));
        assertEquals("buy", first.get("Type"));
        assertEquals("0.10", first.get("Volume"));
    }

    @Test
    void parsesCommaSeparatedRows() {
        List<String> lines = Arrays.asList(
                "a,b,c",
                "1,2,3");
        CsvParser parser = CsvParser.parseLines(new ArrayList<>(lines));
        assertEquals(',', parser.getDelimiter());
        assertEquals("2", parser.getRows().get(0).get("b"));
    }

    @Test
    void quotedFieldsMayContainDelimiterAndNewline() {
        List<String> lines = Arrays.asList(
                "id;text",
                "1;\"Hallo; Welt\"",
                "2;\"Zeile eins\nZeile zwei\"");
        CsvParser parser = CsvParser.parseLines(new ArrayList<>(lines));
        assertEquals(2, parser.getRows().size());
        assertEquals("Hallo; Welt", parser.getRows().get(0).get("text"));
        assertEquals("Zeile eins\nZeile zwei", parser.getRows().get(1).get("text"));
    }

    @Test
    void doubledQuotesInsideQuotedField() {
        List<String> lines = Arrays.asList(
                "a",
                "\"er sagte \"\"hi\"\"\"");
        CsvParser parser = CsvParser.parseLines(new ArrayList<>(lines));
        assertEquals("er sagte \"hi\"", parser.getRows().get(0).get("a"));
    }

    @Test
    void stripsBomAndSkipsLeadingEmptyLines() {
        List<String> lines = new ArrayList<>();
        lines.add("\uFEFFcol");
        lines.add("");
        lines.add("value");
        CsvParser parser = CsvParser.parseLines(lines);
        assertEquals(1, parser.getRows().size());
        assertEquals("value", parser.getRows().get(0).get("col"));
    }

    @Test
    void missingColumnsBecomeEmpty() {
        List<String> lines = Arrays.asList(
                "a;b;c",
                "nur-eins");
        CsvParser parser = CsvParser.parseLines(new ArrayList<>(lines));
        Map<String, String> row = parser.getRows().get(0);
        assertEquals("nur-eins", row.get("a"));
        assertEquals("", row.get("b"));
        assertEquals("", row.get("c"));
    }

    @Test
    void emptyInputYieldsNoRows() {
        CsvParser parser = CsvParser.parseLines(new ArrayList<String>());
        assertEquals(0, parser.getHeader().size());
        assertEquals(0, parser.getRows().size());
    }
}
