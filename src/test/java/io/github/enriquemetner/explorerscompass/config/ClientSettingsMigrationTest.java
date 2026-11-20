package io.github.enriquemetner.explorerscompass.config;

import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientSettingsMigrationTest {
    @Test
    void rewritesLegacyKeysAndClampsNegativeOffset() {
        TomlParser parser = new TomlParser();
        var legacy = parser.parse("""
                [overlay]
                overlaySide = "RIGHT"
                overlayLineOffset = -2
                displayWithChatOpen = false

                [view]
                checkedFiltersFirst = true

                [behavior]
                hideSearchCost = true
                matchSearchWordsInAnyOrder = true
                pauseGameWhenOpen = true

                [singleplayerAndCreative]
                creativeUnlocksAll = false
                showTeleportOnlyInCreative = true

                [creativeAndSingleplayer]
                showTeleportOnlyInCreative = false
                """);

        ExplorersCompassConfig.restoreClientDefaults();
        try {
            ExplorersCompassConfig.loadClient(legacy);
            var saved = parser.parse(ExplorersCompassConfig.serializeClientSettings());
            assertEquals("RIGHT", saved.get("hud.hudSide"));
            assertEquals(Integer.valueOf(0), saved.get("hud.verticalOffset"));
            assertEquals(Boolean.FALSE, saved.get("hud.displayWithChatOpen"));
            assertEquals(Boolean.TRUE, saved.get("view.checkedFiltersOnTop"));
            assertEquals(Boolean.TRUE, saved.get("view.hideSearchCosts"));
            assertEquals(Boolean.TRUE, saved.get("behavior.flexibleSearch"));
            assertEquals(Boolean.FALSE, saved.get("creativeAndSingleplayer.creativeUnlocksAll"));
            assertEquals(Boolean.FALSE, saved.get("creativeAndSingleplayer.showTeleportOnlyInCreative"));
            assertEquals(Boolean.TRUE, saved.get("creativeAndSingleplayer.pauseGameWhileOpen"));
            assertFalse(saved.contains("overlay"));
            assertFalse(saved.contains("singleplayerAndCreative"));
            assertFalse(saved.contains("behavior.pauseGameWhenOpen"));
        } finally {
            ExplorersCompassConfig.restoreClientDefaults();
        }
    }
}
