package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FilterSelectionsTest {
    @Test void currentAndNamedDimensionsAreIndependentAcrossDimensionChanges() {
        Identifier overworld = new Identifier("minecraft:overworld");
        Identifier nether = new Identifier("minecraft:the_nether");
        FilterSelections.clear();
        try {
            FilterSelections.openedInDimension(overworld);
            FilterSelections.toggleCurrent();
            assertTrue(FilterSelections.isCurrentSelected());
            assertTrue(FilterSelections.dimensions.isEmpty());
            assertFalse(FilterSelections.isEmpty());
            FilterSelections.toggleDimension(overworld);
            FilterSelections.toggleCurrent();
            assertEquals(Set.of(overworld), FilterSelections.dimensions);
            FilterSelections.toggleCurrent();
            FilterSelections.toggleDimension(overworld);
            assertTrue(FilterSelections.isCurrentSelected());
            assertTrue(FilterSelections.dimensions.isEmpty());
            FilterSelections.toggleDimension(overworld);
            FilterSelections.openedInDimension(nether);
            assertTrue(FilterSelections.isCurrentSelected());
            assertEquals(Set.of(overworld), FilterSelections.dimensions);
            FilterSelections.clear();
            assertFalse(FilterSelections.isCurrentSelected());
            assertTrue(FilterSelections.isEmpty());
        } finally {
            FilterSelections.clear();
        }
    }

    @Test void offersOptionsUsingOnlyTheOtherColumnsAndEligibleStructures() {
        var plains = new Identifier("minecraft:village_plains");
        var fortress = new Identifier("minecraft:fortress");
        var tower = new Identifier("example:tower");
        var villageSet = new Identifier("minecraft:villages");
        var fortressSet = new Identifier("minecraft:fortresses");
        var towerSet = new Identifier("example:towers");
        var overworld = new Identifier("minecraft:overworld");
        var nether = new Identifier("minecraft:the_nether");
        var end = new Identifier("minecraft:the_end");
        var oldUnlocks = ClientPacketHandlers.latestUnlockedStructures;
        var dimensions = ArrayListMultimap.<Identifier, Identifier>create();
        dimensions.put(plains, overworld);
        dimensions.put(fortress, nether);
        dimensions.putAll(tower, List.of(nether, end));
        List<Identifier> structures = List.of(plains, fortress, tower);
        var sets = Map.of(plains, villageSet, fortress, fortressSet, tower, towerSet);
        FilterSelections.clear();
        try {
            FilterSelections.sources.add("example");
            var available = FilterSelections.availableOptions(structures, sets, dimensions, id -> true);
            assertEquals(Set.of("example", "minecraft"), available.sources());
            assertEquals(Set.of(nether, end), available.dimensions());
            assertEquals(Set.of(towerSet), available.sets());

            FilterSelections.openedInDimension(overworld);
            FilterSelections.toggleCurrent();
            FilterSelections.sets.add(towerSet);
            available = FilterSelections.availableOptions(structures, sets, dimensions, id -> true);
            assertTrue(available.sources().isEmpty());
            assertEquals(Set.of(nether, end), available.dimensions());
            assertTrue(available.sets().isEmpty());

            FilterSelections.sources.add("minecraft");
            FilterSelections.toggleDimension(nether);
            available = FilterSelections.availableOptions(structures, sets, dimensions, id -> true);
            assertEquals(Set.of("example"), available.sources());
            assertEquals(Set.of(villageSet, fortressSet, towerSet), available.sets());

            FilterSelections.clear();
            ClientPacketHandlers.latestUnlockedStructures = Set.of(plains, tower);
            FilterSelections.toggleNewlyUnlocked();
            FilterSelections.toggleExcludeVisited();
            available = FilterSelections.availableOptions(structures, sets, dimensions, id -> !id.equals(tower));
            assertEquals(Set.of("minecraft"), available.sources());
            assertEquals(Set.of(overworld), available.dimensions());
            assertEquals(Set.of(villageSet), available.sets());

            FilterSelections.clear();
            available = FilterSelections.availableOptions(structures, sets, dimensions, id -> true);
            assertEquals(Set.of(overworld, nether, end), available.dimensions());
            assertEquals(Set.of(villageSet, fortressSet, towerSet), available.sets());
        } finally {
            FilterSelections.clear();
            ClientPacketHandlers.latestUnlockedStructures = oldUnlocks;
        }
    }
}
