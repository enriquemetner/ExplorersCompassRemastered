package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.ClientSetting;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompassSettingsRowsTest {
    @Test void keepsSettingsInOrderAndBindsResetsToTheirOwnRows() {
        var screen = new CompassSettingsScreen(null);
        var rows = screen.matchingRows("", true);
        assertEquals(List.of("HUD", "View", "Behavior", "Creative & Singleplayer", "Other"),
                rows.stream().filter(CompassSettingsScreen.SettingRow::divider).map(CompassSettingsScreen.SettingRow::label).toList());
        assertEquals(List.of(ClientSetting.OVERLAY_SIDE, ClientSetting.OVERLAY_LINE_OFFSET, ClientSetting.DISPLAY_WITH_CHAT_OPEN,
                        ClientSetting.ADJUST_ROWS_TO_FIT_VIEW, ClientSetting.CHECKED_FILTERS_FIRST, ClientSetting.HIDE_SEARCH_COST,
                        ClientSetting.CLOSE_ON_FOUND, ClientSetting.CLEAR_SEARCH_BAR_ON_CLOSE, ClientSetting.CLEAR_FILTERS_ON_CLOSE,
                        ClientSetting.MATCH_SEARCH_WORDS_IN_ANY_ORDER, ClientSetting.SHOW_ONLY_MATCHING_FILTER_OPTIONS,
                        ClientSetting.CREATIVE_UNLOCKS_ALL, ClientSetting.SHOW_TELEPORT_ONLY_IN_CREATIVE, ClientSetting.PAUSE_GAME_WHEN_OPEN),
                rows.stream().map(CompassSettingsScreen.SettingRow::reset).filter(java.util.Objects::nonNull).toList());

        assertEquals(List.of("View", "Hide Search Costs"), screen.matchingRows("hide cost", true).stream()
                .map(CompassSettingsScreen.SettingRow::label).toList());
        assertTrue(screen.matchingRows("hide cost", false).isEmpty());
    }
}
