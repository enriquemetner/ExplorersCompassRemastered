package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.client.CompassTextures;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.gui.widgets.CostRendering;

import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public final class SearchStatusDialog {
    public enum Kind { SEARCHING, WRONG_DIMENSION, INSUFFICIENT_COST, MULTI_COST, REJECTED }

    private static final String WRONG_DIMENSION_MESSAGE = "You're In The Wrong Dimension";
    private final TextRenderer textRenderer;
    private final Kind kind;
    private final int cost;
    private final int balance;
    private final long openedAt = System.currentTimeMillis();
    private final Supplier<List<String>> progress;
    private final LongSupplier progressStarted;

    private SearchStatusDialog(TextRenderer font, Kind kind, int cost, int balance,
                               Supplier<List<String>> progress, LongSupplier progressStarted) {
        this.textRenderer = font;
        this.kind = kind;
        this.cost = cost;
        this.balance = balance;
        this.progress = progress;
        this.progressStarted = progressStarted;
    }

    public static SearchStatusDialog searching(TextRenderer font, Supplier<List<String>> lines, LongSupplier started) {
        return new SearchStatusDialog(font, Kind.SEARCHING, 0, 0, lines, started);
    }

    public static SearchStatusDialog notice(TextRenderer font, Kind kind, int cost, int balance) {
        return new SearchStatusDialog(font, kind, cost, balance, null, null);
    }

    public boolean expired(long now) {
        return timed(kind) && now - openedAt >= 3000;
    }

    private static boolean timed(Kind kind) {
        return kind == Kind.WRONG_DIMENSION || kind == Kind.INSUFFICIENT_COST;
    }

    private int width(int screenWidth) {
        if (kind == Kind.WRONG_DIMENSION) {
            return Math.min(screenWidth - 20, textRenderer.getWidth(WRONG_DIMENSION_MESSAGE) + 12);
        }
        if (kind == Kind.INSUFFICIENT_COST || kind == Kind.MULTI_COST) {
            int content = Math.max(textRenderer.getWidth(costTitle()), CostRendering.width(textRenderer, costLabel(), cost, costSuffix(cost)));
            content = Math.max(content, CostRendering.width(textRenderer, balanceLabel(), balance, costSuffix(balance)));
            if (kind == Kind.MULTI_COST) content = Math.max(content, 205);
            return Math.min(screenWidth - 20, content + 16);
        }
        return kind == Kind.SEARCHING ? FoundStructureDialog.dialogWidth(textRenderer, screenWidth) : Math.min(screenWidth - 20, 245);
    }

    private DialogFrame.Bounds bounds(int screenWidth, int screenHeight) {
        int width = width(screenWidth);
        int height = switch (kind) {
            case WRONG_DIMENSION -> textRenderer.fontHeight + 12;
            case INSUFFICIENT_COST -> 3 * textRenderer.fontHeight + 16;
            case MULTI_COST -> (costExplanation(width).size() + 4) * textRenderer.fontHeight + 46;
            case SEARCHING -> FoundStructureDialog.dialogHeight(screenHeight, FoundStructureDialog.rowsHeight(4, textRenderer.fontHeight));
            case REJECTED -> 80;
        };
        return DialogFrame.centered(screenWidth, screenHeight, width, height);
    }

    public boolean dismissOnClick(int width, int height, double mouseX, double mouseY) {
        return timed(kind) || bounds(width, height).outside(mouseX, mouseY);
    }

    private DialogButton backButton(DialogFrame.Bounds b, Runnable dismiss) {
        int y = switch (kind) {
            case SEARCHING -> b.y() + b.height() - 25;
            case MULTI_COST -> b.y() + b.height() - 26;
            case REJECTED -> b.y() + 57;
            default -> Integer.MIN_VALUE;
        };
        return y == Integer.MIN_VALUE ? null : new DialogButton(b.x() + (b.width() - 70) / 2, y, 70, "Back", dismiss);
    }

    public Runnable actionAt(int width, int height, double mouseX, double mouseY, Runnable dismiss) {
        DialogButton button = backButton(bounds(width, height), dismiss);
        return button != null && button.contains(mouseX, mouseY) ? button.action() : null;
    }

    public void render(DrawContext context, int width, int height, int mouseX, int mouseY, Runnable dismiss) {
        var b = bounds(width, height);
        int x = b.x(), y = b.y(), w = b.width(), center = x + w / 2;
        DialogFrame.begin(context, width, height, 300, x, y, w, b.height());
        DialogButton back = backButton(b, dismiss);
        if (kind == Kind.SEARCHING) {
            int frame = (int)((System.currentTimeMillis() - progressStarted.getAsLong()) / 31L % 32L);
            context.drawTexture(CompassTextures.frame(frame), x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Locating..."), center, y + 32, 0xffffff);
            var lines = progress.get();
            int lineSpacing = 12;
            int textTop = y + 32 + textRenderer.fontHeight;
            int textHeight = (lines.size() - 1) * lineSpacing + textRenderer.fontHeight;
            int lineY = textTop + (back.y() - textTop - textHeight) / 2;
            for (String line : lines) {
                context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(line, w - 12), center, lineY, 0xffffff);
                lineY += lineSpacing;
            }
        } else if (kind == Kind.INSUFFICIENT_COST || kind == Kind.MULTI_COST) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(costTitle()), center, y + 6, 0xffff4444);
            int lineY = y + 10 + textRenderer.fontHeight;
            if (kind == Kind.MULTI_COST) {
                for (var line : costExplanation(w)) {
                    context.drawCenteredTextWithShadow(textRenderer, line, center, lineY, 0xffffff);
                    lineY += textRenderer.fontHeight;
                }
                lineY += textRenderer.fontHeight;
                back.render(context, textRenderer, mouseX, mouseY);
                back = null;
            }
            CostRendering.draw(context, textRenderer, center, lineY, costLabel(), cost, costSuffix(cost));
            CostRendering.draw(context, textRenderer, center, lineY + textRenderer.fontHeight, balanceLabel(), balance, costSuffix(balance));
        } else {
            boolean wrongDimension = kind == Kind.WRONG_DIMENSION;
            String message = wrongDimension ? WRONG_DIMENSION_MESSAGE : "Search could not start";
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(message, w - 12), center,
                    wrongDimension ? y + (b.height() - textRenderer.fontHeight) / 2 : y + 14, wrongDimension ? 0xffff4444 : 0xffffff);
        }
        if (back != null) back.render(context, textRenderer, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private boolean usesEnergy() {
        return SearchCosts.remote().type() == SearchCosts.Type.ITEMS;
    }

    private String costTitle() {
        return usesEnergy() ? "Not Enough Energy" : "Not Enough Experience";
    }

    private String costLabel() {
        String label = switch (SearchCosts.remote().type()) {
            case ITEMS -> "Energy cost: ";
            case XP -> "XP cost: ";
            case XP_LEVELS -> "Level cost: ";
        };
        return kind == Kind.MULTI_COST ? "Highest " + (SearchCosts.remote().type() == SearchCosts.Type.XP
                ? "XP cost: " : label.toLowerCase(Locale.ROOT)) : label;
    }

    private String balanceLabel() {
        return switch (SearchCosts.remote().type()) {
            case ITEMS -> "Current energy: ";
            case XP -> "Current XP: ";
            case XP_LEVELS -> "Current XP Levels: ";
        };
    }

    private String costSuffix(int amount) {
        return SearchCosts.remote().type() == SearchCosts.Type.XP
                ? SearchCosts.equivalentLevels(amount) + " Levels" : "";
    }

    private List<OrderedText> costExplanation(int dialogWidth) {
        return textRenderer.wrapLines(Text.literal("Your selection includes structures for which you don't have enough " + (usesEnergy() ? "energy." : "experience.")),
                Math.max(1, dialogWidth - 16));
    }
}
