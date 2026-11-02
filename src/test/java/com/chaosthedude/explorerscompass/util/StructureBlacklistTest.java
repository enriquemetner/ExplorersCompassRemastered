package com.chaosthedude.explorerscompass.util;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureBlacklistTest {
    @Test void onlyStarActsAsAWildcard() {
        List<String> original = ExplorersCompassConfig.structureBlacklist;
        try {
            Identifier city = new Identifier("minecraft:ancient_city");
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_cit?");
            assertFalse(StructureUtils.structureIdIsBlacklisted(city));
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_city?");
            assertFalse(StructureUtils.structureIdIsBlacklisted(city));
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_.*");
            assertFalse(StructureUtils.structureIdIsBlacklisted(city));
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_city*");
            assertTrue(StructureUtils.structureIdIsBlacklisted(city));
            ExplorersCompassConfig.structureBlacklist = List.of("*:ancient_*");
            assertTrue(StructureUtils.structureIdIsBlacklisted(city));
            assertFalse(StructureUtils.structureIdIsBlacklisted(new Identifier("minecraft:fortress")));
        } finally {
            ExplorersCompassConfig.structureBlacklist = original;
        }
    }
}
