package gui;

import database.SubscriberStat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** File discovery failures must not discard otherwise usable subscriber statistics. */
final class ReportFiles {
    private ReportFiles() {
    }

    static ScanResult scan(String directory, List<SubscriberStat> stats) {
        List<SubscriberStatisticsDialog.TestReports> reports = new ArrayList<>(
                Collections.nCopies(stats.size(), SubscriberStatisticsDialog.TestReports.EMPTY));
        String path = directory != null ? directory.trim() : "";
        if (path.isEmpty()) {
            return new ScanResult(reports, "Analyse-Verzeichnis nicht konfiguriert (siehe Einstellungen)");
        }

        File[] files;
        try {
            files = new File(path).listFiles(file -> file.isFile()
                    && file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"));
        } catch (SecurityException ex) {
            files = null;
        }
        if (files == null) {
            return new ScanResult(reports, "Analyse-Verzeichnis nicht erreichbar (Pfad und Berechtigungen pr\u00fcfen)");
        }

        for (int i = 0; i < stats.size(); i++) {
            SubscriberStat stat = stats.get(i);
            Pattern pattern = signalIdPattern(stat != null ? stat.getSignalId() : null);
            if (pattern == null) {
                continue;
            }
            List<Path> matches = new ArrayList<>();
            for (File file : files) {
                if (pattern.matcher(file.getName()).find()) {
                    matches.add(file.toPath());
                }
            }
            matches.sort(Comparator.comparing(
                    file -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
            if (!matches.isEmpty()) {
                reports.set(i, new SubscriberStatisticsDialog.TestReports(matches));
            }
        }
        return new ScanResult(reports, null);
    }

    static boolean matchesSignalId(String fileName, String signalId) {
        Pattern pattern = signalIdPattern(signalId);
        return fileName != null && pattern != null && pattern.matcher(fileName).find();
    }

    private static Pattern signalIdPattern(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return null;
        }
        return Pattern.compile("(?<!\\d)" + Pattern.quote(signalId.trim()) + "(?!\\d)");
    }

    static final class ScanResult {
        final List<SubscriberStatisticsDialog.TestReports> reports;
        final String warning;

        ScanResult(List<SubscriberStatisticsDialog.TestReports> reports, String warning) {
            this.reports = reports;
            this.warning = warning;
        }
    }
}
