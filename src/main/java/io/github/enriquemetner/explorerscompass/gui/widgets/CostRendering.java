package io.github.enriquemetner.explorerscompass.gui.widgets;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class CostRendering {
    private CostRendering() {}

    public static int width(TextRenderer textRenderer, String label, int amount, String suffix) {
        return textRenderer.getWidth(label) + textRenderer.getWidth(Integer.toString(amount))
                + 8 + textRenderer.getWidth(suffix);
    }

    public static void draw(DrawContext context, TextRenderer textRenderer, int centerX, int y, String label, int amount, String suffix) {
        int x = centerX - width(textRenderer, label, amount, suffix) / 2;
        context.drawTextWithShadow(textRenderer, label, x, y, 0xffffff);
        x += textRenderer.getWidth(label);
        String number = Integer.toString(amount);
        drawExperienceNumber(context, textRenderer, number, x, y);
        x += textRenderer.getWidth(number) + 3;
        drawEnergyOrb(context, textRenderer, x, y);
        if (!suffix.isEmpty()) context.drawTextWithShadow(textRenderer, suffix, x + 5, y, 0xffffff);
    }

    public static void drawExperienceNumber(DrawContext context, TextRenderer font, String number, int x, int y) {
        context.drawText(font, number, x - 1, y, 0x000000, false);
        context.drawText(font, number, x + 1, y, 0x000000, false);
        context.drawText(font, number, x, y - 1, 0x000000, false);
        context.drawText(font, number, x, y + 1, 0x000000, false);
        context.drawText(font, number, x, y, 0x80ff20, false);
    }

    public static void drawEnergyOrb(DrawContext context, TextRenderer font, int x, int y) {
        Identifier texture = new Identifier(ExplorersCompass.MODID, "textures/gui/experience_orb.png");
        context.drawTexture(texture, x, y + (font.fontHeight - 6) / 2, 5, 5, 0, 0, 16, 16, 16, 16);
    }
}
