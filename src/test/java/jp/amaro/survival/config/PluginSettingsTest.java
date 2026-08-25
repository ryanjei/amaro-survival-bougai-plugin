package jp.amaro.survival.config;
import jp.amaro.survival.domain.InterferenceCategory;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class PluginSettingsTest {
    @Test void readsSupportedValuesFromYamlConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("youtube.enabled", true); yaml.set("interference.enabled", false);
        yaml.set("interference.required-comments", 12);
        yaml.set("interference.category-weights.small", 6);
        yaml.set("interference.category-weights.medium", 3);
        yaml.set("interference.category-weights.large", 1);
        yaml.set("base-raid.duration-seconds", 120);
        yaml.set("base-raid.radius", 32);
        yaml.set("base-raid.wave-interval-seconds", 20);
        yaml.set("base-raid.mobs-per-wave", 7);
        PluginSettings settings = PluginSettings.from(yaml);
        assertTrue(settings.youtubeEnabled()); assertFalse(settings.interferenceEnabled());
        assertEquals(12, settings.requiredComments()); assertEquals(32, settings.raidRadius());
        assertEquals(7, settings.mobsPerWave()); assertEquals(6, settings.categoryWeights().get(InterferenceCategory.SMALL));
    }
    @Test void acceptsValidConfiguration() {
        PluginSettings settings = new PluginSettings(true, true, 10, Map.of(InterferenceCategory.SMALL, 55,
                InterferenceCategory.MEDIUM, 35, InterferenceCategory.LARGE, 10), Duration.ofSeconds(180), 40, Duration.ofSeconds(30), 8);
        assertEquals(10, settings.requiredComments()); assertEquals(40, settings.raidRadius());
    }
    @Test void rejectsUnsafeConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings(false, true, 0, Map.of(InterferenceCategory.SMALL, 1), Duration.ofSeconds(180), 40, Duration.ofSeconds(30), 8));
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings(false, true, 10, Map.of(InterferenceCategory.SMALL, 0), Duration.ofSeconds(180), 40, Duration.ofSeconds(30), 8));
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings(false, true, 10, Map.of(InterferenceCategory.SMALL, 1), Duration.ofSeconds(180), 40, Duration.ofSeconds(30), 41));
    }
}
