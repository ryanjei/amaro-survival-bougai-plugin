package jp.amaro.survival.youtube;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeRuntimeTest {
    @Test void supportsOffOnOffOnWithoutDuplicateConnections() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        YouTubeRuntime runtime = new YouTubeRuntime(() -> {
            created.incrementAndGet();
            return Optional.of(connection(started, closed));
        }, Logger.getAnonymousLogger());

        assertFalse(runtime.running());
        assertEquals(YouTubeRuntime.StartResult.STARTED, runtime.start());
        assertTrue(runtime.running());
        assertEquals(YouTubeRuntime.StartResult.ALREADY_RUNNING, runtime.start());
        assertEquals(1, created.get());
        assertEquals(1, started.get());

        assertEquals(YouTubeRuntime.StopResult.STOPPED, runtime.stop());
        assertFalse(runtime.running());
        assertEquals(1, closed.get());
        assertEquals(YouTubeRuntime.StopResult.ALREADY_STOPPED, runtime.stop());
        assertEquals(1, closed.get());

        assertEquals(YouTubeRuntime.StartResult.STARTED, runtime.start());
        assertTrue(runtime.running());
        assertEquals(2, created.get());
        assertEquals(2, started.get());
        runtime.close();
        assertFalse(runtime.running());
        assertEquals(2, closed.get());
    }

    @Test void staysStoppedWhenSecretsOrInitializationAreUnavailable() {
        YouTubeRuntime missing = new YouTubeRuntime(Optional::<YouTubeRuntime.Connection>empty, Logger.getAnonymousLogger());
        assertEquals(YouTubeRuntime.StartResult.FAILED, missing.start());
        assertFalse(missing.running());

        YouTubeRuntime failing = new YouTubeRuntime(() -> { throw new IllegalStateException("safe failure"); }, Logger.getAnonymousLogger());
        assertEquals(YouTubeRuntime.StartResult.FAILED, failing.start());
        assertFalse(failing.running());
    }

    @Test void closesPartiallyStartedConnectionAndAllowsRetry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        YouTubeRuntime runtime = new YouTubeRuntime(() -> Optional.of(new YouTubeRuntime.Connection() {
            @Override public void start() {
                if (attempts.getAndIncrement() == 0) throw new IllegalStateException("start failed");
            }
            @Override public void close() { closed.incrementAndGet(); }
        }), Logger.getAnonymousLogger());

        assertEquals(YouTubeRuntime.StartResult.FAILED, runtime.start());
        assertFalse(runtime.running());
        assertEquals(1, closed.get());
        assertEquals(YouTubeRuntime.StartResult.STARTED, runtime.start());
        assertTrue(runtime.running());
    }

    private static YouTubeRuntime.Connection connection(AtomicInteger started, AtomicInteger closed) {
        return new YouTubeRuntime.Connection() {
            @Override public void start() { started.incrementAndGet(); }
            @Override public void close() { closed.incrementAndGet(); }
        };
    }
}
