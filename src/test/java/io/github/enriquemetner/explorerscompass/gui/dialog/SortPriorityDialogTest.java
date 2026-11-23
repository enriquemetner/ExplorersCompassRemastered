package io.github.enriquemetner.explorerscompass.gui.dialog;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SortPriorityDialogTest {
    @Test void savesAChangeOnceOnReleaseOrClose() {
        var saved = new ArrayList<List<String>>();
        var dialog = new SortPriorityDialog(List.of("NAME", "SOURCE", "SET"), saved::add, () -> {});

        dialog.open();
        dialog.grab(0);
        dialog.moveTo(0);
        assertTrue(dialog.release(0));
        assertTrue(saved.isEmpty());

        dialog.grab(0);
        dialog.moveTo(2);
        assertTrue(saved.isEmpty());
        assertTrue(dialog.release(0));
        assertEquals(List.of(List.of("SOURCE", "SET", "NAME")), saved);
        dialog.close();
        assertEquals(1, saved.size());

        dialog.open();
        dialog.grab(2);
        dialog.moveTo(0);
        dialog.close();
        assertEquals(List.of("NAME", "SOURCE", "SET"), saved.get(1));
        assertFalse(dialog.release(0));
    }
}
