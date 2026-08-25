package jp.amaro.survival.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WeightedInterferenceSelectorTest {
    @Test void selectsOnlyWeightedCategoryAndItsMembers() {
        WeightedInterferenceSelector selector = new WeightedInterferenceSelector(Map.of(
                InterferenceCategory.SMALL, 0, InterferenceCategory.MEDIUM, 10, InterferenceCategory.LARGE, 0), new Random(1));
        for (int i = 0; i < 100; i++) assertEquals(InterferenceCategory.MEDIUM, selector.select().category());
    }
    @Test void supportsEachConfiguredCategory() {
        for (InterferenceCategory category : InterferenceCategory.values()) {
            WeightedInterferenceSelector selector = new WeightedInterferenceSelector(Map.of(category, 1), new Random(2));
            assertEquals(category, selector.selectCategory());
        }
    }
    @Test void rejectsInvalidWeights() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedInterferenceSelector(Map.of(), new Random()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedInterferenceSelector(Map.of(InterferenceCategory.SMALL, -1), new Random()));
    }
}
