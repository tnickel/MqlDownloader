package rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generischer Parser für die heruntergeladenen Trading-History-CSV-Dateien.
 * Erkennt das Trennzeichen (Semikolon, Komma oder Tabulator) anhand der
 * Kopfzeile, versteht Anführungszeichen nach RFC 4180 (inklusive mehrzeiliger
 * Zellen) und liefert die Zeilen als Maps mit den Spaltenüberschriften als
 * Schlüssel. Die erste inhaltstragende Zeile wird als Kopfzeile erwartet.
 */
public final class CsvParser {
    private static final char[] DELIMITER_CANDIDATES = {';', ',', '\t'};

    private final List<String> header;
    private final List<Map<String, String>> rows;
    private final char delimiter;

    private CsvParser(List<String> header, List<Map<String, String>> rows, char delimiter) {
        this.header = header;
        this.rows = rows;
        this.delimiter = delimiter;
    }

    public static CsvParser parse(File file) throws IOException {
        List<String> lines = readLines(file, StandardCharsets.UTF_8);
        if (containsReplacementChar(lines)) {
            // Wahrscheinlich keine UTF-8-Datei: zweiter Versuch mit ISO-8859-1
            lines = readLines(file, StandardCharsets.ISO_8859_1);
        }
        return parseLines(lines);
    }

    public static CsvParser parseLines(List<String> rawLines) {
        StringBuilder content = new StringBuilder();
        for (String line : rawLines) {
            content.append(stripBom(line)).append('\n');
        }

        char delimiter = detectDelimiter(rawLines);
        List<List<String>> records = tokenize(content, delimiter);

        List<String> header = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        if (!records.isEmpty()) {
            List<String> headerRecord = records.remove(0);
            for (int i = 0; i < headerRecord.size(); i++) {
                String name = headerRecord.get(i).trim();
                header.add(name.isEmpty() ? ("column_" + (i + 1)) : name);
            }
            for (List<String> record : records) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    String value = i < record.size() ? record.get(i) : "";
                    row.put(header.get(i), value.trim());
                }
                rows.add(row);
            }
        }
        return new CsvParser(header, rows, delimiter);
    }

    public List<String> getHeader() {
        return header;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public char getDelimiter() {
        return delimiter;
    }

    /** Zerlegt den gesamten Inhalt in Datensätze; Anführungszeichen schützen Trenner und Zeilenenden. */
    private static List<List<String>> tokenize(StringBuilder content, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    boolean isDoubled = i + 1 < content.length() && content.charAt(i + 1) == '"';
                    if (isDoubled) {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                record.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                record.add(cell.toString());
                cell.setLength(0);
                if (hasContent(record)) {
                    records.add(record);
                }
                record = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !record.isEmpty()) {
            record.add(cell.toString());
            if (hasContent(record)) {
                records.add(record);
            }
        }
        return records;
    }

    private static boolean hasContent(List<String> record) {
        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static char detectDelimiter(List<String> lines) {
        int sampleLines = Math.min(lines.size(), 5);
        char best = ';';
        long bestCount = -1;
        for (char candidate : DELIMITER_CANDIDATES) {
            long count = 0;
            for (int i = 0; i < sampleLines; i++) {
                count += countUnquoted(lines.get(i), candidate);
            }
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    private static long countUnquoted(String line, char candidate) {
        long count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == candidate && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    private static String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private static List<String> readLines(File file, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Viele Ersetzungszeichen deuten darauf hin, dass UTF-8 die falsche Annahme war. */
    private static boolean containsReplacementChar(List<String> lines) {
        int checked = 0;
        for (String line : lines) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '\uFFFD') {
                    return true;
                }
            }
            checked += line.length();
            if (checked > 20000) {
                break;
            }
        }
        return false;
    }
}
