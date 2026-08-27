package jp.amaro.survival.command;

import jp.amaro.survival.domain.InterferenceType;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class AdminTestCommand implements CommandExecutor, TabCompleter {
    public static final String PERMISSION = "amaro.survival.admin";
    private static final int MAX_GAUGE_ADD = 100;
    private final AdminOperations operations;
    private final java.util.function.Predicate<CommandSender> administrator;

    public AdminTestCommand(AdminOperations operations) { this(operations, sender -> sender.hasPermission(PERMISSION)); }
    public AdminTestCommand(AdminOperations operations, java.util.function.Predicate<CommandSender> administrator) { this.operations = operations; this.administrator = administrator; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!administrator.test(sender)) { reply(sender, "このCommandを実行する権限がありません。"); return true; }
        if (args.length < 2 || !args[0].equalsIgnoreCase("test")) { usage(sender); return true; }
        try {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "status" -> status(sender, args);
                case "gauge" -> gauge(sender, args);
                case "interference" -> interference(sender, args);
                case "raid" -> raid(sender, args);
                case "mobs" -> mobs(sender, args);
                case "youtube" -> youtube(sender, args);
                default -> { usage(sender); yield true; }
            };
        } catch (RuntimeException exception) {
            reply(sender, "操作に失敗しました: " + safeMessage(exception));
            return true;
        }
    }

    private boolean status(CommandSender sender, String[] args) {
        if (args.length != 2) { usage(sender); return true; }
        AdminOperations.StatusSnapshot s = operations.status();
        reply(sender, "プラグイン: " + state(s.pluginEnabled()));
        reply(sender, "YouTube連携: " + state(s.youtubeEnabled()));
        reply(sender, "妨害機能: " + state(s.interferenceEnabled()));
        reply(sender, "妨害ゲージ: %d / %d (%d%%)".formatted(s.gaugeCurrent(), s.gaugeRequired(), s.gaugePercent()));
        reply(sender, "拠点襲撃: " + (s.raidActive() ? "進行中" : "停止中"));
        reply(sender, "ASBP所有Mob: %d体".formatted(s.ownedMobs()));
        reply(sender, "オンライン人数: " + s.onlinePlayers() + "人");
        return true;
    }

    private boolean gauge(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4 || !args[2].equalsIgnoreCase("add")) { usage(sender); return true; }
        int count = args.length == 4 ? parseCount(args[3]) : 1;
        operations.addGauge(count); reply(sender, "妨害ゲージへ" + count + "件追加しました。"); return true;
    }

    private boolean interference(CommandSender sender, String[] args) {
        if (args.length != 3) { interferenceTypes(sender); return true; }
        try { InterferenceType type = InterferenceType.valueOf(args[2].toUpperCase(Locale.ROOT)); operations.applyInterference(type); reply(sender, "妨害「" + type.displayName() + "」を発動しました。"); }
        catch (IllegalArgumentException exception) { interferenceTypes(sender); }
        return true;
    }

    private boolean raid(CommandSender sender, String[] args) {
        if (args.length != 3) { usage(sender); return true; }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "start" -> { operations.startRaid(); reply(sender, "拠点襲撃の開始を要求しました。"); }
            case "stop" -> { operations.stopRaid(); reply(sender, "拠点襲撃を停止しました。"); }
            case "status" -> { AdminOperations.RaidSnapshot s = operations.raidStatus(); reply(sender, s.active() ? "拠点襲撃: 進行中 / 残り%d秒 / 第%dウェーブ".formatted(s.remainingSeconds(), s.currentWave()) : "拠点襲撃: 停止中"); }
            default -> usage(sender);
        }
        return true;
    }

    private boolean mobs(CommandSender sender, String[] args) {
        if (args.length != 3) { usage(sender); return true; }
        if (args[2].equalsIgnoreCase("count")) { AdminOperations.StatusSnapshot s = operations.status(); reply(sender, "ASBP所有Mob: %d体".formatted(s.ownedMobs())); }
        else if (args[2].equalsIgnoreCase("cleanup")) reply(sender, operations.cleanupOwnedMobs() + "体のASBP所有Mobをcleanupしました。");
        else usage(sender);
        return true;
    }

    private boolean youtube(CommandSender sender, String[] args) {
        if (args.length < 5 || !args[2].equalsIgnoreCase("fake")) { usage(sender); return true; }
        String message = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        if (args[3].isBlank() || message.isBlank()) { usage(sender); return true; }
        operations.fakeYouTubeComment(args[3], message); reply(sender, "Fake YouTube Commentを投入しました。"); return true;
    }

    static int parseCount(String value) {
        final int count;
        try { count = Integer.parseInt(value); } catch (NumberFormatException exception) { throw new IllegalArgumentException("countは1～" + MAX_GAUGE_ADD + "の整数で指定してください。"); }
        if (count < 1 || count > MAX_GAUGE_ADD) throw new IllegalArgumentException("countは1～" + MAX_GAUGE_ADD + "で指定してください。");
        return count;
    }

    private static void interferenceTypes(CommandSender sender) { reply(sender, "type候補: " + String.join(", ", Arrays.stream(InterferenceType.values()).map(Enum::name).toList())); }
    private static void usage(CommandSender sender) { reply(sender, "使用方法: /asbp test <status|gauge add [count]|interference <type>|raid <start|stop|status>|mobs <count|cleanup>|youtube fake <author> <message...>>"); }
    private static String state(boolean value) { return value ? "有効" : "無効"; }
    private static String safeMessage(RuntimeException exception) { return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); }
    private static void reply(CommandSender sender, String message) { sender.sendMessage(Component.text("[ASBP] " + message)); }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!administrator.test(sender)) return List.of();
        if (args.length == 1) return matches(args[0], List.of("test"));
        if (args.length == 2) return matches(args[1], List.of("status", "gauge", "interference", "raid", "mobs", "youtube"));
        if (args.length == 3 && args[1].equalsIgnoreCase("interference")) return matches(args[2], Arrays.stream(InterferenceType.values()).map(Enum::name).toList());
        if (args.length == 3 && args[1].equalsIgnoreCase("raid")) return matches(args[2], List.of("start", "stop", "status"));
        if (args.length == 3 && args[1].equalsIgnoreCase("mobs")) return matches(args[2], List.of("count", "cleanup"));
        if (args.length == 3 && args[1].equalsIgnoreCase("gauge")) return matches(args[2], List.of("add"));
        if (args.length == 3 && args[1].equalsIgnoreCase("youtube")) return matches(args[2], List.of("fake"));
        return List.of();
    }
    private static List<String> matches(String prefix, List<String> values) { String lower = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).toList(); }
}
