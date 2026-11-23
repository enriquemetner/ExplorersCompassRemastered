package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.gui.widgets.HitBox;

import net.minecraft.client.gui.DrawContext;

final class DialogFrame {
    private DialogFrame() {}
    static int standardWidth(int screenWidth) {
        return Math.max(40, Math.min(260, screenWidth - 24));
    }

    record Bounds(int x, int y, int width, int height) {
        boolean outside(double mouseX, double mouseY) {
            return !HitBox.contains(mouseX, mouseY, x - 1, y - 1, width + 2, height + 2);
        }
    }

    static Bounds centered(int screenWidth, int screenHeight, int width, int height) {
        return new Bounds((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    static void begin(DrawContext context, int screenWidth, int screenHeight, int depth,
                      int x, int y, int width, int height) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, depth);
        context.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xff909090);
        context.fill(x, y, x + width, y + height, 0xff000000);
    }
}
