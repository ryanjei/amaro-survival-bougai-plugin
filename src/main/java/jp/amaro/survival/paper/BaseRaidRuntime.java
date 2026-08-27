package jp.amaro.survival.paper;

import jp.amaro.survival.config.PluginSettings;
import jp.amaro.survival.domain.RaidTimeline;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.random.RandomGenerator;

public final class BaseRaidRuntime {
    public record Status(boolean active, long remainingSeconds, long currentWave) {}
    private static final List<EntityType> MOB_TYPES = List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.SPIDER, EntityType.CAVE_SPIDER,
            EntityType.CREEPER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.WITCH);
    private final JavaPlugin plugin; private final PluginSettings settings; private final OwnedMobService ownership; private final RandomGenerator random; private final World world;
    private BossBar bar; private BukkitTask task; private BukkitTask ambientTask; private RaidTimeline timeline; private long lastWave = -1; private Location center;

    public BaseRaidRuntime(JavaPlugin plugin, PluginSettings settings, OwnedMobService ownership, RandomGenerator random, World world) {
        this.plugin = plugin; this.settings = settings; this.ownership = ownership; this.random = random; this.world = world;
    }

    public boolean start() {
        if (task != null) return false;
        if (world == null) { plugin.getLogger().warning("拠点襲撃を開始できる通常ワールドがありません。"); return false; }
        center = world.getSpawnLocation().toBlockLocation().add(.5, 0, .5);
        timeline = new RaidTimeline(Instant.now(), settings.raidDuration(), settings.waveInterval());
        bar = BossBar.bossBar(Component.text("拠点襲撃 残り --:--"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
        plugin.getLogger().info("大規模襲撃を開始しました。world=" + world.getName());
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(world), 0L, 20L);
        ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnAmbient, settings.ambientSpawnInterval().toSeconds() * 20L, settings.ambientSpawnInterval().toSeconds() * 20L);
        return true;
    }

    private void tick(World world) {
        Instant now = Instant.now();
        if (timeline.expired(now)) { stop(true); return; }
        long remaining = timeline.remainingSeconds(now);
        bar.name(Component.text("拠点襲撃 残り %02d:%02d".formatted(remaining / 60, remaining % 60)));
        bar.progress(Math.max(0f, Math.min(1f, (float) remaining / settings.raidDuration().toSeconds())));
        long wave = timeline.elapsedWave(now);
        if (wave != lastWave) { lastWave = wave; spawnWave(world); }
    }

    private void spawnWave(World world) {
        int spawned = 0; Map<EntityType,Integer> composition = new EnumMap<>(EntityType.class);
        for (int i = 0; i < settings.mobsPerWave(); i++) {
            Optional<Location> location = SpawnLocations.around(center, Math.max(8, settings.raidRadius() / 2), settings.raidRadius(), random);
            if (location.isEmpty()) continue;
            Entity entity = world.spawnEntity(location.get(), MOB_TYPES.get(random.nextInt(MOB_TYPES.size())));
            ownership.mark(entity, "base_raid"); spawned++; composition.merge(entity.getType(),1,Integer::sum);
        }
        plugin.getLogger().info("拠点襲撃ウェーブ: " + spawned + "体を生成しました。");
        announceWave(lastWave + 1, composition);
    }

    private void spawnAmbient() {
        for (int i = 0; i < settings.ambientMobsPerSpawn(); i++) spawnOne("base_raid_ambient");
    }

    private void spawnOne(String source) {
        SpawnLocations.around(center, Math.max(8, settings.raidRadius() / 2), settings.raidRadius(), random)
                .ifPresent(location -> {
                    Entity entity = world.spawnEntity(location, MOB_TYPES.get(random.nextInt(MOB_TYPES.size())));
                    ownership.mark(entity, source);
                });
    }

    private void announceWave(long wave, Map<EntityType, Integer> composition) {
        Title title = Title.title(Component.text("⚠ 拠点襲撃 Wave " + wave + " ⚠"), Component.text(waveDetail(composition)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.8f, 1.0f);
        }
    }

    static String waveDetail(Map<EntityType, Integer> composition) {
        String detail = composition.entrySet().stream()
                .map(entry -> mobName(entry.getKey()) + " ×" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(" / "));
        return detail.isEmpty() ? "出現地点を確保できませんでした" : detail;
    }

    static String mobName(EntityType type) {
        return switch (type) {
            case ZOMBIE -> "ゾンビ";
            case HUSK -> "ハスク";
            case DROWNED -> "ドラウンド";
            case SKELETON -> "スケルトン";
            case STRAY -> "ストレイ";
            case SPIDER -> "クモ";
            case CAVE_SPIDER -> "洞窟グモ";
            case CREEPER -> "クリーパー";
            case PILLAGER -> "ピリジャー";
            case VINDICATOR -> "ヴィンディケーター";
            case WITCH -> "ウィッチ";
            default -> type.name();
        };
    }

    public void addViewer(Player player) { if (bar != null) player.showBossBar(bar); }
    public void removeViewer(Player player) { if (bar != null) player.hideBossBar(bar); }
    public boolean active() { return task != null; }
    public Status status() {
        if (task == null || timeline == null) return new Status(false, 0, 0);
        return new Status(true, timeline.remainingSeconds(Instant.now()), Math.max(0, lastWave + 1));
    }
    public void stop(boolean announce) {
        if (task == null && ambientTask == null) return;
        if (task != null) { task.cancel(); task = null; }
        if (ambientTask != null) { ambientTask.cancel(); ambientTask = null; }
        timeline = null; lastWave = -1; center = null;
        BossBar oldBar = bar; bar = null;
        Bukkit.getOnlinePlayers().forEach(player -> { player.hideBossBar(oldBar); if (announce) player.sendMessage(Component.text("[妨害] 拠点襲撃が終了しました。")); });
        plugin.getLogger().info("大規模襲撃を終了しました。");
    }
}
