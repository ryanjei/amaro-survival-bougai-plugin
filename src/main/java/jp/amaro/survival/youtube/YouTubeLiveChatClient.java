package jp.amaro.survival.youtube;
import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
public final class YouTubeLiveChatClient implements YouTubeCommentSource {
    private static final URI ENDPOINT = URI.create("https://www.googleapis.com/youtube/v3/liveChat/messages");
    private final HttpClient client; private final URI endpoint; private final YouTubeSecrets secrets;
    public YouTubeLiveChatClient(YouTubeSecrets secrets) { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), ENDPOINT, secrets); }
    YouTubeLiveChatClient(HttpClient client, URI endpoint, YouTubeSecrets secrets) { this.client = client; this.endpoint = endpoint; this.secrets = secrets; }
    @Override public YouTubePollResult poll(String pageToken) throws IOException, InterruptedException {
        String query = "liveChatId=" + encode(secrets.liveChatId()) + "&part=id,authorDetails,snippet&maxResults=200&key=" + encode(secrets.apiKey());
        if (pageToken != null && !pageToken.isBlank()) query += "&pageToken=" + encode(pageToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?" + query)).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) throw new IOException("YouTube API returned HTTP " + response.statusCode());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        List<YouTubeComment> comments = new ArrayList<>();
        JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject(), snippet = item.getAsJsonObject("snippet"), author = item.getAsJsonObject("authorDetails");
            if (snippet == null || author == null || !snippet.has("displayMessage") || !author.has("displayName")) continue;
            comments.add(new YouTubeComment(item.get("id").getAsString(), author.get("displayName").getAsString(), snippet.get("displayMessage").getAsString()));
        }
        String next = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : null;
        long millis = root.has("pollingIntervalMillis") ? root.get("pollingIntervalMillis").getAsLong() : 5000;
        return new YouTubePollResult(List.copyOf(comments), next, Duration.ofMillis(Math.max(1000, millis)));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
