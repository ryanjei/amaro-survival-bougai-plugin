package jp.amaro.survival.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class WeightedInterferenceSelector {
    private final Map<InterferenceCategory, Integer> weights;
    private final RandomGenerator random;

    public WeightedInterferenceSelector(Map<InterferenceCategory, Integer> weights, RandomGenerator random) {
        this.weights = Map.copyOf(weights);
        this.random = random;
        int total = 0;
        for (InterferenceCategory category : InterferenceCategory.values()) {
            int weight = this.weights.getOrDefault(category, 0);
            if (weight < 0) throw new IllegalArgumentException("weights must not be negative");
            total += weight;
        }
        if (total <= 0) throw new IllegalArgumentException("at least one category weight is required");
    }

    public InterferenceCategory selectCategory() {
        int total = Arrays.stream(InterferenceCategory.values()).mapToInt(c -> weights.getOrDefault(c, 0)).sum();
        int value = random.nextInt(total);
        for (InterferenceCategory category : InterferenceCategory.values()) {
            value -= weights.getOrDefault(category, 0);
            if (value < 0) return category;
        }
        throw new IllegalStateException("category selection failed");
    }

    public InterferenceType select() {
        InterferenceCategory category = selectCategory();
        List<InterferenceType> candidates = Arrays.stream(InterferenceType.values())
                .filter(type -> type.category() == category).toList();
        return candidates.get(random.nextInt(candidates.size()));
    }
}
