package jp.amaro.survival.domain;

public final class CommentGauge {
    private final int requiredComments;
    private int comments;

    public CommentGauge(int requiredComments) {
        if (requiredComments <= 0) throw new IllegalArgumentException("requiredComments must be positive");
        this.requiredComments = requiredComments;
    }

    public synchronized boolean addComment() {
        comments++;
        if (comments >= requiredComments) {
            comments = 0;
            return true;
        }
        return false;
    }

    public synchronized int comments() { return comments; }
    public int requiredComments() { return requiredComments; }
    public synchronized double progress() { return (double) comments / requiredComments; }
}
