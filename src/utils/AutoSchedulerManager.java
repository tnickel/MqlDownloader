package utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
        stop(); // Cancel any existing schedule
        scheduler = Executors.newSingleThreadScheduledExecutor();

        long initialDelay = getMillisUntilNextFriday18();
        long period = 7L * 24 * 60 * 60 * 1000; // 7 days in ms

        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.info("Automatikmodus getriggert: Geplante Ausf\u00fchrung startet...");
                task.run();
            } catch (Exception e) {
                logger.error("Fehler im Automatikmodus-Scheduler", e);
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);

        logger.info("Automatikmodus gestartet. N\u00e4chste Ausf\u00fchrung in " + (initialDelay / 1000 / 60) + " Minuten (" + getNextRunDateFormatted() + ")");
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
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 18);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        int daysUntilFriday = (Calendar.FRIDAY - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        target.add(Calendar.DAY_OF_YEAR, daysUntilFriday);

        if (target.before(now) || target.equals(now)) {
            target.add(Calendar.DAY_OF_YEAR, 7);
        }

        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    public static String getNextRunDateFormatted() {
        long delayMs = getMillisUntilNextFriday18();
        Date nextDate = new Date(System.currentTimeMillis() + delayMs);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd.MM.yyyy 'um' HH:mm 'Uhr'");
        return sdf.format(nextDate);
    }
}
