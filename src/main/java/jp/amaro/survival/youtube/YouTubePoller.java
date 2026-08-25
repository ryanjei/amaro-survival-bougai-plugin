package jp.amaro.survival.youtube;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
public final class YouTubePoller implements AutoCloseable {
    private static final int MAX_SEEN_IDS = 10_000;
    private final YouTubeLiveChatClient client; private final Consumer<YouTubeComment> sink; private final Logger logger;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "amaro-youtube-poller"); t.setDaemon(true); return t; });
    private final Set<String> seenIds = new LinkedHashSet<>(); private volatile boolean closed; private String pageToken; private Instant lastErrorLog = Instant.EPOCH;
    public YouTubePoller(YouTubeLiveChatClient client, Consumer<YouTubeComment> sink, Logger logger) { this.client = client; this.sink = sink; this.logger = logger; }
    public void start() { schedule(Duration.ZERO); }
    private void schedule(Duration delay) { if (!closed) executor.schedule(this::poll, delay.toMillis(), TimeUnit.MILLISECONDS); }
    private void poll() {
        if (closed) return;
        try { YouTubePollResult result = client.poll(pageToken); pageToken = result.nextPageToken(); for (YouTubeComment c : result.comments()) if (remember(c.id())) sink.accept(c); schedule(result.nextPollDelay()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { Instant now = Instant.now(); if (Duration.between(lastErrorLog, now).toMinutes() >= 5) { logger.warning("YouTube Live Chat取得に失敗しました。30秒後に再試行します: " + e.getMessage()); lastErrorLog = now; } schedule(Duration.ofSeconds(30)); }
    }
    private synchronized boolean remember(String id) { if (!seenIds.add(id)) return false; if (seenIds.size() > MAX_SEEN_IDS) seenIds.remove(seenIds.iterator().next()); return true; }
    @Override public void close() { closed = true; executor.shutdownNow(); }
}
