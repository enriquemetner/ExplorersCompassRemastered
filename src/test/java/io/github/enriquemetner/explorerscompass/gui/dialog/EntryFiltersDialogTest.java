package io.github.enriquemetner.explorerscompass.gui.dialog;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntryFiltersDialogTest {
    @Test void applyingAFilterClosesTheDialogIfTheEntryDisappears() {
        var visible = new AtomicBoolean(true);
        var closed = new AtomicInteger();
        var sounds = new AtomicInteger();
        var rows = List.of(new EntryFiltersDialog.Row("Dimension:", "Overworld", visible::get,
                () -> visible.set(false)));
        var dialog = new EntryFiltersDialog(null, () -> rows, closed::incrementAndGet, visible::get,
                sounds::incrementAndGet, (context, x, y, checked) -> fail("No drawing expected"));

        assertTrue(dialog.click(150, 90, 0, 300, 200));
        assertFalse(visible.get());
        assertEquals(1, closed.get());
        assertEquals(1, sounds.get());
    }
}
