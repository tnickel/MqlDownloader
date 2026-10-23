package logging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LoggerManagerTest {
    @Test void applicationLoggerInSeparateJvmReceivesInfoAfterBadInitialLocation(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("forked.log"), config = dir.resolve("log4j2.xml");
        Files.writeString(config, "<Configuration><Appenders><File name=\"F\" fileName=\""
                + output.toString().replace('\\', '/')
                + "\" immediateFlush=\"true\"><PatternLayout pattern=\"%m%n\"/></File></Appenders>"
                + "<Loggers><Root level=\"INFO\"><AppenderRef ref=\"F\"/></Root></Loggers></Configuration>");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path diagnostics = dir.resolve("forked-output.txt");
        Process child = new ProcessBuilder(java, "-Dlog4j.configurationFile=C:/synthetic-missing-log4j.xml",
                "-cp", classpath, PreMainContextProbe.class.getName(), config.toString())
                .redirectErrorStream(true).redirectOutput(diagnostics.toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Isolated logging probe timed out");
            assertEquals(0, child.exitValue(), () -> {
                try { return Files.readString(diagnostics); } catch (Exception e) { return e.toString(); }
            });
            assertTrue(Files.readString(output).contains("Synthetic forked download evidence"));
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }

    public static class PreMainContextProbe {
        private static final org.apache.logging.log4j.Logger PRE_MAIN = LogManager.getLogger(PreMainContextProbe.class);
        public static void main(String[] args) {
            // Force the default context to exist as well. It must not steal
            // configuration from the classloader context of PRE_MAIN.
            LogManager.getContext(false);
            LoggerManager.initializeLogger(args[0]);
            if (!PRE_MAIN.isInfoEnabled()) throw new IllegalStateException("Application logger remains ERROR-only");
            PRE_MAIN.info("Synthetic forked download evidence");
        }
    }

    @Test void configuredInfoLoggerReplacesContextCreatedBeforeMain(@TempDir Path dir) throws Exception {
        org.apache.logging.log4j.Logger preMainLogger = LogManager.getLogger("synthetic-pre-main");
        LoggerContext context = ((org.apache.logging.log4j.core.Logger) preMainLogger).getContext();
        org.apache.logging.log4j.core.config.Configuration previous = context.getConfiguration();
        java.lang.reflect.Field initialized = LoggerManager.class.getDeclaredField("isInitialized");
        initialized.setAccessible(true);
        boolean previousFlag = initialized.getBoolean(null);
        Path output = dir.resolve("configured.log"), config = dir.resolve("log4j2.xml");
        Files.writeString(config, "<Configuration><Appenders><File name=\"F\" fileName=\""
                + output.toString().replace('\\', '/')
                + "\" immediateFlush=\"true\"><PatternLayout pattern=\"%m%n\"/></File></Appenders>"
                + "<Loggers><Root level=\"INFO\"><AppenderRef ref=\"F\"/></Root></Loggers></Configuration>");
        try {
            initialized.setBoolean(null, false);
            LoggerManager.initializeLogger(config.toString());
            assertTrue(preMainLogger.isInfoEnabled(), "Already-created application logger must be reconfigured");
            preMainLogger.info("Synthetic inner download evidence");
            assertTrue(Files.readString(output).contains("Synthetic inner download evidence"));
        } finally {
            context.setConfiguration(previous);
            initialized.setBoolean(null, previousFlag);
        }
    }
}
