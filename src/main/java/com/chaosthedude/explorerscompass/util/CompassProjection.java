package com.chaosthedude.explorerscompass.util;

import net.minecraft.util.Identifier;

public record CompassProjection(double x, double z, String dimensionLabel) {
    private static final Identifier OVERWORLD = new Identifier("minecraft", "overworld");
    private static final Identifier NETHER = new Identifier("minecraft", "the_nether");

    public static CompassProjection between(Identifier target, Identifier current, int x, int z) {
        if (OVERWORLD.equals(target) && NETHER.equals(current)) {
            return new CompassProjection((x + 0.5) / 8, (z + 0.5) / 8, "nether");
        }
        if (NETHER.equals(target) && OVERWORLD.equals(current)) {
            return new CompassProjection((x + 0.5) * 8, (z + 0.5) * 8, "overworld");
        }
        return null;
    }

    public int blockX() { return (int) Math.floor(x); }

    public int blockZ() { return (int) Math.floor(z); }

    public int distanceFrom(double fromX, double fromZ) {
        return (int) Math.hypot(x - fromX, z - fromZ);
    }
}
