package jp.amaro.survival.paper;

import jp.amaro.survival.config.PluginSettings;
import jp.amaro.survival.domain.RaidTimeline;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
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
    private final JavaPlugin plugin; private final PluginSettings settings; private final OwnedMobService ownership; private final RandomGenerator random;
    private BossBar bar; private BukkitTask task; private RaidTimeline timeline; private long lastWave = -1;

    public BaseRaidRuntime(JavaPlugin plugin, PluginSettings settings, OwnedMobService ownership, RandomGenerator random) {
        this.plugin = plugin; this.settings = settings; this.ownership = ownership; this.random = random;
    }

    public boolean start() {
        if (task != null) return false;
        World world = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL).findFirst().orElse(null);
        if (world == null) { plugin.getLogger().warning("拠点襲撃を開始できる通常ワールドがありません。"); return false; }
        timeline = new RaidTimeline(Instant.now(), settings.raidDuration(), settings.waveInterval());
        bar = BossBar.bossBar(Component.text("拠点襲撃 残り --:--"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
        plugin.getLogger().info("大規模襲撃を開始しました。world=" + world.getName());
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(world), 0L, 20L);
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
        Location center = world.getSpawnLocation(); int spawned = 0;
        for (int i = 0; i < settings.mobsPerWave(); i++) {
            Optional<Location> location = SpawnLocations.around(center, Math.max(8, settings.raidRadius() / 2), settings.raidRadius(), random);
            if (location.isEmpty()) continue;
            Entity entity = world.spawnEntity(location.get(), MOB_TYPES.get(random.nextInt(MOB_TYPES.size())));
            ownership.mark(entity, "base_raid"); spawned++;
        }
        plugin.getLogger().info("拠点襲撃ウェーブ: " + spawned + "体を生成しました。");
    }

    public void addViewer(Player player) { if (bar != null) player.showBossBar(bar); }
    public void removeViewer(Player player) { if (bar != null) player.hideBossBar(bar); }
    public boolean active() { return task != null; }
    public Status status() {
        if (task == null || timeline == null) return new Status(false, 0, 0);
        return new Status(true, timeline.remainingSeconds(Instant.now()), Math.max(0, lastWave));
    }
    public void stop(boolean announce) {
        if (task == null) return;
        task.cancel(); task = null; timeline = null; lastWave = -1;
        BossBar oldBar = bar; bar = null;
        Bukkit.getOnlinePlayers().forEach(player -> { player.hideBossBar(oldBar); if (announce) player.sendMessage(Component.text("[妨害] 拠点襲撃が終了しました。")); });
        plugin.getLogger().info("大規模襲撃を終了しました。");
    }
}
