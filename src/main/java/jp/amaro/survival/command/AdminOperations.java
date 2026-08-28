package jp.amaro.survival.command;

import jp.amaro.survival.domain.InterferenceType;

public interface AdminOperations {
    StatusSnapshot status();
    YouTubeRuntimeResult startYouTube();
    YouTubeRuntimeResult stopYouTube();
    InterferenceRuntimeResult enableInterference();
    InterferenceRuntimeResult disableInterference();
    void addGauge(int count);
    void applyInterference(InterferenceType type);
    void startRaid();
    void stopRaid();
    RaidSnapshot raidStatus();
    int cleanupOwnedMobs();
    void fakeYouTubeComment(String author, String message);

    record StatusSnapshot(boolean pluginEnabled, boolean youtubeRunning, boolean youtubeAutoStart,
                          boolean interferenceRunning, boolean interferenceAutoStart,
                          int gaugeCurrent, int gaugeRequired, int gaugePercent, boolean raidActive,
                          int ownedMobs, int remainingCapacity, int onlinePlayers) {}
    enum YouTubeRuntimeResult { STARTED, ALREADY_RUNNING, STOPPED, ALREADY_STOPPED, FAILED }
    enum InterferenceRuntimeResult { ENABLED, ALREADY_ENABLED, DISABLED, ALREADY_DISABLED }
    record RaidSnapshot(boolean active, long remainingSeconds, long currentWave) {}
}
