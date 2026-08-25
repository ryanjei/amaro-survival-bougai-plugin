package jp.amaro.survival.youtube;
import java.time.Duration;
import java.util.List;
public record YouTubePollResult(List<YouTubeComment> comments, String nextPageToken, Duration nextPollDelay) {}
