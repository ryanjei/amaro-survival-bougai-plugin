package jp.amaro.survival.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommentGaugeTest {
    @Test void triggersAtRequiredCountAndResets() {
        CommentGauge gauge = new CommentGauge(3);
        assertFalse(gauge.addComment()); assertFalse(gauge.addComment()); assertEquals(2.0 / 3.0, gauge.progress(), .001);
        assertTrue(gauge.addComment()); assertEquals(0, gauge.comments()); assertEquals(0, gauge.progress());
        assertFalse(gauge.addComment());
    }
    @Test void rejectsNonPositiveRequirement() { assertThrows(IllegalArgumentException.class, () -> new CommentGauge(0)); }
}
