package jp.amaro.survival.youtube;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class YouTubePollerTest {
    @Test void skipsInitialBacklogAndProcessesEachNewCommentOnce() throws Exception {
        YouTubeComment old = new YouTubeComment("old", "viewer-a", "past");
        YouTubeComment fresh = new YouTubeComment("new", "viewer-b", "now");
        AtomicInteger calls = new AtomicInteger();
        YouTubeCommentSource source = token -> switch (calls.getAndIncrement()) {
            case 0 -> new YouTubePollResult(List.of(old), "p1", Duration.ofMillis(5));
            case 1 -> new YouTubePollResult(List.of(old, fresh), "p2", Duration.ofMillis(5));
            default -> new YouTubePollResult(List.of(fresh), "p3", Duration.ofSeconds(10));
        };
        List<YouTubeComment> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try (YouTubePoller poller = new YouTubePoller(source, comment -> { received.add(comment); latch.countDown(); }, Logger.getAnonymousLogger())) {
            poller.start();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            Thread.sleep(30);
        }
        assertEquals(List.of(fresh), received);
    }
}
