package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.client.CompassTextures;
import io.github.enriquemetner.explorerscompass.gui.widgets.Scrollbar;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class SearchFailureDialog {
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
    private long needleUpdated = System.nanoTime();
    private double needleAngle;
    private double needleSpeed;
    private double scroll;
    private int cachedWrapWidth;
    private List<Line> cachedLines = List.of();

    private SearchFailureDialog(TextRenderer textRenderer, Identifier excludedStructure,
                              boolean excludedByWhitelist, NotFound notFound) {
        this.textRenderer = textRenderer;
        this.excludedStructure = excludedStructure;
        this.excludedByWhitelist = excludedByWhitelist;
        this.notFound = notFound;
    }

    public static SearchFailureDialog invalidSearch(TextRenderer textRenderer) {
        return new SearchFailureDialog(textRenderer, null, false, null);
    }

    public static SearchFailureDialog excluded(TextRenderer textRenderer, Identifier id, boolean whitelistActive) {
        return new SearchFailureDialog(textRenderer, id, whitelistActive, null);
    }

    public static SearchFailureDialog notFound(TextRenderer textRenderer, int radius, boolean excludeVisited,
                                      int selectedCount, Text unavailableCounts) {
        return new SearchFailureDialog(textRenderer, null, false, new NotFound(radius, excludeVisited, selectedCount, unavailableCounts));
    }

    private int dialogHeight(int screenHeight, int width) {
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

    private int maxScroll(int screenHeight, int width, int dialogHeight) {
        return Math.max(0, contentHeight(width) - textHeight(screenHeight, dialogHeight));
    }

    private Scrollbar scrollbar(int x, int y, int width, int screenHeight, int dialogHeight, double scroll) {
        int top = y + textOffset(screenHeight);
        int viewport = textHeight(screenHeight, dialogHeight);
        int maxScroll = viewport > 0 ? maxScroll(screenHeight, width, dialogHeight) : 0;
        return new Scrollbar(x + width - 12 - Scrollbar.WIDTH, top, top + viewport,
                Scrollbar.pixelThumbHeight(viewport, maxScroll, 12, 0), scroll, maxScroll);
    }

    private int dialogWidth(int screenWidth) {
        return Math.min(screenWidth - 20, 340);
    }

    public void scroll(int screenWidth, int screenHeight, double amount) {
        int width = dialogWidth(screenWidth);
        scroll = Scrollbar.scrollPixels(scroll, amount, textRenderer.fontHeight,
                maxScroll(screenHeight, width, dialogHeight(screenHeight, width)));
    }

    public Scrollbar scrollbar(int screenWidth, int screenHeight) {
        int width = dialogWidth(screenWidth), height = dialogHeight(screenHeight, width);
        return scrollbar((screenWidth - width) / 2, (screenHeight - height) / 2, width, screenHeight, height, scroll);
    }

    public void setScroll(double offset) {
        scroll = offset;
    }

    private List<DialogButton> buttons(int x, int y, int w, int h, Runnable back,
                                       Runnable hide, BooleanSupplier hidden) {
        int gap = 5;
        int available = w - 24;
        int width = Math.max(1, Math.min(70, hide == null ? available : (available - gap) / 2));
        int buttonY = y + h - 25;
        if (hide == null) return List.of(new DialogButton(x + (w - width) / 2, buttonY, width, "Back", back));
        int first = x + (w - 2 * width - gap) / 2;
        return List.of(new DialogButton(first, buttonY, width, hidden.getAsBoolean() ? "Undo" : "Hide", hide),
                new DialogButton(first + width + gap, buttonY, width, "Back", back));
    }

    public boolean outside(int screenWidth, int screenHeight, double mouseX, double mouseY) {
        int w = dialogWidth(screenWidth), h = dialogHeight(screenHeight, w);
        return DialogFrame.centered(screenWidth, screenHeight, w, h).outside(mouseX, mouseY);
    }

    public Runnable actionAt(int screenWidth, int screenHeight, double mouseX, double mouseY,
                             Runnable back, Runnable hide, BooleanSupplier hidden) {
        int w = dialogWidth(screenWidth), h = dialogHeight(screenHeight, w);
        return DialogButton.actionAt(buttons((screenWidth - w) / 2, (screenHeight - h) / 2, w, h, back, hide, hidden),
                mouseX, mouseY);
    }

    public void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY,
                       Runnable back, Runnable hide, BooleanSupplier hidden) {
        int w = dialogWidth(screenWidth), h = dialogHeight(screenHeight, w);
        int x = (screenWidth - w) / 2, y = (screenHeight - h) / 2;
        DialogFrame.begin(context, screenWidth, screenHeight, 300, x, y, w, h);
        if (notFound != null && screenHeight >= 90) {
            long now = System.nanoTime();
            long elapsed = now - needleUpdated;
            int ticks = (int)Math.min(5L, elapsed / 50_000_000L);
            if (ticks > 0) {
                needleUpdated = now - elapsed % 50_000_000L;
            }
            for (int tick = 0; tick < ticks; tick++) {
                double delta = MathHelper.floorMod(Math.random() - needleAngle + 0.5D, 1.0D) - 0.5D;
                needleSpeed = (needleSpeed + delta * 0.1D) * 0.8D;
                needleAngle = MathHelper.floorMod(needleAngle + needleSpeed, 1.0D);
            }
            int frame = (16 + (int)Math.floor(needleAngle * 32.0D + 0.5D)) % 32;
            Identifier texture = CompassTextures.frame(frame);
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
        }

        renderContent(context, x, y, w, screenHeight, h, scroll);
        var buttons = buttons(x, y, w, h, back, hide, hidden);
        for (int i = buttons.size() - 1; i >= 0; i--) {
            buttons.get(i).render(context, textRenderer, mouseX, mouseY);
        }
        if (hide != null && !hidden.getAsBoolean() && buttons.get(0).contains(mouseX, mouseY)) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(EntryDetailsDialog.HIDE_TOOLTIP),
                    Math.max(1, Math.min(220, screenWidth - 30))), mouseX, mouseY);
        }
        context.getMatrices().pop();
    }

    private void renderContent(DrawContext context, int x, int y, int w, int screenHeight, int dialogHeight, double scroll) {
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
