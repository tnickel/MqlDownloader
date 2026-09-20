package rest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Findet die zum Dateisatz eines Signals gehörenden Dateien: Trading-History
 * (CSV), konvertierte Kennzahlen (*_root.txt) und Testreport-PDFs. Die
 * Zuordnung läuft über die Signal-ID im Dateinamen, analog zur Erkennung in
 * der GUI (Muster mit Ziffern-Grenze, damit ID 25 nicht in 125 matcht).
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
        if (name.endsWith("_root")) {
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
        Pattern pattern = idPattern(signalId);
        for (File file : listFiles(dir)) {
            String name = file.getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))
                    && pattern.matcher(name).find()) {
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
