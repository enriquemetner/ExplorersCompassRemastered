package com.chaosthedude.explorerscompass.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonPressTest {
    @Test void releaseMustHitTheSameOwnerAndAction() {
        Object row = new Object();
        ButtonPress pressed = new ButtonPress(row, "more", () -> {});
        assertTrue(pressed.matches(new ButtonPress(row, "more", () -> {})));
        assertFalse(pressed.matches(new ButtonPress(row, "filters", () -> {})));
        assertFalse(pressed.matches(new ButtonPress(new Object(), "more", () -> {})));
        assertFalse(pressed.matches(null));
    }

    @Test void releaseUsesCurrentButtonBoundsAndCanReturnFromOutside() {
        Object popup = new Object();
        Runnable action = () -> {};
        ButtonPress pressed = ButtonPress.at(10, 10, 10, 10, 20, 20, popup, "back", action);
        assertNull(ButtonPress.at(30, 10, 10, 10, 20, 20, popup, "back", action));
        assertNull(ButtonPress.at(10, 30, 10, 10, 20, 20, popup, "back", action));
        assertTrue(pressed.matches(ButtonPress.at(29, 29, 10, 10, 20, 20, popup, "back", action)));
        assertFalse(pressed.matches(ButtonPress.at(10, 10, 40, 10, 20, 20, popup, "back", action)));
        assertTrue(pressed.matches(ButtonPress.at(40, 10, 40, 10, 20, 20, popup, "back", action)));
    }
}
