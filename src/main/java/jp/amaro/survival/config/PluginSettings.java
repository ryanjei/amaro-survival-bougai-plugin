package jp.amaro.survival.config;

import jp.amaro.survival.domain.InterferenceCategory;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public record PluginSettings(boolean youtubeEnabled, boolean interferenceEnabled, int requiredComments,
                             Map<InterferenceCategory, Integer> categoryWeights, Duration raidDuration,
                             int raidRadius, Duration waveInterval, int mobsPerWave,
                             Duration ambientSpawnInterval, int ambientMobsPerSpawn) {
    public PluginSettings {
        categoryWeights = Map.copyOf(categoryWeights);
        if (requiredComments <= 0) throw new IllegalArgumentException("interference.required-comments must be positive");
        if (raidDuration.isZero() || raidDuration.isNegative()) throw new IllegalArgumentException("base-raid.duration-seconds must be positive");
        if (raidRadius < 8) throw new IllegalArgumentException("base-raid.radius must be at least 8");
        if (waveInterval.isZero() || waveInterval.isNegative()) throw new IllegalArgumentException("base-raid.wave-interval-seconds must be positive");
        if (mobsPerWave <= 0 || mobsPerWave > 40) throw new IllegalArgumentException("base-raid.mobs-per-wave must be 1..40");
        if (ambientSpawnInterval.isZero() || ambientSpawnInterval.isNegative()) throw new IllegalArgumentException("base-raid.ambient-spawn-interval-seconds must be positive");
        if (ambientMobsPerSpawn <= 0 || ambientMobsPerSpawn > 20) throw new IllegalArgumentException("base-raid.ambient-mobs-per-spawn must be 1..20");
        int total = categoryWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (categoryWeights.values().stream().anyMatch(v -> v < 0) || total <= 0) throw new IllegalArgumentException("category weights are invalid");
    }

    public static PluginSettings from(FileConfiguration config) {
        EnumMap<InterferenceCategory, Integer> weights = new EnumMap<>(InterferenceCategory.class);
        weights.put(InterferenceCategory.SMALL, config.getInt("interference.category-weights.small", 55));
        weights.put(InterferenceCategory.MEDIUM, config.getInt("interference.category-weights.medium", 35));
        weights.put(InterferenceCategory.LARGE, config.getInt("interference.category-weights.large", 10));
        return new PluginSettings(config.getBoolean("youtube.enabled"), config.getBoolean("interference.enabled", true),
                config.getInt("interference.required-comments", 10), weights,
                Duration.ofSeconds(config.getLong("base-raid.duration-seconds", 180)),
                config.getInt("base-raid.radius", 40),
                Duration.ofSeconds(config.getLong("base-raid.wave-interval-seconds", 30)),
                config.getInt("base-raid.mobs-per-wave", 8),
                Duration.ofSeconds(config.getLong("base-raid.ambient-spawn-interval-seconds", 8)),
                config.getInt("base-raid.ambient-mobs-per-spawn", 2));
    }
}
