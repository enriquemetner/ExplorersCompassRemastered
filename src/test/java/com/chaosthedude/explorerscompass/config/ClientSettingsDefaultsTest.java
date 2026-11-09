package com.chaosthedude.explorerscompass.config;

import java.util.List;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig.ClientSetting;
import com.chaosthedude.explorerscompass.util.OverlaySide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSettingsDefaultsTest {
    @Test void resetsOneSettingIndependentlyAndBulkResetKeepsHiddenStructures() {
        var oldSide = ExplorersCompassConfig.overlaySide;
        int oldOffset = ExplorersCompassConfig.overlayLineOffset;
        boolean oldFittedRows = ExplorersCompassConfig.adjustRowsToFitView;
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

            ExplorersCompassConfig.restoreClientDefaults();
            assertEquals(OverlaySide.LEFT, ExplorersCompassConfig.overlaySide);
            assertTrue(ExplorersCompassConfig.adjustRowsToFitView);
            assertEquals(List.of("NAME", "SOURCE", "DIMENSION", "SET"), ExplorersCompassConfig.sortOrder);
            assertEquals(hidden, ExplorersCompassConfig.hiddenStructures);
        } finally {
            ExplorersCompassConfig.overlaySide = oldSide;
            ExplorersCompassConfig.overlayLineOffset = oldOffset;
            ExplorersCompassConfig.adjustRowsToFitView = oldFittedRows;
            ExplorersCompassConfig.sortOrder = oldOrder;
            ExplorersCompassConfig.hiddenStructures = oldHidden;
        }
    }
}
