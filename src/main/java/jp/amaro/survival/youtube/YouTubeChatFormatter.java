package jp.amaro.survival.youtube;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class YouTubeChatFormatter {
    private YouTubeChatFormatter() {}
    public static Component minecraft(YouTubeComment comment) {
        return Component.text("[YouTube] ", NamedTextColor.RED)
                .append(Component.text(comment.author() + ": ", NamedTextColor.WHITE))
                .append(Component.text(comment.message(), NamedTextColor.GRAY));
    }
    public static String console(YouTubeComment comment) { return "[YT] " + comment.author() + ": " + comment.message(); }
}
