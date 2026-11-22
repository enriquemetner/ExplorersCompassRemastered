package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

final class SearchFailureView {
    private record Line(OrderedText text, boolean centered, int indent, boolean bullet) {
        Line(OrderedText text, boolean centered) {
            this(text, centered, 0, false);
        }
    }

    private record NotFound(int radius, boolean excludeVisited, int selectedCount, Text unavailableCounts) {}

    private final TextRenderer textRenderer;
    private final Identifier excludedStructure;
    private final boolean excludedByWhitelist;
    private final NotFound notFound;
    private int cachedWrapWidth;
    private List<Line> cachedLines = List.of();

    private SearchFailureView(TextRenderer textRenderer, Identifier excludedStructure,
                              boolean excludedByWhitelist, NotFound notFound) {
        this.textRenderer = textRenderer;
        this.excludedStructure = excludedStructure;
        this.excludedByWhitelist = excludedByWhitelist;
        this.notFound = notFound;
    }

    static SearchFailureView invalidSearch(TextRenderer textRenderer) {
        return new SearchFailureView(textRenderer, null, false, null);
    }

    static SearchFailureView excluded(TextRenderer textRenderer, Identifier id, boolean whitelistActive) {
        return new SearchFailureView(textRenderer, id, whitelistActive, null);
    }

    static SearchFailureView notFound(TextRenderer textRenderer, int radius, boolean excludeVisited,
                                      int selectedCount, Text unavailableCounts) {
        return new SearchFailureView(textRenderer, null, false, new NotFound(radius, excludeVisited, selectedCount, unavailableCounts));
    }

    int dialogHeight(int screenHeight, int width) {
        return Math.min(screenHeight - 10, textOffset(screenHeight) + contentHeight(width) + 30);
    }

    private int textOffset(int screenHeight) {
        if (notFound == null) return 12 + textRenderer.fontHeight;
        return screenHeight >= 90 ? 32 : 6;
    }

    private int textHeight(int screenHeight, int dialogHeight) {
        return Math.max(0, dialogHeight - textOffset(screenHeight) - 30);
    }

    private int contentHeight(int width) {
        return lines(width).size() * textRenderer.fontHeight;
    }

    int maxScroll(int screenHeight, int width, int dialogHeight) {
        return Math.max(0, contentHeight(width) - textHeight(screenHeight, dialogHeight));
    }

    Scrollbar scrollbar(int x, int y, int width, int screenHeight, int dialogHeight, double scroll) {
        int top = y + textOffset(screenHeight);
        int viewport = textHeight(screenHeight, dialogHeight);
        int maxScroll = viewport > 0 ? maxScroll(screenHeight, width, dialogHeight) : 0;
        return new Scrollbar(x + width - 12 - Scrollbar.WIDTH, top, top + viewport,
                Scrollbar.pixelThumbHeight(viewport, maxScroll, 12, 0), scroll, maxScroll);
    }

    void render(DrawContext context, int x, int y, int w, int screenHeight, int dialogHeight, double scroll) {
        if (excludedStructure != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(excludedByWhitelist ? "Not On Whitelist" : "Blacklisted"), x + w / 2, y + 6, 0xffff5555);
        } else if (notFound == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Invalid Search"), x + w / 2, y + 6, 0xffffff);
        }

        int textTop = y + textOffset(screenHeight);
        int textHeight = textHeight(screenHeight, dialogHeight);
        if (textHeight > 0) {
            int lineY = textTop - (int) MathHelper.clamp(scroll, 0, maxScroll(screenHeight, w, dialogHeight));
            context.enableScissor(x + 8, textTop, x + w - 8, textTop + textHeight);
            for (Line line : lines(w)) {
                int lineX = line.centered() ? x + (w - textRenderer.getWidth(line.text())) / 2 : x + 12 + line.indent();
                if (line.bullet()) context.drawTextWithShadow(textRenderer, "•", x + 12, lineY, 0xffffff);
                context.drawTextWithShadow(textRenderer, line.text(), lineX, lineY, 0xffffff);
                lineY += textRenderer.fontHeight;
            }
            context.disableScissor();
            scrollbar(x, y, w, screenHeight, dialogHeight, scroll).render(context, 0xff242424, 0xff909090);
        }
    }

    private List<Line> lines(int width) {
        int wrapWidth = Math.max(1, width - 24 - Scrollbar.WIDTH);
        if (wrapWidth != cachedWrapWidth) {
            List<Line> lines = new ArrayList<>();
            if (excludedStructure != null) {
                addLines(lines, Text.literal(StructureUtils.getStructureName(excludedStructure)), wrapWidth, false);
                addLines(lines, Text.literal(excludedStructure.toString()).formatted(Formatting.GRAY), wrapWidth, false);
                lines.add(new Line(OrderedText.EMPTY, false));
                if (excludedByWhitelist) {
                    addLines(lines, Text.literal("This structure isn’t on the server’s whitelist, so it doesn’t appear in the compass list and can’t be searched."), wrapWidth, false);
                } else {
                    addLines(lines, Text.literal("The server has blacklisted this structure, so it doesn’t appear in the compass list and can’t be searched"), wrapWidth, false);
                    lines.add(new Line(OrderedText.EMPTY, false));
                    addLines(lines, Text.literal("Possible reasons:"), wrapWidth, false);
                    addBullet(lines, "The structure is disabled", wrapWidth);
                    addBullet(lines, "The server or modpack creator left it out to keep the list focused on useful destinations", wrapWidth);
                    addBullet(lines, "The creator intended players to find it another way", wrapWidth);
                    addBullet(lines, "The creator may have another reason for excluding it", wrapWidth);
                }
            } else if (notFound == null) {
                addLines(lines, Text.literal("None of your selected structures are valid for searching."), wrapWidth, false);
                lines.add(new Line(OrderedText.EMPTY, false));
                addLines(lines, Text.literal("Make sure at least one valid structure is selected by:"), wrapWidth, false);
                addLines(lines, Text.literal("• going to the correct dimension of the selected structures"), wrapWidth, false);
                addLines(lines, Text.literal("• selecting structures for the dimension you're currently in"), wrapWidth, false);
                addLines(lines, Text.literal("• selecting structures which aren't disabled"), wrapWidth, false);
            } else {
                addLines(lines, Text.literal("Couldn't locate structure within " + notFound.radius() + " blocks"), wrapWidth, true);
                lines.add(new Line(OrderedText.EMPTY, false));
                addLines(lines, Text.literal("Possible reasons:"), wrapWidth, false);
                if (notFound.excludeVisited()) {
                    addLines(lines, Text.literal("• ").append(Text.literal("Exclude Visited").formatted(Formatting.ITALIC))
                            .append(" is on, and every match within the search radius has already been visited"), wrapWidth, false);
                }
                addLines(lines, Text.literal("• Matching structures are very rare"), wrapWidth, false);
                addLines(lines, Text.literal("• The configured search radius may be too small to reach a match"), wrapWidth, false);
                addLines(lines, Text.literal("• Structure generation is disabled in a way the compass can't detect"), wrapWidth, false);
                if (notFound.selectedCount() > 0) {
                    lines.add(new Line(OrderedText.EMPTY, false));
                    addLines(lines, Text.literal(notFound.selectedCount() + " structures selected"), wrapWidth, true);
                    if (notFound.unavailableCounts() != null) {
                        addLines(lines, notFound.unavailableCounts(), wrapWidth, true);
                    }
                }
            }
            cachedLines = lines;
            cachedWrapWidth = wrapWidth;
        }
        return cachedLines;
    }

    private void addLines(List<Line> lines, Text text, int wrapWidth, boolean centered) {
        for (OrderedText line : textRenderer.wrapLines(text, wrapWidth)) lines.add(new Line(line, centered));
    }

    private void addBullet(List<Line> lines, String text, int wrapWidth) {
        int indent = textRenderer.getWidth("• ");
        var wrapped = textRenderer.wrapLines(Text.literal(text), Math.max(1, wrapWidth - indent));
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new Line(wrapped.get(i), false, indent, i == 0));
        }
    }
}
