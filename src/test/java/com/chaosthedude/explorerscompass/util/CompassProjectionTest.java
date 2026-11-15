package com.chaosthedude.explorerscompass.util;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompassProjectionTest {
    @Test void projectsOverworldCentersAndFloorsNetherCoordinates() {
        var projection = CompassProjection.between(new Identifier("minecraft:overworld"), new Identifier("minecraft:the_nether"), 15, -9);
        assertEquals(new CompassProjection(1.9375, -1.0625, "nether"), projection);
        assertEquals(1, projection.blockX());
        assertEquals(-2, projection.blockZ());
        assertEquals(5, projection.distanceFrom(projection.x() + 3, projection.z() + 4));
    }

    @Test void projectsNetherCentersIntoTheOverworld() {
        assertEquals(new CompassProjection(-4, 20, "overworld"),
                CompassProjection.between(new Identifier("minecraft:the_nether"), new Identifier("minecraft:overworld"), -1, 2));
    }

    @Test void skipsUnrelatedAndUnknownDimensions() {
        assertNull(CompassProjection.between(new Identifier("minecraft:overworld"), new Identifier("minecraft:the_end"), 0, 0));
        assertNull(CompassProjection.between(new Identifier("minecraft:the_nether"), new Identifier("minecraft:the_nether"), 0, 0));
        assertNull(CompassProjection.between(null, new Identifier("minecraft:the_nether"), 0, 0));
    }
}
