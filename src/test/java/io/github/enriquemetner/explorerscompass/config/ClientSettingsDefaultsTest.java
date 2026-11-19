package io.github.enriquemetner.explorerscompass.config;

import java.util.List;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.ClientSetting;
import io.github.enriquemetner.explorerscompass.util.OverlaySide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientSettingsDefaultsTest {
    @Test void resetsOneSettingIndependentlyAndBulkResetKeepsHiddenStructures() {
        var oldSide = ExplorersCompassConfig.overlaySide;
        int oldOffset = ExplorersCompassConfig.overlayLineOffset;
        boolean oldFittedRows = ExplorersCompassConfig.adjustRowsToFitView;
        boolean oldMatchingOptions = ExplorersCompassConfig.showOnlyMatchingFilterOptions;
        var oldOrder = ExplorersCompassConfig.sortOrder;
        var oldHidden = ExplorersCompassConfig.hiddenStructures;
        try {
            var customOrder = List.of("SET", "NAME", "SOURCE", "DIMENSION");
            var hidden = List.of("minecraft:fortress");
            ExplorersCompassConfig.overlaySide = OverlaySide.RIGHT;
            ExplorersCompassConfig.overlayLineOffset = 10;
            ExplorersCompassConfig.adjustRowsToFitView = false;
            ExplorersCompassConfig.sortOrder = customOrder;
            ExplorersCompassConfig.hiddenStructures = hidden;

            ClientSetting.OVERLAY_LINE_OFFSET.restoreDefault();
            assertEquals(0, ExplorersCompassConfig.overlayLineOffset);
            assertEquals(OverlaySide.RIGHT, ExplorersCompassConfig.overlaySide);
            assertEquals(customOrder, ExplorersCompassConfig.sortOrder);

            ExplorersCompassConfig.showOnlyMatchingFilterOptions = true;
            ClientSetting.SHOW_ONLY_MATCHING_FILTER_OPTIONS.restoreDefault();
            assertFalse(ExplorersCompassConfig.showOnlyMatchingFilterOptions);
            assertEquals(customOrder, ExplorersCompassConfig.sortOrder);

            ExplorersCompassConfig.showOnlyMatchingFilterOptions = true;
            ExplorersCompassConfig.restoreClientDefaults();
            assertFalse(ExplorersCompassConfig.showOnlyMatchingFilterOptions);
            assertEquals(OverlaySide.LEFT, ExplorersCompassConfig.overlaySide);
            assertTrue(ExplorersCompassConfig.adjustRowsToFitView);
            assertEquals(List.of("NAME", "SOURCE", "DIMENSION", "SET"), ExplorersCompassConfig.sortOrder);
            assertEquals(hidden, ExplorersCompassConfig.hiddenStructures);
        } finally {
            ExplorersCompassConfig.overlaySide = oldSide;
            ExplorersCompassConfig.overlayLineOffset = oldOffset;
            ExplorersCompassConfig.adjustRowsToFitView = oldFittedRows;
            ExplorersCompassConfig.showOnlyMatchingFilterOptions = oldMatchingOptions;
            ExplorersCompassConfig.sortOrder = oldOrder;
            ExplorersCompassConfig.hiddenStructures = oldHidden;
        }
    }
}
