package io.github.enriquemetner.explorerscompass.gui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsSearchTest {
    private static final String[] LABELS = {
        "Overlay", "HUD Side", "HUD Line Offset",
        "Singleplayer & Creative", "Pause Game When Open", "Show Teleport Only in Creative",
        "Other", "Hidden Structures"
    };

    @Test void matchesPartialWordsAcrossSettingAndSectionNames() {
        assertEquals(List.of(3, 5), search("  TELE  single "));
        assertEquals(List.of(3, 5), search("crea tele"));
        assertEquals(List.of(6, 7), search("hid stru"));
        assertEquals(List.of(), search("overlay tele"));
    }

    @Test void keepsSectionOrderAndRestoresAllRowsWhenCleared() {
        assertEquals(List.of(0, 1, 2), search("overlay"));
        assertEquals(List.of(3, 4, 5), search("creative"));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), search("   "));
    }

    private static List<Integer> search(String query) {
        return SettingsSearch.matchingRows(LABELS, row -> row == 0 || row == 3 || row == 6, query);
    }
}
