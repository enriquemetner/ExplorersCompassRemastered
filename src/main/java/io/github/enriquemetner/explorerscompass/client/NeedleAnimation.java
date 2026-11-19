package io.github.enriquemetner.explorerscompass.client;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.MathHelper;

/** Vanilla-style interpolation for render contexts with a custom pose or a fractional destination. */
final class NeedleAnimation {
    private final Interpolator aimed = new Interpolator();
    private final Interpolator aimless = new Interpolator();

    float aimed(long tick, double heading, double bearing, boolean smooth) {
        double rotation = smooth ? aimed.update(tick, heading) : heading;
        return (float) MathHelper.floorMod(rotation + bearing, 1);
    }

    float aimless(long tick) {
        if (tick != aimless.lastTick) aimless.update(tick, ThreadLocalRandom.current().nextDouble());
        return (float) aimless.rotation;
    }

    private static final class Interpolator {
        private long lastTick = Long.MIN_VALUE;
        private double rotation;
        private double velocity;

        double update(long tick, double target) {
            if (tick != lastTick) {
                lastTick = tick;
                double delta = MathHelper.floorMod(target - rotation + 0.5, 1) - 0.5;
                velocity = (velocity + delta * 0.1) * 0.8;
                rotation = MathHelper.floorMod(rotation + velocity, 1);
            }
            return rotation;
        }
    }
}
