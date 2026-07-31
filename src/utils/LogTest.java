package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Einfache Klasse zum Testen der Logging-Funktionalit\u00e4t.
 */
public class LogTest {
    private static final Logger logger = LogManager.getLogger(LogTest.class);
    
    /**
     * Konstruktor, der einige Testlogs erzeugt.
     */
    public LogTest() {
        logger.debug("Debug-Log-Test");
        logger.info("Info-Log-Test");
        logger.warn("Warn-Log-Test");
        logger.error("Error-Log-Test (ohne Exception)");
        
        try {
            // Erzeuge einen absichtlichen Fehler f\u00fcr einen Test
            int result = 1 / 0;
        } catch (Exception e) {
            logger.error("Error-Log-Test (mit Exception)", e);
        }
    }
    
    /**
     * F\u00fchrt einige weitere Tests mit spezifischen Log-Levels durch.
     */
    public void runAdditionalTests() {
        logger.trace("Trace-Log-Test (sollte normalerweise nicht sichtbar sein)");
        logger.debug("Ein weiterer Debug-Log-Test");
        logger.info("Ein weiterer Info-Log-Test");
        logger.warn("Ein weiterer Warn-Log-Test");
        logger.error("Ein weiterer Error-Log-Test");
    }
}