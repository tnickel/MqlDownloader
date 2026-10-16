package calculators;

import converter.HtmlConverter;
import utils.HtmlDatabase;
import utils.HtmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MPDDDataQualityTest {
    @Test void missingInputsRemainUnknown() {
        MPDDCalculator c=new MPDDCalculator(null);
        assertTrue(Double.isNaN(c.calculate3MPDD("missing")));
        assertTrue(Double.isNaN(c.calculateFinalMPDD(10,0)));
        assertTrue(Double.isNaN(c.calculateFinalMPDD(10,Double.NaN)));
        assertEquals(0,c.calculateFinalMPDD(0,5));
        assertEquals(2,c.calculateFinalMPDD(10,5));
    }
    @Test void malformedMonthDoesNotLeaveUsablePartialMap(@TempDir Path temp) {
        HtmlParser parser=new HtmlParser(temp.toString()) {
            @Override public List<String> getAllMonthsDetails(String file) {
                return Arrays.asList("2026/08:5","2026/07:bad");
            }
        };
        assertTrue(new HtmlDatabase(parser).getMonthlyProfitPercentages("synthetic").isEmpty());
    }
    @Test void unknownMpddPreservesHtmlCsvAndPreviousMetrics(@TempDir Path temp) throws Exception {
        Path dir=Files.createDirectories(temp.resolve("mql4"));
        Path html=dir.resolve("Synthetic_101_root.html"),csv=dir.resolve("Synthetic_101.csv"),txt=dir.resolve("Synthetic_101_root.txt");
        Files.write(html,"<html>No financial measurements</html>".getBytes(StandardCharsets.UTF_8));
        Files.write(csv,"original history".getBytes(StandardCharsets.UTF_8));
        Files.write(txt,"previous metrics".getBytes(StandardCharsets.UTF_8));
        HtmlConverter converter=new HtmlConverter(temp.toString());
        converter.convertAllHtmlFiles();
        assertEquals(1,converter.getFailedProvidersCount());
        assertTrue(Files.exists(html));
        assertEquals("original history",new String(Files.readAllBytes(csv),StandardCharsets.UTF_8));
        assertEquals("previous metrics",new String(Files.readAllBytes(txt),StandardCharsets.UTF_8));
    }
}
