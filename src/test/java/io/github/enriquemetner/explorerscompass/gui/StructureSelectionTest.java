package io.github.enriquemetner.explorerscompass.gui;

import java.util.List;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSelectionTest {
    private static final Identifier A = new Identifier("test:a");
    private static final Identifier B = new Identifier("test:b");
    private static final Identifier C = new Identifier("test:c");
    private static final Identifier D = new Identifier("test:d");
    private static final List<Identifier> ROWS = List.of(A, B, C, D);

    @Test void controlTogglesAndPlainClickReplaces() {
        var selection = new StructureSelection();
        selection.click(A, ROWS, false, false, false);
        selection.click(C, ROWS, true, false, false);
        assertEquals(List.of(A, C), selection.ids());
        selection.click(A, ROWS, true, false, false);
        assertEquals(List.of(C), selection.ids());
        selection.click(B, ROWS, false, false, false);
        assertEquals(List.of(B), selection.ids());
    }

    @Test void shiftReplacesAndControlShiftAddsInCurrentOrder() {
        var selection = new StructureSelection();
        selection.click(A, ROWS, false, false, false);
        selection.click(D, ROWS, true, false, false);
        selection.click(B, ROWS, true, true, false);
        assertEquals(List.of(A, D, B, C), selection.ids());

        selection.click(C, ROWS, false, true, false);
        assertEquals(List.of(C, D), selection.ids());
        selection.click(B, List.of(D, C, A, B), false, true, false);
        assertEquals(List.of(D, C, A, B), selection.ids());
    }

    @Test void selectAllAddsMatchesWithoutLosingSelectionsFromOtherQueries() {
        var selection = new StructureSelection();
        selection.click(A, ROWS, false, false, false);
        selection.addAll(List.of(B, C));
        selection.addAll(List.of(C, D));
        assertEquals(ROWS, selection.ids());
    }

    @Test void shiftStartsNewRangeWhenPreviousAnchorIsOutsideResults() {
        var selection = new StructureSelection();
        selection.click(A, ROWS, false, false, false);
        selection.click(C, List.of(B, C, D), false, true, false);
        assertEquals(List.of(C), selection.ids());
        selection.click(D, List.of(B, C, D), false, true, false);
        assertEquals(List.of(C, D), selection.ids());
    }

    @Test void removingUnavailableIdsAndClearingSelectionAlsoResetTheirAnchors() {
        var selection = new StructureSelection();
        selection.addAll(ROWS);
        selection.retain(id -> !id.equals(A) && !id.equals(C));
        assertEquals(List.of(B, D), selection.ids());
        selection.click(D, List.of(B, D), false, true, false);
        assertEquals(List.of(D), selection.ids());

        selection.clear();
        assertTrue(selection.isEmpty());
        selection.click(B, ROWS, false, true, false);
        assertEquals(List.of(B), selection.ids());
    }

    @Test void focusFollowsClicksAndFallsBackToMostRecentlyAddedSelection() {
        var selection = new StructureSelection();
        selection.click(A, ROWS, false, false, false);
        selection.click(B, ROWS, true, false, false);
        selection.click(C, ROWS, true, false, false);
        assertEquals(C, selection.lead());

        selection.click(A, ROWS, true, false, false);
        assertEquals(C, selection.lead());
        selection.click(C, ROWS, true, false, true);
        assertEquals(B, selection.lead());
        selection.click(B, ROWS, true, false, true);
        assertNull(selection.lead());
    }

    @Test void selectedOnlyPlainAndShiftClicksKeepJustTheClickedStructure() {
        var selection = new StructureSelection();
        selection.addAll(ROWS);
        selection.click(B, List.of(), false, false, true);
        assertEquals(List.of(B), selection.ids());

        selection.addAll(ROWS);
        selection.click(C, List.of(), false, true, true);
        assertEquals(List.of(C), selection.ids());

        selection.addAll(ROWS);
        selection.click(D, List.of(), true, true, true);
        assertEquals(List.of(D), selection.ids());
    }

    @Test void selectedOnlyControlClickRemovesJustTheClickedStructure() {
        var selection = new StructureSelection();
        selection.addAll(ROWS);
        selection.click(B, List.of(), true, false, true);
        assertEquals(List.of(A, C, D), selection.ids());
    }
}
