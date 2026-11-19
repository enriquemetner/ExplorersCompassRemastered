package io.github.enriquemetner.explorerscompass.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeedleAnimationTest {
    @Test void simultaneousTargetsDoNotShareState() {
        NeedleAnimation a = new NeedleAnimation(), b = new NeedleAnimation();
        assertEquals(0.1f, a.update(1, 0.1, false, true), 0.00001);
        assertEquals(0.6f, b.update(1, 0.6, false, true), 0.00001);
        assertEquals(0.1f, a.update(1, 0.9, false, true), 0.00001);
        b.update(2, 0.9, false, true);
        assertEquals(0.1f, a.update(2, 0.1, false, true), 0.00001);
    }
    @Test void stationaryDisplaysUpdateOrientationWithinTheSameTick() {
        NeedleAnimation frame = new NeedleAnimation();
        assertEquals(0.2f, frame.update(1, 0.2, false, false), 0.00001);
        assertEquals(0.7f, frame.update(1, 0.7, false, false), 0.00001);
    }
    @Test void randomNeedleDoesNotAdvanceMultipleTimesPerTickAndStaysNormalized() {
        NeedleAnimation animation = new NeedleAnimation();
        float first = animation.update(1, 0, true, true);
        assertEquals(first, animation.update(1, 0, true, true));
        for (int tick = 2; tick < 100; tick++) {
            float value = animation.update(tick, 0, true, true);
            assertTrue(value >= 0 && value < 1);
        }
    }
}
