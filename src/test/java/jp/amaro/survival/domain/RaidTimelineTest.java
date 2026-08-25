package jp.amaro.survival.domain;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class RaidTimelineTest {
    @Test void tracksWavesRemainingTimeAndExpiry() {
        Instant start = Instant.parse("2026-08-26T00:00:00Z");
        RaidTimeline timeline = new RaidTimeline(start, Duration.ofSeconds(180), Duration.ofSeconds(30));
        assertEquals(0, timeline.elapsedWave(start)); assertEquals(180, timeline.remainingSeconds(start));
        assertEquals(2, timeline.elapsedWave(start.plusSeconds(61))); assertEquals(119, timeline.remainingSeconds(start.plusSeconds(61)));
        assertFalse(timeline.expired(start.plusSeconds(179))); assertTrue(timeline.expired(start.plusSeconds(180))); assertEquals(0, timeline.remainingSeconds(start.plusSeconds(300)));
    }
}
