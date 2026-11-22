package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.util.RenderUtils;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

final class HiddenStructuresList {
    static final String TITLE = "Hidden Structures";
    private static final int ROW_HEIGHT = 25;
    private static final int TEXT_X = 16;
    private static final String REMOVE_LABEL = "Remove";
    private String query = "";
    private int scroll;

    String query() { return query; }

    void setQuery(String query) {
        this.query = query;
        scroll = 0;
    }

    void setScroll(double offset) { scroll = (int) Math.round(offset); }

    void scroll(double amount, int top, int bottom) {
        scroll = Scrollbar.scrollRows(scroll, amount, 2, maxScroll(matchingIds(), top, bottom));
    }

    Scrollbar scrollbar(int width, int top, int bottom) {
        return scrollbar(width, top, bottom, matchingIds());
    }

    private Scrollbar scrollbar(int width, int top, int bottom, List<String> ids) {
        return new Scrollbar(width - Scrollbar.WIDTH, top, bottom,
                Scrollbar.rowThumbHeight(bottom - top, visibleRows(top, bottom), ids.size()),
                scroll, maxScroll(ids, top, bottom));
    }

    private int visibleRows(int top, int bottom) {
        return Math.max(1, (bottom - top - 12) / ROW_HEIGHT);
    }

    private int maxScroll(List<String> ids, int top, int bottom) {
        return Math.max(0, ids.size() - visibleRows(top, bottom));
    }

    private List<String> matchingIds() {
        if (query.isEmpty()) return ExplorersCompassConfig.hiddenStructures;
        String term = query.toLowerCase(Locale.ROOT);
        return ExplorersCompassConfig.hiddenStructures.stream()
                .filter(raw -> raw.toLowerCase(Locale.ROOT).contains(term)
                        || name(raw).toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }

    private String name(String raw) {
        return StructureUtils.getStructureName(Identifier.tryParse(raw));
    }

    private int removeX(int width, int buttonWidth) {
        return width - Scrollbar.WIDTH - StructureSearchList.ACTION_BUTTON_RIGHT_INSET - buttonWidth;
    }

    private int removeY(int rowY) {
        return rowY + (20 - StructureSearchList.ACTION_BUTTON_HEIGHT) / 2;
    }

    ButtonPress removeButtonAt(double x, double y, TextRenderer font, int width, int top, int bottom, Runnable onRemove) {
        int firstY = top + 6;
        if (y < firstY || y >= bottom) return null;
        int row = (int) (y - firstY) / ROW_HEIGHT;
        int buttonWidth = font.getWidth(REMOVE_LABEL) + StructureSearchList.ACTION_BUTTON_HORIZONTAL_PADDING;
        int buttonX = removeX(width, buttonWidth);
        if (row >= visibleRows(top, bottom) || !ButtonPress.contains(x, y, buttonX, removeY(firstY + row * ROW_HEIGHT),
                buttonWidth, StructureSearchList.ACTION_BUTTON_HEIGHT)) return null;
        List<String> ids = matchingIds();
        int index = scroll + row;
        if (index >= ids.size()) return null;
        String id = ids.get(index);
        return new ButtonPress(this, id, () -> {
            ExplorersCompassConfig.hiddenStructures.remove(id);
            ExplorersCompassConfig.saveHiddenStructures();
            scroll = Math.min(scroll, maxScroll(matchingIds(), top, bottom));
            onRemove.run();
        });
    }

    void render(DrawContext context, TextRenderer font, int width, int top, int bottom, int mouseX, int mouseY) {
        int right = width - Scrollbar.WIDTH;
        context.fill(Scrollbar.WIDTH, top, right, bottom,
                RenderUtils.blackWithOpacity(RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT));
        List<String> ids = matchingIds();
        scroll = MathHelper.clamp(scroll, 0, maxScroll(ids, top, bottom));
        context.enableScissor(Scrollbar.WIDTH, top, right, bottom);
        if (ids.isEmpty()) {
            context.drawCenteredTextWithShadow(font, ExplorersCompassConfig.hiddenStructures.isEmpty()
                    ? "No hidden structures" : "No matching hidden structures", width / 2, top + 20, 0xffffff);
        }
        int buttonWidth = font.getWidth(REMOVE_LABEL) + StructureSearchList.ACTION_BUTTON_HORIZONTAL_PADDING;
        int buttonX = removeX(width, buttonWidth);
        int buttonHeight = StructureSearchList.ACTION_BUTTON_HEIGHT;
        int textWidth = Math.max(1, buttonX - TEXT_X - 5);
        for (int row = 0; row < visibleRows(top, bottom) && scroll + row < ids.size(); row++) {
            int y = top + 6 + row * ROW_HEIGHT;
            String raw = ids.get(scroll + row);
            String name = name(raw);
            String id = font.trimToWidth(raw, textWidth);
            if (name.isEmpty()) {
                context.drawTextWithShadow(font, id, TEXT_X, y + (20 - font.fontHeight) / 2, 0xffffff);
            } else {
                int nameY = y + (20 - 2 * font.fontHeight) / 2;
                context.drawTextWithShadow(font, font.trimToWidth(name, textWidth), TEXT_X, nameY, 0xffffff);
                context.drawTextWithShadow(font, id, TEXT_X, nameY + font.fontHeight, 0xaaaaaa);
            }
            int buttonY = removeY(y);
            boolean hovered = mouseY < bottom && ButtonPress.contains(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
            StructureSearchEntry.drawActionButton(context, font, REMOVE_LABEL, buttonX, buttonY, buttonWidth, buttonHeight, hovered);
        }
        context.disableScissor();
        scrollbar(width, top, bottom, ids).render(context);
    }
}
