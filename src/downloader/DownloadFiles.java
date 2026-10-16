package downloader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import rest.ProviderFiles;

/** Exact provider/version identity for cache reuse and downloaded CSVs. */
public final class DownloadFiles {
    private DownloadFiles() { }
    public static boolean isRecent(File dir, String id, String name, String version, int days, long now) {
        if (days <= 0 || id == null || !id.matches("\\d+")) return false;
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        File html = new File(dir, safe + "_" + id + "_root.html");
        File csv = ProviderFiles.findTradeCsv(dir, id);
        long age = days * 86_400_000L;
        return html.isFile() && csv != null && validHeader(csv, version)
                && now >= html.lastModified() && now >= csv.lastModified()
                && now - html.lastModified() < age && now - csv.lastModified() < age;
    }
    public static boolean validHeader(File csv, String version) {
        String folder = ProviderFiles.normalizeVersionFolder(version);
        String expected = "mql5".equals(folder)
                ? "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit"
                : "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment";
        if (folder == null) return false;
        try (java.io.BufferedReader reader = Files.newBufferedReader(csv.toPath(), StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            return header != null && expected.equals(header.replace("\ufeff", "").trim());
        } catch (IOException e) { return false; }
    }
    public static File findDownload(File dir, String id) {
        if (id == null || !id.matches("\\d+")) return null;
        File[] files = dir.listFiles((d,n) -> n.matches(java.util.regex.Pattern.quote(id) + "(?: \\(\\d+\\))?\\.csv"));
        return files == null ? null : Arrays.stream(files).filter(File::isFile)
                .max(Comparator.comparingLong(File::lastModified)).orElse(null);
    }
}
