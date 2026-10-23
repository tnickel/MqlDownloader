package rest;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Read-only age evidence from the identified, downloaded MQL profile page. */
final class ProviderAge {
    static final String SOURCE = "mql_profile_cached_weeks_lower_bound";

    // A catalogue request visits hundreds of large pages. Retain only the small
    // result, including unknowns, and invalidate whenever the source file changes.
    private final Map<String, Snapshot> cache = new LinkedHashMap<String, Snapshot>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Snapshot> entry) {
            return size() > 512;
        }
    };

    synchronized Integer weeks(File downloadDir, String signalId, String version) {
        String folder = ProviderFiles.normalizeVersionFolder(version);
        if (folder == null || signalId == null || !signalId.matches("\\d+")) return null;
        File html = ProviderFiles.resolveRootHtml(downloadDir, signalId, folder);
        if (html == null) return null;
        try {
            String key = html.getCanonicalPath() + ":" + folder;
            java.nio.file.attribute.BasicFileAttributes attributes = java.nio.file.Files.readAttributes(
                    html.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
            String stamp = attributes.lastModifiedTime().toString() + ":" + attributes.size();
            Snapshot previous = cache.get(key);
            if (previous != null && stamp.equals(previous.stamp)) return previous.weeks;
            Integer weeks = parse(Jsoup.parse(html, null), signalId, folder);
            cache.put(key, new Snapshot(stamp, weeks));
            return weeks;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    static Integer parse(Document doc, String signalId, String folder) {
        if (!ProviderFiles.htmlIdentity(doc, signalId, folder)) return null;
        // A copy button may also occur in recommendations. The page itself
        // must identify this signal before its separate weeks indicator counts.
        Elements canonical = doc.select("link[rel=canonical][href]");
        if (canonical.size() != 1 || !canonical.first().attr("href").matches(
                "https://(?:www\\.)?mql5\\.com/(?:[a-z]{2}/)?signals/" + signalId + "/?")) return null;
        // This is the profile's structured weeks indicator, in all page languages.
        // Do not search free text (names, recommendations, titles) for a number.
        Elements indicators = doc.select(".s-indicators__item_weeks > .s-indicators__item-wrapper"
                + " > .s-indicators__item-desc_weeks");
        if (indicators.size() != 1) return null;
        Element indicator = indicators.first();
        if (indicator.childrenSize() != 1 || !"span".equals(indicator.child(0).tagName())
                || indicator.child(0).childrenSize() != 0) return null;
        String value = indicator.child(0).text().trim();
        if (!value.matches("\\d+")) return null;
        try {
            // The stored site value is a lower bound. File age never adds weeks.
            return Integer.valueOf(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static final class Snapshot {
        final String stamp;
        final Integer weeks;
        Snapshot(String stamp, Integer weeks) { this.stamp = stamp; this.weeks = weeks; }
    }
}
