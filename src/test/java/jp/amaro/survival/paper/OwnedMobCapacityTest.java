package jp.amaro.survival.paper;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnedMobCapacityTest {
    @Test void normalInterferenceCanSpawnFromZeroOwnedMobs() { assertEquals(4, OwnedMobService.allowedSpawnCount(0, 4)); }
    @Test void normalInterferenceIsTrimmedToRemainingTwoSlots() { assertEquals(2, OwnedMobService.allowedSpawnCount(78, 40)); }
    @Test void normalInterferenceSpawnsNothingAtLimit() { assertEquals(0, OwnedMobService.allowedSpawnCount(80, 4)); }
    @Test void raidAndNormalInterferenceShareTheSameLimit() {
        assertEquals(80, OwnedMobService.MAX_OWNED_MOBS);
        assertEquals(5, OwnedMobService.allowedSpawnCount(75, 8));
        assertEquals(5, OwnedMobService.allowedSpawnCount(75, 40));
    }
    @Test void onlyPluginMarkedMobsContributeToOwnedCount() {
        record Mob(boolean marked) {}
        List<Mob> entities = List.of(new Mob(false), new Mob(true), new Mob(false), new Mob(true));
        assertEquals(2, OwnedMobService.countMarked(entities, Mob::marked));
    }
}
