package jp.amaro.survival.domain;

import java.time.Duration;
import java.time.Instant;

public final class RaidTimeline {
    private final Instant startedAt;
    private final Duration duration;
    private final Duration waveInterval;

    public RaidTimeline(Instant startedAt, Duration duration, Duration waveInterval) {
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("duration must be positive");
        if (waveInterval.isZero() || waveInterval.isNegative()) throw new IllegalArgumentException("wave interval must be positive");
        this.startedAt = startedAt;
        this.duration = duration;
        this.waveInterval = waveInterval;
    }

    public boolean expired(Instant now) { return !now.isBefore(startedAt.plus(duration)); }
    public long remainingSeconds(Instant now) {
        return Math.max(0, Duration.between(now, startedAt.plus(duration)).toSeconds());
    }
    public long elapsedWave(Instant now) {
        long elapsed = Math.max(0, Duration.between(startedAt, now).toSeconds());
        return elapsed / waveInterval.toSeconds();
    }
}
