package com.chaosthedude.explorerscompass.gui;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
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
}
