package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class AutoSchedulerManagerTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void schedulesForTheSameFridayBeforeSixPm() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 28, 17, 59, 0, 0, BERLIN);

        assertEquals(ZonedDateTime.of(2026, 8, 28, 18, 0, 0, 0, BERLIN),
                AutoSchedulerManager.getNextRun(now));
    }

    @Test
    void schedulesForTheFollowingWeekAtOrAfterSixPm() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 28, 18, 0, 0, 0, BERLIN);

        assertEquals(ZonedDateTime.of(2026, 9, 4, 18, 0, 0, 0, BERLIN),
                AutoSchedulerManager.getNextRun(now));
    }

    @Test
    void preservesLocalSixPmAcrossDaylightSavingTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 27, 18, 0, 0, 0, BERLIN);
        ZonedDateTime nextRun = AutoSchedulerManager.getNextRun(now);

        assertEquals(ZonedDateTime.of(2026, 4, 3, 18, 0, 0, 0, BERLIN), nextRun);
        assertEquals(Duration.ofHours(167), Duration.between(now, nextRun));
    }
}
