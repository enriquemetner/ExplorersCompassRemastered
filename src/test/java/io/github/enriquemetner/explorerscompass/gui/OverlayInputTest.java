package io.github.enriquemetner.explorerscompass.gui;

import org.junit.jupiter.api.Test;

import static io.github.enriquemetner.explorerscompass.gui.OverlayInput.Event.*;
import static io.github.enriquemetner.explorerscompass.gui.OverlayInput.Layer.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OverlayInputTest {
    @Test void disabledDialogRetainsItsEventSpecificPriority() {
        assertEquals(DETAILS, OverlayInput.layer(ACTION, false, true, true, false, DISABLED));
        assertEquals(DISABLED, OverlayInput.layer(POINTER, true, true, true, false, DISABLED));
        assertEquals(ENTRY_FILTERS, OverlayInput.layer(KEY, false, false, true, false, DISABLED));
    }

    @Test void sortDoesNotStealWheelOrDragFromTheSearchLayer() {
        assertEquals(SORT, OverlayInput.layer(ACTION, false, false, false, true, LOCKED));
        assertEquals(LOCKED, OverlayInput.layer(POINTER, false, false, false, true, LOCKED));
        assertEquals(SORT, OverlayInput.layer(KEY, false, false, false, true, LOCKED));
    }
}
