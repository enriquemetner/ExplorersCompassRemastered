package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.gui.widgets.HitBox;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

record DialogButton(int x, int y, int width, Text label, Runnable action) {
    static final int HEIGHT = 20;

    DialogButton(int x, int y, int width, String label, Runnable action) {
        this(x, y, width, Text.literal(label), action);
    }

    boolean contains(double mouseX, double mouseY) {
        return HitBox.contains(mouseX, mouseY, x, y, width, HEIGHT);
    }

    void render(DrawContext context, TextRenderer font, int mouseX, int mouseY) {
        render(context, font, mouseX, mouseY, x + width / 2);
    }

    void render(DrawContext context, TextRenderer font, int mouseX, int mouseY, int labelCenter) {
        background(context, x, y, width, HEIGHT, contains(mouseX, mouseY));
        context.drawCenteredTextWithShadow(font, label, labelCenter, y + 6, 0xffffff);
    }

    static void background(DrawContext context, int x, int y, int width, int height, boolean hovered) {
        context.fill(x, y, x + width, y + height, 0xff909090);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered ? 0xff424242 : 0xff242424);
    }

    static Runnable actionAt(List<DialogButton> buttons, double x, double y) {
        for (DialogButton button : buttons) {
            if (button.contains(x, y)) return button.action();
        }
        return null;
    }
}
