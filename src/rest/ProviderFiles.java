package rest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * Findet die zum Dateisatz eines Signals gehörenden Dateien: Trading-History
 * (CSV), konvertierte Kennzahlen (*_root.txt) und Testreport-PDFs. Die
 * CSV und Kennzahlen werden über die abschließende Signal-ID zugeordnet;
 * Zahlen im frei gewählten Signalnamen sind keine Identität. Reports haben
 * eigene Dateinamen und verwenden weiterhin das Zifferngrenzen-Muster.
 * Alle Ergebnisse sind Dateien innerhalb des jeweiligen Basisverzeichnisses;
 * Fremd-Pfade werden abgelehnt.
 */
public final class ProviderFiles {

    private ProviderFiles() {
    }

    /** Normalisiert "mql4"/"mt4"/"MQL5"/... auf den Ordnernamen mql4 bzw. mql5. */
    public static String normalizeVersionFolder(String version) {
        if (version == null) {
            return null;
        }
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("mt4") || normalized.equals("mql4")) {
            return "mql4";
        }
        if (normalized.equals("mt5") || normalized.equals("mql5")) {
            return "mql5";
        }
        return null;
    }

    /** Neueste Trading-History-CSV für ein Signal, oder null. */
    public static File findTradeCsv(File versionDir, String signalId) {
        return latest(findBySignalId(versionDir, signalId, ".csv"));
    }

    /** Konvertierte Kennzahlendatei {@code *_root.txt}, oder null. */
    public static File findRootTxt(File versionDir, String signalId) {
        return latest(findBySignalId(versionDir, signalId, "_root.txt"));
    }

    /** Normaler Versionsordner gewinnt. Archive brauchen eigenen Identitätsbeleg. */
    public static File resolveTradeCsv(File downloadDir, String signalId, String version) {
        String folder = normalizeVersionFolder(version);
        if (folder == null || downloadDir == null) return null;
        File normal = findTradeCsv(new File(downloadDir, folder), signalId);
        return normal != null ? normal : findArchiveCsv(downloadDir, signalId, folder);
    }

    /** Archiv-Kennzahlen stammen ausschließlich aus demselben belegten Dateisatz. */
    public static File resolveRootTxt(File downloadDir, String signalId, String version) {
        String folder = normalizeVersionFolder(version);
        if (folder == null || downloadDir == null) return null;
        File normalDir = new File(downloadDir, folder);
        File normal = findRootTxt(normalDir, signalId);
        // Kein älterer Archiv-Metrics-Fallback neben einer regulären Trade-CSV.
        if (findTradeCsv(normalDir, signalId) != null) return normal;
        File csv = findArchiveCsv(downloadDir, signalId, folder);
        if (csv == null) return normal;
        // Bei Archiv-Trades keine unabhängigen regulären Metrics beimischen.
        File txt = new File(csv.getParentFile(), stripExtension(csv.getName()) + "_root.txt");
        return txt.isFile() && isDirectChild(csv.getParentFile(), txt) ? txt : null;
    }

    /** Recover only a missing legacy unit from the same, identified HTML account row.
     * Matching balances bind the HTML evidence to the native money in the TXT;
     * a newer/different account snapshot cannot silently supply its unit.
     * This is a read-only fallback: GET never rewrites source files.
     */
    public static String legacyAccountCurrency(File rootTxt, String signalId, String version,
                                                String txtBalance) {
        String folder = normalizeVersionFolder(version);
        if (rootTxt == null || folder == null || signalId == null || txtBalance == null
                || !rootTxt.getName().toLowerCase(Locale.ROOT).endsWith("_root.txt")
                || !signalId.equals(extractSignalId(rootTxt.getName()))) return "unknown";
        File parent = rootTxt.getAbsoluteFile().getParentFile();
        String stem = rootTxt.getName().substring(0, rootTxt.getName().length() - "_root.txt".length());
        File html = new File(parent, stem + "_root.html");
        if (!html.isFile() || !isDirectChild(parent, html)) return "unknown";
        try {
            utils.HtmlContentCache cache = new utils.HtmlContentCache(parent.toString());
            String content = cache.getHtmlContent(html.toString());
            if (content == null || !htmlIdentity(Jsoup.parse(content), signalId, folder)) return "unknown";
            utils.DataExtractor extractor = new utils.DataExtractor(cache, null);
            String currency = extractor.getCurrency(html.toString());
            if ("unknown".equals(currency)) return currency;
            java.math.BigDecimal converted = new java.math.BigDecimal(txtBalance.trim().replace(',', '.'));
            java.math.BigDecimal original = java.math.BigDecimal.valueOf(extractor.getBalance(html.toString()));
            return converted.compareTo(original) == 0 ? currency : "unknown";
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static File findArchiveCsv(File downloadDir, String signalId, String folder) {
        if (downloadDir == null || signalId == null || !signalId.trim().matches("\\d+")) return null;
        List<File> candidates = new ArrayList<>();
        File[] dirs = downloadDir.listFiles(File::isDirectory);
        if (dirs == null) return null;
        for (File dir : dirs) {
            if (normalizeVersionFolder(dir.getName()) != null || !isDirectChild(downloadDir, dir)) continue;
            for (File csv : findBySignalId(dir, signalId, ".csv")) {
                if (isDirectChild(dir, csv) && archiveEvidence(csv, signalId.trim(), folder)) {
                    candidates.add(csv);
                }
            }
        }
        // Zwei belegte Historien sind ohne zusätzlichen Standbeleg nicht auswählbar.
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean archiveEvidence(File csv, String signalId, String folder) {
        File html = new File(csv.getParentFile(), stripExtension(csv.getName()) + "_root.html");
        if (!html.isFile() || !isDirectChild(csv.getParentFile(), html)) return false;
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html, null);
            if (!htmlIdentity(doc, signalId, folder)) return false;
            String expected = folder.equals("mql5")
                    ? "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit"
                    : "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment";
            try (java.io.BufferedReader reader = Files.newBufferedReader(csv.toPath(), StandardCharsets.UTF_8)) {
                String header = reader.readLine();
                return header != null && expected.equals(header.replace("\ufeff", "").trim());
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean htmlIdentity(org.jsoup.nodes.Document doc, String signalId, String folder) {
        String mt = folder.equals("mql5") ? "5" : "4";
        boolean found = false;
        for (Element tag : doc.select("[data-id][data-mt]")) {
            if (!signalId.equals(tag.attr("data-id"))) continue;
            if (!mt.equals(tag.attr("data-mt"))) return false;
            found = true;
        }
        if (!found) return false;
        java.util.regex.Matcher title = Pattern.compile("MetaTrader\\s+([45])", Pattern.CASE_INSENSITIVE)
                .matcher(doc.title());
        while (title.find()) if (!mt.equals(title.group(1))) return false;
        for (Element canonical : doc.select("link[rel=canonical][href]")) {
            java.util.regex.Matcher id = Pattern.compile("/signals/(\\d+)(?:[/?#].*)?$")
                    .matcher(canonical.attr("href"));
            if (id.find() && !signalId.equals(id.group(1))) return false;
        }
        return true;
    }

    /** Testreport-PDFs des Analyse-Verzeichnisses, sortiert nach Dateiname. */
    public static List<File> findReports(String analysePath, String signalId) {
        if (analysePath == null || analysePath.trim().isEmpty() || signalId == null) {
            return Collections.emptyList();
        }
        File dir = new File(analysePath.trim());
        if (!dir.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> matches = new ArrayList<>();
        for (File file : listFiles(dir)) {
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    && idPattern(signalId).matcher(file.getName()).find()) {
                matches.add(file);
            }
        }
        matches.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return matches;
    }

    /** Alle Trading-History-CSVs beider Versionsordner, ohne Zuordnung zu Signalen. */
    public static List<File> listTradeCsvs(File versionDir) {
        List<File> result = new ArrayList<>();
        for (File file : listFiles(versionDir)) {
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                result.add(file);
            }
        }
        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    /**
     * Extrahiert die Signal-ID aus einem Dateinamen der Form
     * {@code <Name>_<ID>.csv} bzw. {@code <Name>_<ID>_root.txt}.
     */
    public static String extractSignalId(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = stripExtension(fileName);
        if (name.toLowerCase(Locale.ROOT).endsWith("_root")) {
            name = name.substring(0, name.length() - "_root".length());
        }
        int underscore = name.lastIndexOf('_');
        if (underscore < 0 || underscore == name.length() - 1) {
            return null;
        }
        String candidate = name.substring(underscore + 1);
        return candidate.matches("\\d+") ? candidate : null;
    }

    /** Sicherheit: Kandidat muss eine direkte Datei im Basisverzeichnis sein. */
    public static boolean isDirectChild(File baseDir, File candidate) {
        try {
            return candidate.getCanonicalPath().startsWith(baseDir.getCanonicalPath() + File.separator)
                    && candidate.getParentFile() != null
                    && candidate.getParentFile().getCanonicalPath().equals(baseDir.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    /** Datei nur akzeptieren, wenn sie exakt (ohne Pfadanteile) in der Liste liegt. */
    public static File findByName(List<File> files, String requestedName) {
        if (requestedName == null || requestedName.indexOf('/') >= 0
                || requestedName.indexOf('\\') >= 0 || requestedName.contains("..")) {
            return null;
        }
        for (File file : files) {
            if (file.getName().equals(requestedName)) {
                return file;
            }
        }
        // Fallschutz zweiter Ebene: niemals außerhalb der Liste auflösen
        return null;
    }

    private static List<File> findBySignalId(File dir, String signalId, String suffix) {
        if (dir == null || !dir.isDirectory() || signalId == null || signalId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<File> matches = new ArrayList<>();
        for (File file : listFiles(dir)) {
            String name = file.getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))
                    && signalId.trim().equals(extractSignalId(name))) {
                matches.add(file);
            }
        }
        return matches;
    }

    private static Pattern idPattern(String signalId) {
        return Pattern.compile("(?<!\\d)" + Pattern.quote(signalId.trim()) + "(?!\\d)");
    }

    private static List<File> listFiles(File dir) {
        File[] files = dir.listFiles(File::isFile);
        return files != null ? Arrays.asList(files) : Collections.<File>emptyList();
    }

    private static File latest(List<File> files) {
        File best = null;
        for (File file : files) {
            if (best == null || file.lastModified() > best.lastModified()) {
                best = file;
            }
        }
        return best;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
