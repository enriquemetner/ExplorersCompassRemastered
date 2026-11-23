package io.github.enriquemetner.explorerscompass.client;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class CompassTextures {
    private static final Identifier COMPASS_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/explorers_compass_icon.png");
    private static final Identifier CLASSIC_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/explorers_compass_icon_classic.png");

    private CompassTextures() {}

    public static Identifier stillIcon() {
        return ClientPacketHandlers.useClassicCompass ? CLASSIC_ICON : COMPASS_ICON;
    }

    public static void drawStillIcon(DrawContext context, int x, int y, int size) {
        context.drawTexture(stillIcon(), x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    public static Identifier frame(int index) {
        String directory = ClientPacketHandlers.useClassicCompass ? "textures/item/classic/" : "textures/item/";
        return new Identifier(ExplorersCompass.MODID, directory + String.format(Locale.ROOT, "explorerscompass_%02d.png", index));
    }
}
