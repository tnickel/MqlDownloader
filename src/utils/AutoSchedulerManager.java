package utils;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoSchedulerManager {
    private static final Logger logger = LogManager.getLogger(AutoSchedulerManager.class);
    private ScheduledExecutorService scheduler;
    private final Runnable task;

    public AutoSchedulerManager(Runnable task) {
        this.task = task;
    }

    public synchronized void start() {
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mql-automatikmodus");
            thread.setDaemon(true);
            return thread;
        });
        scheduleNextRun();
    }

    private synchronized void scheduleNextRun() {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }

        long delay = getMillisUntilNextFriday18();
        logger.info("Automatikmodus gestartet. N\u00e4chste Ausf\u00fchrung in "
                + (delay / 1000 / 60) + " Minuten (" + getNextRunDateFormatted() + ")");

        scheduler.schedule(() -> {
            try {
                logger.info("Automatikmodus getriggert: Geplante Ausf\u00fchrung startet...");
                task.run();
            } catch (Exception e) {
                logger.error("Fehler im Automatikmodus-Scheduler", e);
            } finally {
                scheduleNextRun();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
            logger.info("Automatikmodus gestoppt.");
        }
    }

    public synchronized boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    public static long getMillisUntilNextFriday18() {
        ZonedDateTime now = ZonedDateTime.now();
        return Duration.between(now, getNextRun(now)).toMillis();
    }

    static ZonedDateTime getNextRun(ZonedDateTime now) {
        ZonedDateTime target = now
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                .withHour(18)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        if (!target.isAfter(now)) {
            target = target.plusWeeks(1);
        }
        return target;
    }

    public static String getNextRunDateFormatted() {
        ZonedDateTime target = getNextRun(ZonedDateTime.now());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                "EEE, dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMAN);
        return formatter.format(target);
    }
}
