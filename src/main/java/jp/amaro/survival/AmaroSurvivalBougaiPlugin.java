package jp.amaro.survival;

import jp.amaro.survival.config.PluginSettings;
import jp.amaro.survival.domain.*;
import jp.amaro.survival.paper.*;
import jp.amaro.survival.youtube.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class AmaroSurvivalBougaiPlugin extends JavaPlugin {
    private BossBar gaugeBar; private BaseRaidRuntime raid; private OwnedMobService ownership; private YouTubePoller poller;
    private CommentGauge gauge; private WeightedInterferenceSelector selector; private InterferenceRuntime interference;

    @Override public void onEnable() {
        saveDefaultConfig();
        final PluginSettings settings;
        try { settings = PluginSettings.from(getConfig()); }
        catch (IllegalArgumentException exception) { getLogger().severe("config.ymlが不正です: " + exception.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        gauge = new CommentGauge(settings.requiredComments());
        selector = new WeightedInterferenceSelector(settings.categoryWeights(), ThreadLocalRandom.current());
        gaugeBar = BossBar.bossBar(Component.text("視聴者妨害 0%"), 0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        ownership = new OwnedMobService(this);
        raid = new BaseRaidRuntime(this, settings, ownership, ThreadLocalRandom.current());
        interference = new InterferenceRuntime(ownership, raid, ThreadLocalRandom.current(), getLogger());
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(gaugeBar));
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(gaugeBar, raid), this);
        if (settings.youtubeEnabled()) startYouTube(settings);
        else getLogger().info("YouTube連携はconfigで停止しています。Minecraftプラグインは通常稼働します。");
        getLogger().info("Amaro Survival Bougai Plugin v0.1 を有効化しました。");
    }

    private void startYouTube(PluginSettings settings) {
        try {
            Path secretsPath = getDataFolder().toPath().resolve("secrets.properties");
            Optional<YouTubeSecrets> secrets = YouTubeSecrets.load(secretsPath);
            if (secrets.isEmpty()) { getLogger().warning("YouTube連携を開始できません。secrets.propertiesにAPI KeyとLive Chat IDを設定してください。"); return; }
            poller = new YouTubePoller(new YouTubeLiveChatClient(secrets.get()), comment -> {
                if (!isEnabled()) return;
                Bukkit.getScheduler().runTask(this, () -> processComment(comment, settings));
            }, getLogger());
            poller.start(); getLogger().info("YouTube Live Chat連携を開始しました。");
        } catch (Exception exception) { getLogger().warning("YouTube連携の初期化に失敗しました。Minecraftプラグインは継続します: " + exception.getMessage()); }
    }

    private void processComment(YouTubeComment comment, PluginSettings settings) {
        Bukkit.broadcast(Component.text("[YT] " + comment.author() + ": " + comment.message()));
        if (!settings.interferenceEnabled()) return;
        boolean triggered = gauge.addComment(); updateGauge();
        if (triggered) {
            InterferenceType selected = selector.select();
            if (selected == InterferenceType.BASE_RAID && raid.active()) {
                selected = selector.select(InterferenceCategory.MEDIUM);
                getLogger().info("拠点襲撃進行中のためMEDIUM妨害へ切り替えました: " + selected.name());
            }
            interference.apply(selected);
        }
    }

    private void updateGauge() {
        int percent = (int) Math.floor(gauge.progress() * 100);
        gaugeBar.name(Component.text("視聴者妨害 " + percent + "%"));
        gaugeBar.progress((float) gauge.progress());
    }

    @Override public void onDisable() {
        if (poller != null) poller.close();
        if (raid != null) raid.stop(false);
        if (gaugeBar != null) Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(gaugeBar));
        if (ownership != null) ownership.cleanupAll();
        getLogger().info("Amaro Survival Bougai Pluginを無効化しました。");
    }
}
