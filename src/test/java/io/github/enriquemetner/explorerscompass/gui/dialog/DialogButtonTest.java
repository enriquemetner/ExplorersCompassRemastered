package io.github.enriquemetner.explorerscompass.gui.dialog;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogButtonTest {
    @Test void findsAnActionOnMouseDownWithHalfOpenBounds() {
        var calls = new AtomicInteger();
        var buttons = List.of(new DialogButton(10, 12, 24, "Back", calls::incrementAndGet));

        var action = DialogButton.actionAt(buttons, 10, 12);
        assertNotNull(action);
        assertEquals(0, calls.get());
        action.run();
        assertEquals(1, calls.get());

        assertNotNull(DialogButton.actionAt(buttons, 33, 31));
        assertNull(DialogButton.actionAt(buttons, 34, 31));
        assertNull(DialogButton.actionAt(buttons, 33, 32));
        assertNull(DialogButton.actionAt(buttons, 9, 12));
    }
}
