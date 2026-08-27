package jp.amaro.survival.youtube;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class YouTubeChatFormatterTest{@Test void preservesAuthorAndMessage(){YouTubeComment c=new YouTubeComment("id","testuser","こんにちは");String rendered=YouTubeChatFormatter.minecraft(c).toString();assertTrue(rendered.contains("YouTube"));assertTrue(rendered.contains("testuser"));assertTrue(rendered.contains("こんにちは"));assertEquals("[YT] testuser: こんにちは",YouTubeChatFormatter.console(c));}}
