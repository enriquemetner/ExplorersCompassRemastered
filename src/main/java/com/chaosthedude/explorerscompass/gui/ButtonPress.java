package com.chaosthedude.explorerscompass.gui;

/** Identifies a button within its owning widget, row, or popup. */
record ButtonPress(Object owner, Object key, Runnable action) {
    static ButtonPress at(double mouseX, double mouseY, int x, int y, int width, int height,
                          Object owner, Object key, Runnable action) {
        return contains(mouseX, mouseY, x, y, width, height) ? new ButtonPress(owner, key, action) : null;
    }

    static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    boolean matches(ButtonPress other) {
        return other != null && owner == other.owner && key.equals(other.key);
    }
}
