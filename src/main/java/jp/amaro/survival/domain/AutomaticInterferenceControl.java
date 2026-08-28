package jp.amaro.survival.domain;

public final class AutomaticInterferenceControl {
    public enum ChangeResult { CHANGED, ALREADY }
    public enum CommentResult { IGNORED, ACCUMULATED, TRIGGERED }

    private final CommentGauge gauge;
    private boolean enabled;

    public AutomaticInterferenceControl(CommentGauge gauge, boolean enabled) {
        this.gauge = gauge;
        this.enabled = enabled;
    }

    public ChangeResult enable() {
        if (enabled) return ChangeResult.ALREADY;
        enabled = true;
        return ChangeResult.CHANGED;
    }

    public ChangeResult disable() {
        if (!enabled) return ChangeResult.ALREADY;
        enabled = false;
        return ChangeResult.CHANGED;
    }

    public boolean enabled() { return enabled; }
    public int comments() { return gauge.comments(); }
    public int requiredComments() { return gauge.requiredComments(); }
    public double progress() { return gauge.progress(); }

    public CommentResult acceptAutomaticComment() {
        if (!enabled) return CommentResult.IGNORED;
        return addComment();
    }

    public CommentResult addManualGaugeComment() {
        return addComment();
    }

    private CommentResult addComment() {
        return gauge.addComment() ? CommentResult.TRIGGERED : CommentResult.ACCUMULATED;
    }
}
