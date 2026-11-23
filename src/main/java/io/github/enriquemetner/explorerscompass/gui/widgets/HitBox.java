package io.github.enriquemetner.explorerscompass.gui.widgets;

public final class HitBox {
    private HitBox() {}

    public static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
