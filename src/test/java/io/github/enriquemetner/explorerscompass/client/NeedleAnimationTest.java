package io.github.enriquemetner.explorerscompass.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeedleAnimationTest {
    @Test void simultaneousTargetsDoNotShareState() {
        NeedleAnimation a = new NeedleAnimation(), b = new NeedleAnimation();
        assertEquals(0.02f, a.aimed(1, 0.25, 0, true), 0.00001);
        assertEquals(0.98f, b.aimed(1, -0.25, 0, true), 0.00001);
        assertEquals(0.12f, a.aimed(1, 0.45, 0.1, true), 0.00001);
    }

    @Test void stationaryDisplaysUpdateOrientationWithinTheSameTick() {
        NeedleAnimation frame = new NeedleAnimation();
        assertEquals(0.2f, frame.aimed(1, 0.2, 0, false), 0.00001);
        assertEquals(0.7f, frame.aimed(1, 0.7, 0, false), 0.00001);
    }
    @Test void randomNeedleDoesNotAdvanceMultipleTimesPerTickAndStaysNormalized() {
        NeedleAnimation animation = new NeedleAnimation();
        float first = animation.aimless(1);
        assertEquals(first, animation.aimless(1));
        for (int tick = 2; tick < 100; tick++) {
            float value = animation.aimless(tick);
            assertTrue(value >= 0 && value < 1);
        }
    }
}
