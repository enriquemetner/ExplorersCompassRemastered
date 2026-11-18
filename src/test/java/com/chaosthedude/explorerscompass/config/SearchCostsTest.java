package com.chaosthedude.explorerscompass.config;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchCostsTest {
    private static final Identifier CITY = new Identifier("minecraft:ancient_city");
    private static final Identifier PEARL = new Identifier("minecraft:ender_pearl");

    @Test void chargesNothingByDefaultAndAppliesLaterOverrides() {
        var defaults = SearchCosts.parse(SearchCosts.DEFAULT_JSON, key -> Set.of(), id -> false);
        assertEquals(SearchCosts.Type.XP_LEVELS, defaults.type());
        assertEquals(0, defaults.cost(CITY));
        assertFalse(defaults.allowSearchWithoutXp());

        var costs = SearchCosts.parse("""
                {"type":"items","defaultCost":2,"initialEnergy":4,
                 "overrides":{"minecraft:*":1,"minecraft:ancient_city":5},
                 "items":{"minecraft:ender_pearl":2}}
                """, key -> Set.of(CITY), PEARL::equals);
        assertEquals(SearchCosts.Type.ITEMS, costs.type());
        assertEquals(5, costs.cost(CITY));
        assertEquals(2, costs.cost(new Identifier("minecraft:fortress")));
        assertEquals(4, costs.initialEnergy());
        assertEquals(2, costs.items().get(PEARL));
        assertEquals(132, SearchCosts.energyAfterConsumingItems(4, 64, costs.items().get(PEARL)));
        assertEquals(-1, SearchCosts.energyAfterConsumingItems(Integer.MAX_VALUE, 1, 2));
    }

    @Test void reportsOverlappingSelectorsOncePerStructure() {
        var warnings = new java.util.ArrayList<String>();
        var costs = SearchCosts.parse("""
                {
                  "type": "xp",
                  "defaultCost": 0,
                  "overrides": {
                    "minecraft:*": 1,
                    "minecraft:ancient_city": 2,
                    "minecraft:ancient_city": 3
                  }
                }
                """, key -> Set.of(CITY), id -> false, warnings::add);
        assertEquals(3, costs.cost(CITY));
        assertEquals(java.util.List.of(
                "minecraft:ancient_city is listed more than once under \"overrides\" on lines 5, 6, 7"), warnings);
    }

    @Test void validatesSettingsAndKeepsXpTypesDistinct() {
        var xp = SearchCosts.parse("{\"type\":\"xp\",\"defaultCost\":2,\"allowSearchWithoutXp\":true}",
                key -> Set.of(), id -> false);
        assertEquals(SearchCosts.Type.XP, xp.type());
        assertTrue(xp.allowSearchWithoutXp());
        for (String bad : Set.of(
                "{\"type\":\"xp\",\"defaultCost\":1.5}",
                "{\"type\":\"xp\",\"defaultCost\":-1}",
                "{\"type\":\"xp\",\"defaultCost\":1,}",
                "{\"type\":\"xp\",\"defaultCost\":1,\"overrides\":{\"missing\":3}}",
                "{\"type\":\"items\",\"defaultCost\":1,\"initialEnergy\":0,\"items\":{\"unknown\":1}}")) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> SearchCosts.parse(bad, key -> Set.of(), id -> false)).getMessage().contains("line"));
        }
    }

    @Test void xpEquivalentUsesVanillaThresholdsAndRoundsUp() {
        assertEquals(352, SearchCosts.pointsAtLevel(16));
        assertEquals(1507, SearchCosts.pointsAtLevel(31));
        assertEquals(" = 0", SearchCosts.equivalentLevels(0));
        assertEquals(" ≈ 0.3", SearchCosts.equivalentLevels(2));
        assertEquals(" = 1", SearchCosts.equivalentLevels(7));
        assertEquals(" ≈ 16.1", SearchCosts.equivalentLevels(353));
        assertEquals(" = 18.5", SearchCosts.equivalentLevels(467));
        assertEquals(" ≈ 31", SearchCosts.equivalentLevels(1506));
        assertEquals(" = 31", SearchCosts.equivalentLevels(1507));
    }
}
