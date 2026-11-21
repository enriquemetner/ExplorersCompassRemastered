package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.util.RenderUtils;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

final class CurrentStructurePopup {
    private static final String[] EMPTY_LINES = {
            "This feature shows you which structure you're in.",
            "You need to be in a structure to get a result."
    };
    private static final int ROW_HEIGHT = 26;
    private static final int BACK_WIDTH = 70;
    private final TextRenderer font;
    private final List<Identifier> matches;
    private final Consumer<Identifier> select;
    private final Runnable close;
    private final int emptyHeight;
    private double scroll;
    private boolean dragging;
    private double grabOffset;

    CurrentStructurePopup(TextRenderer font, List<Identifier> matches, int emptyHeight, Consumer<Identifier> select, Runnable close) {
        this.font = font;
        this.emptyHeight = emptyHeight;
        this.matches = matches.stream().sorted(Comparator.comparing(StructureUtils::getStructureName,
                String.CASE_INSENSITIVE_ORDER).thenComparing(Identifier::toString)).toList();
        this.select = select;
        this.close = close;
    }

    private record Layout(int x, int y, int width, int height) {
        int top() { return y + 28; }
        int bottom() { return y + height - 36; }
        int backX() { return x + (width - BACK_WIDTH) / 2; }
        int backY() { return y + height - 30; }
        int rowWidth() { return width - 28; }
    }

    private Layout layout(int width, int height) {
        if (matches.isEmpty()) {
            int textWidth = Math.max(font.getWidth(EMPTY_LINES[0]), font.getWidth(EMPTY_LINES[1]));
            int w = Math.min(textWidth + 20, width - 20);
            return new Layout((width - w) / 2, (height - emptyHeight) / 2, w, emptyHeight);
        }
        int w = Math.min(380, width - 20);
        int h = Math.min(64 + Math.max(ROW_HEIGHT, matches.size() * ROW_HEIGHT), height - 20);
        return new Layout((width - w) / 2, (height - h) / 2, w, h);
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, matches.size() * ROW_HEIGHT - (layout.bottom() - layout.top()));
    }

    private Scrollbar scrollbar(Layout layout) {
        int track = layout.bottom() - layout.top();
        return new Scrollbar(layout.x + layout.width - 15, layout.top(), layout.bottom(),
                Scrollbar.pixelThumbHeight(track, maxScroll(layout), 12, 0), scroll, maxScroll(layout));
    }

    void render(DrawContext context, int width, int height, int mouseX, int mouseY) {
        Layout box = layout(width, height);
        scroll = MathHelper.clamp(scroll, 0, maxScroll(box));
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(0, 0, width, height, 0x88000000);
        context.fill(box.x - 1, box.y - 1, box.x + box.width + 1, box.y + box.height + 1, 0xff909090);
        context.fill(box.x, box.y, box.x + box.width, box.y + box.height, 0xff000000);
        context.drawCenteredTextWithShadow(font, matches.isEmpty() ? "Not Found" : "Multiple Structures Identified", width / 2, box.y + 10, matches.isEmpty() ? 0xff5555 : 0xffffff);
        if (matches.isEmpty()) {
            for (int i = 0; i < EMPTY_LINES.length; i++) {
                Text line = Text.literal(EMPTY_LINES[i]);
                int lineWidth = Math.min(font.getWidth(line), box.width - 20);
                RenderUtils.drawTextToFit(context, font, line, (width - lineWidth) / 2,
                        (box.top() + box.bottom() - 2 * font.fontHeight - 3) / 2 + i * (font.fontHeight + 3), lineWidth, 0xffffff);
            }
        } else {
            context.enableScissor(box.x + 8, box.top(), box.x + 8 + box.rowWidth(), box.bottom());
            for (int i = (int) scroll / ROW_HEIGHT; i < matches.size(); i++) {
                int y = box.top() + i * ROW_HEIGHT - (int) scroll;
                if (y >= box.bottom()) break;
                int x = box.x + 8;
                boolean hovered = mouseY >= box.top() && mouseY < box.bottom()
                        && ButtonPress.contains(mouseX, mouseY, x, y, box.rowWidth(), ROW_HEIGHT - 2);
                drawButton(context, x, y, box.rowWidth(), ROW_HEIGHT - 2, hovered);
                Identifier id = matches.get(i);
                RenderUtils.drawTextToFit(context, font, Text.literal(StructureUtils.getStructureName(id)),
                        x + 4, y + 3, box.rowWidth() - 8, 0xffffff);
                RenderUtils.drawTextToFit(context, font, Text.literal(id.toString()),
                        x + 4, y + 13, box.rowWidth() - 8, 0xaaaaaa);
            }
            context.disableScissor();
            scrollbar(box).render(context, 0xff242424, 0xff909090);
        }
        boolean backHovered = ButtonPress.contains(mouseX, mouseY, box.backX(), box.backY(), BACK_WIDTH, 20);
        drawButton(context, box.backX(), box.backY(), BACK_WIDTH, 20, backHovered);
        context.drawCenteredTextWithShadow(font, "Back", width / 2, box.backY() + 6,
                0xffffff);
        context.getMatrices().pop();
    }

    private static void drawButton(DrawContext context, int x, int y, int w, int h, boolean hovered) {
        context.fill(x, y, x + w, y + h, 0xff909090);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0xff424242 : 0xff242424);
    }

    ButtonPress buttonAt(double x, double y, int width, int height) {
        Layout box = layout(width, height);
        if (ButtonPress.contains(x, y, box.backX(), box.backY(), BACK_WIDTH, 20)) {
            return new ButtonPress(this, "back", close);
        }
        if (y < box.top() || y >= box.bottom() || x < box.x + 8 || x >= box.x + 8 + box.rowWidth()) return null;
        int offset = (int) (y - box.top()) + (int) scroll;
        int row = offset / ROW_HEIGHT;
        if (row >= matches.size() || offset % ROW_HEIGHT >= ROW_HEIGHT - 2) return null;
        Identifier id = matches.get(row);
        return new ButtonPress(this, id, () -> select.accept(id));
    }

    boolean click(double x, double y, int button, int width, int height) {
        Layout box = layout(width, height);
        if (!ButtonPress.contains(x, y, box.x - 1, box.y - 1, box.width + 2, box.height + 2)) {
            close.run();
            return true;
        } else if (button == 0 && scrollbar(box).contains(x, y)) {
            dragging = true;
            grabOffset = scrollbar(box).grabOffset(y, 0);
            drag(y, width, height);
        }
        return false;
    }

    void scroll(double amount, int width, int height) {
        scroll = Scrollbar.scrollPixels(scroll, amount, ROW_HEIGHT, maxScroll(layout(width, height)));
    }

    void drag(double y, int width, int height) {
        if (dragging) scroll = scrollbar(layout(width, height)).offsetForMouse(y, grabOffset);
    }

    void release() { dragging = false; }
}
