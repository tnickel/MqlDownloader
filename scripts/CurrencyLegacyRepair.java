import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import rest.MetricsParser;
import rest.ProviderFiles;

/** Local, idempotent legacy-unit repair. Dry run by default; never downloads or converts.
 * Uses the same signal/platform/account-balance proof as the REST fallback.
 * Native money and all original TXT bytes are preserved after an added Currency line.
 */
public class CurrencyLegacyRepair {
    static final Path ROOT = Paths.get("C:\\Forex\\MqlAnalyzer\\download");
    static String hash(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b));
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        boolean apply = false;
        Path sourceRoot = ROOT;
        Path backupRoot = Paths.get("tmp");
        for (String arg : args) {
            if (arg.equals("--apply")) apply = true;
            else if (arg.startsWith("--root=")) sourceRoot = Paths.get(arg.substring(7));
            else if (arg.startsWith("--backup-root=")) backupRoot = Paths.get(arg.substring(14));
            else throw new IllegalArgumentException("Expected --apply, --root=PATH or --backup-root=PATH");
        }
        Path backup = backupRoot.resolve("currency_legacy_backup_" + Instant.now().toEpochMilli());
        Map<String, Integer> counts = new TreeMap<>();
        int written = 0;
        for (String version : new String[]{"mql4", "mql5"}) {
            Path folder = sourceRoot.resolve(version);
            if (!Files.isDirectory(folder)) continue;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder, "*_root.txt")) {
                for (Path txt : entries) {
                    String id = ProviderFiles.extractSignalId(txt.getFileName().toString());
                    if (id == null) continue;
                    byte[] original = Files.readAllBytes(txt);
                    String text = new String(original, StandardCharsets.UTF_8);
                    if (java.util.regex.Pattern.compile("(?m)^\\s*Currency\\s*=").matcher(text).find()) {
                        counts.merge("existing_currency", 1, Integer::sum); continue;
                    }
                    if (text.startsWith("\ufeff")) { counts.merge("bom_skipped", 1, Integer::sum); continue; }
                    Path html = txt.resolveSibling(txt.getFileName().toString().replace("_root.txt", "_root.html"));
                    if (!Files.isRegularFile(html)) { counts.merge("missing_html", 1, Integer::sum); continue; }
                    byte[] htmlBefore = Files.readAllBytes(html);
                    String balance = MetricsParser.parse(txt.toFile()).getMetrics().get("Balance");
                    String currency = ProviderFiles.legacyAccountCurrency(txt.toFile(), id, version, balance);
                    if (!currency.matches("[A-Z]{3}")) { counts.merge("unproven", 1, Integer::sum); continue; }
                    counts.merge("eligible_" + version + "_" + currency, 1, Integer::sum);
                    if (!apply) continue;
                    if (!Arrays.equals(original, Files.readAllBytes(txt)) || !Arrays.equals(htmlBefore, Files.readAllBytes(html)))
                        throw new IllegalStateException("Source changed during validation for " + id);
                    Files.createDirectories(backup);
                    Path saved = backup.resolve(version + "_" + txt.getFileName());
                    Files.write(saved, original, StandardOpenOption.CREATE_NEW);
                    byte[] prefix = ("Currency=" + currency + "\n").getBytes(StandardCharsets.UTF_8);
                    byte[] updated = new byte[prefix.length + original.length];
                    System.arraycopy(prefix, 0, updated, 0, prefix.length);
                    System.arraycopy(original, 0, updated, prefix.length, original.length);
                    Path staged = Files.createTempFile(folder, ".currency-", ".tmp");
                    try {
                        Files.write(staged, updated);
                        Files.move(staged, txt, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(staged); }
                    if (!currency.equals(MetricsParser.parse(txt.toFile()).getMetrics().get("Currency")))
                        throw new IllegalStateException("Repair did not round-trip for " + id);
                    String manifestLine = id + "\t" + currency + "\t" + txt + "\t" + hash(original) + "\t" + hash(updated) + "\t" + hash(htmlBefore);
                    Files.write(backup.resolve("manifest.tsv"), Collections.singletonList(manifestLine), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    written++;
                }
            }
        }
        System.out.println("COUNTS " + counts);
        System.out.println("WRITTEN " + written);
        if (written > 0) System.out.println("BACKUP " + backup.toAbsolutePath());
    }
}
