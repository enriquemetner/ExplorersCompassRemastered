package io.github.enriquemetner.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** A modal view of one entry, independent of the list's technical-name setting. */
final class EntryDetailsPopup {
    private static final int ACTION_HEIGHT = 20;
    private static final int TEXT_INSET = 12;
    private static final String COPY_HINT = "Click on IDs to copy";
    static final String HIDE_TOOLTIP = "Removes the structure from the list on this client, across all worlds and servers.\nYou can undo this in Settings.";
    private final MinecraftClient client;
    private final TextRenderer textRenderer;
    private final List<Detail> details = new ArrayList<>();
    private final BooleanSupplier hidden;
    private final Runnable toggleHidden;
    private final BooleanSupplier requirementsVisible;
    private final Runnable showRequirements;
    private final Runnable close;
    private double scroll;
    private boolean dragging;
    private double grabOffset;

    private record Detail(String label, String readable, String technical) {
        String readableLine() { return label + ": " + value(readable); }
        String technicalLine() { return value(technical); }
        boolean canCopy() { return technical != null && !technical.isEmpty(); }
        private static String value(String text) { return text == null || text.isEmpty() ? "None" : text; }
    }

    private record Layout(int x, int y, int width, int height, int top, int bottom, int actionCount) {
        int textX() { return x + TEXT_INSET; }
        int textWidth() { return width - 34; }
        int actionsY() { return y + height - 30; }
        int actionWidth() { return Math.min(82, (width - 8 * actionCount) / actionCount); }
        int actionX(int index) {
            return x + (width - actionCount * actionWidth() - (actionCount - 1) * 8) / 2 + index * (actionWidth() + 8);
        }
    }

    EntryDetailsPopup(MinecraftClient client, Identifier id, BooleanSupplier hidden, Runnable toggleHidden,
                      BooleanSupplier requirementsVisible, Runnable showRequirements, Runnable close) {
        this.client = client;
        this.textRenderer = client.textRenderer;
        this.hidden = hidden;
        this.toggleHidden = toggleHidden;
        this.requirementsVisible = requirementsVisible;
        this.showRequirements = showRequirements;
        this.close = close;
        details.add(new Detail("Name", StructureUtils.getStructureName(id), id.toString()));
        details.add(new Detail("Source", StructureUtils.getStructureSource(id), id.getNamespace()));
        Identifier set = ExplorersCompass.structureIDsToSetIDs.get(id);
        details.add(new Detail("Set", StructureUtils.formatIdPath(set), set == null ? null : set.toString()));
        List<Identifier> dimensions = ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id);
        if (dimensions == null || dimensions.isEmpty()) {
            details.add(new Detail("Dimension", null, null));
        } else {
            for (Identifier dimension : dimensions) {
                details.add(new Detail("Dimension", StructureUtils.getDimensionName(dimension), dimension.toString()));
            }
        }
    }

    private Layout layout(int screenWidth, int screenHeight) {
        int width = Math.min(380, screenWidth - 20);
        int height = Math.min(contentHeight() + 64, screenHeight - 20);
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        return new Layout(x, y, width, height, y + 28, y + height - 36, requirementsVisible.getAsBoolean() ? 3 : 2);
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, contentHeight() - (layout.bottom - layout.top));
    }

    private int rowHeight() {
        return textRenderer.fontHeight * 2 + 14;
    }

    private int contentHeight() {
        return (details.size() - 1) * rowHeight() + 4 + textRenderer.fontHeight * 2;
    }

    private Scrollbar scrollbar(Layout layout) {
        int track = layout.bottom - layout.top;
        return new Scrollbar(layout.x + layout.width - 15, layout.top, layout.bottom,
                Scrollbar.pixelThumbHeight(track, maxScroll(layout), 12, 0), scroll, maxScroll(layout));
    }

    void render(DrawContext context, int width, int height, int mouseX, int mouseY) {
        Layout layout = layout(width, height);
        scroll = MathHelper.clamp(scroll, 0, maxScroll(layout));
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(0, 0, width, height, 0x88000000);
        context.fill(layout.x - 1, layout.y - 1, layout.x + layout.width + 1, layout.y + layout.height + 1, 0xff909090);
        context.fill(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, 0xff000000);
        int titleY = layout.y + 10;
        String title = "Details";
        int hintX = layout.x + layout.width - TEXT_INSET - textRenderer.getWidth(COPY_HINT);
        int titleCenter = Math.min(width / 2, hintX - 8 - (textRenderer.getWidth(title) + 1) / 2);
        context.drawCenteredTextWithShadow(textRenderer, title, titleCenter, titleY, 0xffffff);
        context.drawTextWithShadow(textRenderer, COPY_HINT, hintX, titleY, 0xaaaaaa);
        context.enableScissor(layout.x + 8, layout.top, layout.x + layout.width - 18, layout.bottom);
        for (int i = 0; i < details.size(); i++) {
            Detail detail = details.get(i);
            int rowY = layout.top + i * rowHeight() - (int) scroll;
            int technicalY = rowY + 4 + textRenderer.fontHeight;
            String readable = detail.readableLine();
            String technical = detail.technicalLine();
            boolean idHovered = idHovered(detail, layout, technicalY, mouseX, mouseY);
            drawLine(context, readable, layout.textX(), rowY + 4, layout.textWidth(), 0xffffff);
            drawLine(context, (idHovered ? "§l" : "") + technical, layout.textX(), technicalY, layout.textWidth(), 0xaaaaaa);
        }
        context.disableScissor();
        scrollbar(layout).render(context, 0xff242424, 0xff909090);
        drawButton(context, hidden.getAsBoolean() ? "Undo" : "Hide", layout, 0, mouseX, mouseY);
        if (layout.actionCount() == 3) drawButton(context, "Requirements", layout, 1, mouseX, mouseY);
        drawButton(context, "Close", layout, layout.actionCount() - 1, mouseX, mouseY);
        if (!hidden.getAsBoolean() && actionHovered(layout, 0, mouseX, mouseY)) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(HIDE_TOOLTIP), Math.min(220, width - 30)), mouseX, mouseY);
        }
        context.getMatrices().pop();
    }

    private void drawLine(DrawContext context, String value, int x, int y, int width, int color) {
        String visible = textRenderer.getWidth(value) > width
                ? textRenderer.trimToWidth(value, Math.max(0, width - textRenderer.getWidth("..."))) + "..." : value;
        context.drawTextWithShadow(textRenderer, visible, x, y, color);
    }

    private boolean idHovered(Detail detail, Layout layout, int y, double mouseX, double mouseY) {
        return detail.canCopy() && mouseY >= layout.top && mouseY < layout.bottom
                && inside(mouseX, mouseY, layout.textX(), y,
                        Math.min(textRenderer.getWidth("§l" + detail.technical()), layout.textWidth()), textRenderer.fontHeight);
    }

    private boolean actionHovered(Layout layout, int index, double mouseX, double mouseY) {
        return inside(mouseX, mouseY, layout.actionX(index), layout.actionsY(), layout.actionWidth(), ACTION_HEIGHT);
    }

    private void drawButton(DrawContext context, String label, Layout layout, int index, int mouseX, int mouseY) {
        int x = layout.actionX(index);
        int y = layout.actionsY();
        int width = layout.actionWidth();
        context.fill(x, y, x + width, y + ACTION_HEIGHT, 0xff909090);
        context.fill(x + 1, y + 1, x + width - 1, y + ACTION_HEIGHT - 1,
                actionHovered(layout, index, mouseX, mouseY) ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(textRenderer, label, x + width / 2, y + 6, 0xffffff);
    }

    ButtonPress buttonAt(double mouseX, double mouseY, int width, int height) {
        Layout layout = layout(width, height);
        if (actionHovered(layout, 0, mouseX, mouseY)) return new ButtonPress(this, "hide", toggleHidden);
        if (layout.actionCount() == 3 && actionHovered(layout, 1, mouseX, mouseY)) {
            return new ButtonPress(this, "requirements", showRequirements);
        }
        if (actionHovered(layout, layout.actionCount() - 1, mouseX, mouseY)) return new ButtonPress(this, "close", close);
        if (mouseY >= layout.top && mouseY < layout.bottom) {
            for (int i = 0; i < details.size(); i++) {
                int rowY = layout.top + i * rowHeight() - (int) scroll;
                Detail detail = details.get(i);
                if (idHovered(detail, layout, rowY + 4 + textRenderer.fontHeight, mouseX, mouseY)) {
                    return new ButtonPress(this, i, () -> client.keyboard.setClipboard(detail.technical));
                }
            }
        }
        return null;
    }

    boolean click(double mouseX, double mouseY, int button, int width, int height) {
        Layout layout = layout(width, height);
        if (!inside(mouseX, mouseY, layout.x - 1, layout.y - 1, layout.width + 2, layout.height + 2)) {
            close.run();
            return true;
        }
        if (button == 0 && scrollbar(layout).contains(mouseX, mouseY)) {
            dragging = true;
            grabOffset = scrollbar(layout).grabOffset(mouseY, 0);
            drag(mouseY, width, height);
        }
        return false;
    }

    void scroll(double amount, int width, int height) {
        scroll = Scrollbar.scrollPixels(scroll, amount, rowHeight(), maxScroll(layout(width, height)));
    }

    void drag(double mouseY, int width, int height) {
        if (dragging) scroll = scrollbar(layout(width, height)).offsetForMouse(mouseY, grabOffset);
    }

    void release() { dragging = false; }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
