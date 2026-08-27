package jp.amaro.survival.paper;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnedMobCapacityTest {
    @Test void onlyPluginMarkedMobsContributeToOwnedCount() {
        record Mob(boolean marked) {}
        List<Mob> entities = List.of(new Mob(false), new Mob(true), new Mob(false), new Mob(true));
        assertEquals(2, OwnedMobService.countMarked(entities, Mob::marked));
    }
    @Test void ownershipTrackingHasNoFixedTotalLimit() {
        record Mob(boolean marked) {}
        List<Mob> entities = java.util.stream.IntStream.range(0, 120).mapToObj(i -> new Mob(true)).toList();
        assertEquals(120, OwnedMobService.countMarked(entities, Mob::marked));
    }
}
