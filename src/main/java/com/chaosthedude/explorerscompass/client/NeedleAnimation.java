package com.chaosthedude.explorerscompass.client;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.MathHelper;

/** Pure animation state, independently testable without a Minecraft client. */
final class NeedleAnimation {
    private long lastTick = Long.MIN_VALUE;
    private boolean initialized;
    private boolean wasRandom;
    private double rotation;
    private double velocity;
    float update(long tick, double target, boolean random, boolean smooth) {
        if (tick != lastTick || !initialized || random != wasRandom || (!smooth && !random)) {
            lastTick = tick;
            wasRandom = random;
            if (random) target = ThreadLocalRandom.current().nextDouble();
            if (!initialized || (!smooth && !random)) {
                rotation = target;
                velocity = 0;
                initialized = true;
            } else {
                double delta = MathHelper.floorMod(target - rotation + 0.5, 1) - 0.5;
                velocity = (velocity + MathHelper.clamp(delta, -0.16, 0.16) * 0.1) * 0.8;
                rotation += velocity;
            }
        }
        return (float) MathHelper.floorMod(rotation, 1);
    }
}
