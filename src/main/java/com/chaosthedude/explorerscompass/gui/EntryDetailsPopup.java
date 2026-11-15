package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** A modal view of one entry, independent of the list's technical-name setting. */
final class EntryDetailsPopup {
    private static final int COPY_HEIGHT = StructureSearchList.ACTION_BUTTON_HEIGHT;
    private final int copyWidth;
    private static final int ACTION_WIDTH = 70;
    static final String HIDE_TOOLTIP = "Removes the structure from the list on this client, across all worlds and servers. You can undo this in Settings.";
    private final MinecraftClient client;
    private final TextRenderer textRenderer;
    private final List<Detail> details = new ArrayList<>();
    private final BooleanSupplier hidden;
    private final Runnable toggleHidden;
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

    private record Layout(int x, int y, int width, int height, int top, int bottom, int copyWidth) {
        int copyX() { return x + width - copyWidth - 22; }
        int actionsY() { return y + height - 30; }
        int hideX() { return x + (width - ACTION_WIDTH * 2 - 8) / 2; }
        int backX() { return hideX() + ACTION_WIDTH + 8; }
    }

    EntryDetailsPopup(MinecraftClient client, Identifier id, BooleanSupplier hidden, Runnable toggleHidden, Runnable close) {
        this.client = client;
        this.textRenderer = client.textRenderer;
        this.copyWidth = textRenderer.getWidth("Copy") + 8;
        this.hidden = hidden;
        this.toggleHidden = toggleHidden;
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
        return new Layout(x, y, width, height, y + 28, y + height - 36, copyWidth);
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, contentHeight() - (layout.bottom - layout.top));
    }

    private int rowHeight() {
        return textRenderer.fontHeight * 2 + 14;
    }

    private int contentHeight() {
        // Stop at the last text or button edge, excluding the gap after the last group.
        int lastRowBottom = Math.max(4 + textRenderer.fontHeight * 2,
                textRenderer.fontHeight + COPY_HEIGHT);
        return (details.size() - 1) * rowHeight() + lastRowBottom;
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
        context.drawCenteredTextWithShadow(textRenderer, "Details", width / 2, layout.y + 10, 0xffffff);
        String hoveredText = null;
        context.enableScissor(layout.x + 8, layout.top, layout.x + layout.width - 18, layout.bottom);
        for (int i = 0; i < details.size(); i++) {
            Detail detail = details.get(i);
            int rowY = layout.top + i * rowHeight() - (int) scroll;
            int textX = layout.x + 12;
            int technicalY = rowY + 4 + textRenderer.fontHeight;
            int technicalWidth = layout.copyX() - textX - 8;
            int readableWidth = technicalWidth;
            drawLine(context, detail.readableLine(), textX, rowY + 4, readableWidth, 0xffffff);
            drawLine(context, detail.technicalLine(), textX, technicalY, technicalWidth, 0xaaaaaa);
            drawButton(context, "Copy", layout.copyX(), technicalY - 4, copyWidth, COPY_HEIGHT, mouseX, mouseY, detail.canCopy());
            if (mouseY >= layout.top && mouseY < layout.bottom) {
                if (textHovered(detail.readableLine(), textX, rowY + 4, readableWidth, mouseX, mouseY)) hoveredText = detail.readableLine();
                if (textHovered(detail.technicalLine(), textX, technicalY, technicalWidth, mouseX, mouseY)) hoveredText = detail.technicalLine();
            }
        }
        context.disableScissor();
        scrollbar(layout).render(context, 0xff242424, 0xff909090);
        drawButton(context, hidden.getAsBoolean() ? "Undo" : "Hide", layout.hideX(), layout.actionsY(), ACTION_WIDTH, 20, mouseX, mouseY, true);
        drawButton(context, "Back", layout.backX(), layout.actionsY(), ACTION_WIDTH, 20, mouseX, mouseY, true);
        if (!hidden.getAsBoolean() && inside(mouseX, mouseY, layout.hideX(), layout.actionsY(), ACTION_WIDTH, 20)) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(HIDE_TOOLTIP), Math.min(220, width - 30)), mouseX, mouseY);
        } else if (hoveredText != null) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(hoveredText), Math.min(300, width - 30)), mouseX, mouseY);
        }
        context.getMatrices().pop();
    }

    private void drawLine(DrawContext context, String value, int x, int y, int width, int color) {
        String visible = textRenderer.getWidth(value) > width
                ? textRenderer.trimToWidth(value, Math.max(0, width - textRenderer.getWidth("..."))) + "..." : value;
        context.drawTextWithShadow(textRenderer, visible, x, y, color);
    }

    private boolean textHovered(String text, int x, int y, int width, int mouseX, int mouseY) {
        return textRenderer.getWidth(text) > width && inside(mouseX, mouseY, x, y, width, textRenderer.fontHeight);
    }

    private void drawButton(DrawContext context, String label, int x, int y, int width, int height,
                            int mouseX, int mouseY, boolean enabled) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        boolean available = enabled;
        context.fill(x, y, x + width, y + height, available ? 0xff909090 : 0xff505050);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                available && hovered ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(textRenderer, label, x + width / 2, y + (height == COPY_HEIGHT ? 4 : 6),
                available ? 0xffffff : 0x808080);
    }

    ButtonPress buttonAt(double mouseX, double mouseY, int width, int height) {
        Layout layout = layout(width, height);
        if (inside(mouseX, mouseY, layout.hideX(), layout.actionsY(), ACTION_WIDTH, 20)) {
            return new ButtonPress(this, "hide", toggleHidden);
        }
        if (inside(mouseX, mouseY, layout.backX(), layout.actionsY(), ACTION_WIDTH, 20)) {
            return new ButtonPress(this, "back", close);
        }
        if (mouseY >= layout.top && mouseY < layout.bottom) {
            for (int i = 0; i < details.size(); i++) {
                int rowY = layout.top + i * rowHeight() - (int) scroll;
                Detail detail = details.get(i);
                if (detail.canCopy() && inside(mouseX, mouseY, layout.copyX(), rowY + textRenderer.fontHeight, copyWidth, COPY_HEIGHT)) {
                    return new ButtonPress(this, i, () -> client.keyboard.setClipboard(detail.technical));
                }
            }
        }
        return null;
    }

    void click(double mouseX, double mouseY, int button, int width, int height) {
        Layout layout = layout(width, height);
        if (!inside(mouseX, mouseY, layout.x - 1, layout.y - 1, layout.width + 2, layout.height + 2)) {
            close.run();
            return;
        }
        if (button == 0 && scrollbar(layout).contains(mouseX, mouseY)) {
            dragging = true;
            grabOffset = scrollbar(layout).grabOffset(mouseY, 0);
            drag(mouseY, width, height);
        }
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
