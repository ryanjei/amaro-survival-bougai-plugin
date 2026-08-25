package jp.amaro.survival.domain;

public enum InterferenceType {
    DARKNESS(InterferenceCategory.SMALL, "全員暗闇"),
    LEVITATION(InterferenceCategory.SMALL, "全員浮遊"),
    HUNGER(InterferenceCategory.SMALL, "全員空腹"),
    GLOWING(InterferenceCategory.SMALL, "全員発光"),
    KNOCKBACK(InterferenceCategory.SMALL, "全員ノックバック"),
    ZOMBIE_SWARM(InterferenceCategory.MEDIUM, "ゾンビの群れ"),
    SKELETON_SWARM(InterferenceCategory.MEDIUM, "スケルトンの群れ"),
    CREEPER_ALERT(InterferenceCategory.MEDIUM, "クリーパー警報"),
    MIXED_MOB_SWARM(InterferenceCategory.MEDIUM, "混成モブの群れ"),
    ENHANCED_MOB_SWARM(InterferenceCategory.MEDIUM, "強化モブの群れ"),
    BASE_RAID(InterferenceCategory.LARGE, "拠点襲撃");

    private final InterferenceCategory category;
    private final String displayName;

    InterferenceType(InterferenceCategory category, String displayName) {
        this.category = category;
        this.displayName = displayName;
    }

    public InterferenceCategory category() { return category; }
    public String displayName() { return displayName; }
}
