package jp.amaro.survival.paper;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseRaidRuntimeTest {
    @Test
    void formatsActualWaveCompositionWithJapaneseMobNames() {
        Map<EntityType, Integer> composition = new LinkedHashMap<>();
        composition.put(EntityType.ZOMBIE, 4);
        composition.put(EntityType.SKELETON, 3);
        composition.put(EntityType.CREEPER, 1);

        assertEquals("ゾンビ ×4 / スケルトン ×3 / クリーパー ×1", BaseRaidRuntime.waveDetail(composition));
    }

    @Test
    void reportsWhenNoSafeSpawnWasFound() {
        assertEquals("出現地点を確保できませんでした", BaseRaidRuntime.waveDetail(Map.of()));
    }
}
