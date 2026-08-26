package jp.amaro.survival.command;

import jp.amaro.survival.domain.InterferenceType;

public interface AdminOperations {
    StatusSnapshot status();
    void addGauge(int count);
    void applyInterference(InterferenceType type);
    void startRaid();
    void stopRaid();
    RaidSnapshot raidStatus();
    int cleanupOwnedMobs();
    void fakeYouTubeComment(String author, String message);

    record StatusSnapshot(boolean pluginEnabled, boolean youtubeEnabled, boolean interferenceEnabled,
                          int gaugeCurrent, int gaugeRequired, int gaugePercent, boolean raidActive,
                          int ownedMobs, int remainingCapacity, int onlinePlayers) {}
    record RaidSnapshot(boolean active, long remainingSeconds, long currentWave) {}
}
