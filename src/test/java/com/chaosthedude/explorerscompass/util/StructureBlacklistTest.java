package com.chaosthedude.explorerscompass.util;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureBlacklistTest {
    @Test void nonemptyWhitelistOverridesBlacklistEvenWhenNothingMatches() {
        List<String> originalWhitelist = ExplorersCompassConfig.structureWhitelist;
        List<String> originalBlacklist = ExplorersCompassConfig.structureBlacklist;
        try {
            Identifier city = new Identifier("minecraft:ancient_city");
            Identifier village = new Identifier("minecraft:village_plains");
            Identifier moddedCity = new Identifier("example:ancient_city");
            ExplorersCompassConfig.structureBlacklist = List.of("ancient_city");
            ExplorersCompassConfig.structureWhitelist = List.of("ancient_*");
            assertFalse(StructureUtils.structureIdIsExcluded(city));
            assertTrue(StructureUtils.structureIdIsExcluded(village));
            assertTrue(StructureUtils.structureIdIsExcluded(moddedCity));

            ExplorersCompassConfig.structureWhitelist = List.of("example:missing");
            assertTrue(StructureUtils.structureIdIsExcluded(village));

            ExplorersCompassConfig.structureWhitelist = List.of();
            assertFalse(StructureUtils.structureIdIsExcluded(village));
            assertTrue(StructureUtils.structureIdIsExcluded(city));
        } finally {
            ExplorersCompassConfig.structureWhitelist = originalWhitelist;
            ExplorersCompassConfig.structureBlacklist = originalBlacklist;
        }
    }

    @Test void omittedNamespacesDefaultToMinecraft() {
        List<String> original = ExplorersCompassConfig.structureBlacklist;
        try {
            Identifier vanilla = new Identifier("minecraft:ancient_city");
            Identifier modded = new Identifier("example:ancient_city");
            for (String pattern : List.of("ancient_city", "ancient_*", "*")) {
                ExplorersCompassConfig.structureBlacklist = List.of(pattern);
                assertTrue(StructureUtils.structureIdIsExcluded(vanilla), pattern);
                assertFalse(StructureUtils.structureIdIsExcluded(modded), pattern);
            }
            ExplorersCompassConfig.structureBlacklist = List.of("*:ancient_*");
            assertTrue(StructureUtils.structureIdIsExcluded(vanilla));
            assertTrue(StructureUtils.structureIdIsExcluded(modded));
            ExplorersCompassConfig.structureBlacklist = List.of("exam*:ancient_city");
            assertFalse(StructureUtils.structureIdIsExcluded(vanilla));
            assertTrue(StructureUtils.structureIdIsExcluded(modded));
        } finally {
            ExplorersCompassConfig.structureBlacklist = original;
        }
    }

    @Test void onlyStarActsAsAWildcard() {
        List<String> original = ExplorersCompassConfig.structureBlacklist;
        try {
            Identifier city = new Identifier("minecraft:ancient_city");
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_cit?");
            assertFalse(StructureUtils.structureIdIsExcluded(city));
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_city?");
            assertFalse(StructureUtils.structureIdIsExcluded(city));
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_.*");
            assertFalse(StructureUtils.structureIdIsExcluded(city));
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_city*");
            assertTrue(StructureUtils.structureIdIsExcluded(city));
            ExplorersCompassConfig.structureBlacklist = List.of("*:ancient_*");
            assertTrue(StructureUtils.structureIdIsExcluded(city));
            assertFalse(StructureUtils.structureIdIsExcluded(new Identifier("minecraft:fortress")));
        } finally {
            ExplorersCompassConfig.structureBlacklist = original;
        }
    }
}
