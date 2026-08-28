package jp.amaro.survival.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticInterferenceControlTest {
    @Test void enabledCommentsAdvanceGaugeAndTriggerAtThreshold() {
        AutomaticInterferenceControl control = new AutomaticInterferenceControl(new CommentGauge(2), true);

        assertEquals(AutomaticInterferenceControl.CommentResult.ACCUMULATED, control.acceptAutomaticComment());
        assertEquals(1, control.comments());
        assertEquals(AutomaticInterferenceControl.CommentResult.TRIGGERED, control.acceptAutomaticComment());
        assertEquals(0, control.comments());
    }

    @Test void disabledCommentsAreNotAccumulatedAndExistingGaugeIsPreserved() {
        AutomaticInterferenceControl control = new AutomaticInterferenceControl(new CommentGauge(10), true);
        for (int i = 0; i < 7; i++) control.acceptAutomaticComment();

        assertEquals(AutomaticInterferenceControl.ChangeResult.CHANGED, control.disable());
        for (int i = 0; i < 100; i++) {
            assertEquals(AutomaticInterferenceControl.CommentResult.IGNORED, control.acceptAutomaticComment());
        }
        assertEquals(7, control.comments());

        assertEquals(AutomaticInterferenceControl.ChangeResult.CHANGED, control.enable());
        assertEquals(AutomaticInterferenceControl.CommentResult.ACCUMULATED, control.acceptAutomaticComment());
        assertEquals(8, control.comments());
    }

    @Test void transitionsAreIdempotentAndManualGaugeRemainsAvailableWhileDisabled() {
        AutomaticInterferenceControl control = new AutomaticInterferenceControl(new CommentGauge(10), false);

        assertEquals(AutomaticInterferenceControl.ChangeResult.ALREADY, control.disable());
        assertEquals(AutomaticInterferenceControl.CommentResult.ACCUMULATED, control.addManualGaugeComment());
        assertEquals(1, control.comments());
        assertEquals(AutomaticInterferenceControl.ChangeResult.CHANGED, control.enable());
        assertEquals(AutomaticInterferenceControl.ChangeResult.ALREADY, control.enable());
    }
}
