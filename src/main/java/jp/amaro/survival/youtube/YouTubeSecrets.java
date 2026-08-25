package jp.amaro.survival.youtube;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public record YouTubeSecrets(String apiKey, String liveChatId) {
    public static Optional<YouTubeSecrets> load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        String apiKey = value(properties, "youtube.api-key", "AMARO_YOUTUBE_API_KEY");
        String liveChatId = value(properties, "youtube.live-chat-id", "AMARO_YOUTUBE_LIVE_CHAT_ID");
        return apiKey == null || liveChatId == null ? Optional.empty() : Optional.of(new YouTubeSecrets(apiKey, liveChatId));
    }
    private static String value(Properties properties, String key, String environment) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
