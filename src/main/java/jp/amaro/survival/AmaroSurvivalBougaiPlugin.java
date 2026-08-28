package jp.amaro.survival;

import jp.amaro.survival.config.PluginSettings;
import jp.amaro.survival.command.*;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class AmaroSurvivalBougaiPlugin extends JavaPlugin implements AdminOperations {
    private BossBar gaugeBar; private BaseRaidRuntime raid; private OwnedMobService ownership; private YouTubeRuntime youtubeRuntime;
    private AutomaticInterferenceControl automaticInterference; private WeightedInterferenceSelector selector; private InterferenceRuntime interference;
    private PluginSettings settings;

    @Override public void onEnable() {
        saveDefaultConfig();
        try { settings = PluginSettings.from(getConfig()); }
        catch (IllegalArgumentException exception) { getLogger().severe("config.ymlが不正です: " + exception.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        youtubeRuntime = new YouTubeRuntime(this::createYouTubeConnection, getLogger());
        automaticInterference = new AutomaticInterferenceControl(new CommentGauge(settings.requiredComments()), settings.interferenceEnabled());
        selector = new WeightedInterferenceSelector(settings.categoryWeights(), ThreadLocalRandom.current());
        gaugeBar = BossBar.bossBar(Component.text("視聴者妨害 0%"), 0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        ownership = new OwnedMobService(this);
        raid = new BaseRaidRuntime(this, settings, ownership, ThreadLocalRandom.current());
        interference = new InterferenceRuntime(ownership, raid, ThreadLocalRandom.current(), getLogger());
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(gaugeBar));
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(gaugeBar, raid), this);
        AdminTestCommand adminCommand = new AdminTestCommand(this);
        if (getCommand("asbp") == null) throw new IllegalStateException("plugin.ymlにasbp Commandがありません。");
        getCommand("asbp").setExecutor(adminCommand);
        getCommand("asbp").setTabCompleter(adminCommand);
        if (settings.youtubeEnabled()) {
            if (youtubeRuntime.start() == YouTubeRuntime.StartResult.STARTED) getLogger().info("YouTube Live Chat連携を自動開始しました。");
        }
        else getLogger().info("YouTube連携はconfigで停止しています。Minecraftプラグインは通常稼働します。");
        getLogger().info("Amaro Survival Bougai Plugin v0.1 を有効化しました。");
    }

    private Optional<YouTubeRuntime.Connection> createYouTubeConnection() throws Exception {
        Path secretsPath = getDataFolder().toPath().resolve("secrets.properties");
        Optional<YouTubeSecrets> secrets = YouTubeSecrets.load(secretsPath);
        if (secrets.isEmpty()) {
            getLogger().warning("YouTube連携を開始できません。secrets.propertiesにAPI KeyとLive Chat IDを設定してください。");
            return Optional.empty();
        }
        AtomicBoolean active = new AtomicBoolean();
        YouTubePoller poller = new YouTubePoller(new YouTubeLiveChatClient(secrets.get()), comment -> {
            if (!isEnabled() || !active.get()) return;
            Bukkit.getScheduler().runTask(this, () -> {
                if (active.get()) processComment(comment);
            });
        }, getLogger());
        return Optional.of(new YouTubeRuntime.Connection() {
            @Override public void start() { active.set(true); poller.start(); }
            @Override public void close() { active.set(false); poller.close(); }
        });
    }

    private void processComment(YouTubeComment comment) {
        Bukkit.broadcast(Component.text("[YT] " + comment.author() + ": " + comment.message()));
        applyGaugeResult(automaticInterference.acceptAutomaticComment());
    }

    private void applyGaugeResult(AutomaticInterferenceControl.CommentResult result) {
        if (result == AutomaticInterferenceControl.CommentResult.IGNORED) return;
        updateGauge();
        if (result == AutomaticInterferenceControl.CommentResult.TRIGGERED) {
            InterferenceType selected = selector.select();
            if (selected == InterferenceType.BASE_RAID && raid.active()) {
                selected = selector.select(InterferenceCategory.MEDIUM);
                getLogger().info("拠点襲撃進行中のためMEDIUM妨害へ切り替えました: " + selected.name());
            }
            interference.apply(selected);
        }
    }

    private void updateGauge() {
        int percent = (int) Math.floor(automaticInterference.progress() * 100);
        gaugeBar.name(Component.text("視聴者妨害 " + percent + "%"));
        gaugeBar.progress((float) automaticInterference.progress());
    }

    @Override public StatusSnapshot status() {
        int owned = ownership.countOwned();
        return new StatusSnapshot(isEnabled(), youtubeRuntime.running(), settings.youtubeEnabled(),
                automaticInterference.enabled(), settings.interferenceEnabled(),
                automaticInterference.comments(), automaticInterference.requiredComments(),
                (int) Math.floor(automaticInterference.progress() * 100), raid.active(),
                owned, Math.max(0, OwnedMobService.MAX_OWNED_MOBS - owned), Bukkit.getOnlinePlayers().size());
    }

    @Override public YouTubeRuntimeResult startYouTube() {
        return switch (youtubeRuntime.start()) {
            case STARTED -> YouTubeRuntimeResult.STARTED;
            case ALREADY_RUNNING -> YouTubeRuntimeResult.ALREADY_RUNNING;
            case FAILED -> YouTubeRuntimeResult.FAILED;
        };
    }
    @Override public YouTubeRuntimeResult stopYouTube() {
        return switch (youtubeRuntime.stop()) {
            case STOPPED -> YouTubeRuntimeResult.STOPPED;
            case ALREADY_STOPPED -> YouTubeRuntimeResult.ALREADY_STOPPED;
        };
    }
    @Override public InterferenceRuntimeResult enableInterference() {
        return automaticInterference.enable() == AutomaticInterferenceControl.ChangeResult.CHANGED
                ? InterferenceRuntimeResult.ENABLED : InterferenceRuntimeResult.ALREADY_ENABLED;
    }
    @Override public InterferenceRuntimeResult disableInterference() {
        return automaticInterference.disable() == AutomaticInterferenceControl.ChangeResult.CHANGED
                ? InterferenceRuntimeResult.DISABLED : InterferenceRuntimeResult.ALREADY_DISABLED;
    }
    @Override public void addGauge(int count) {
        for (int i = 0; i < count; i++) applyGaugeResult(automaticInterference.addManualGaugeComment());
    }
    @Override public void applyInterference(InterferenceType type) { interference.apply(type); }
    @Override public void startRaid() { interference.apply(InterferenceType.BASE_RAID); }
    @Override public void stopRaid() { raid.stop(true); }
    @Override public RaidSnapshot raidStatus() { BaseRaidRuntime.Status s = raid.status(); return new RaidSnapshot(s.active(), s.remainingSeconds(), s.currentWave()); }
    @Override public int cleanupOwnedMobs() { int before = ownership.countOwned(); ownership.cleanupAll(); return before; }
    @Override public void fakeYouTubeComment(String author, String message) { processComment(new YouTubeComment("admin-fake", author, message)); }

    @Override public void onDisable() {
        if (youtubeRuntime != null) youtubeRuntime.close();
        if (raid != null) raid.stop(false);
        if (gaugeBar != null) Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(gaugeBar));
        if (ownership != null) ownership.cleanupAll();
        getLogger().info("Amaro Survival Bougai Pluginを無効化しました。");
    }
}
