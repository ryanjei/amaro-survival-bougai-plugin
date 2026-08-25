package jp.amaro.survival.youtube;

public interface YouTubeCommentSource {
    YouTubePollResult poll(String pageToken) throws Exception;
}
