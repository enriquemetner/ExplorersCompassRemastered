package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class CompassTextures {
    private static final Identifier CLASSIC_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/classic_ftb_reward_compass.png");

    private CompassTextures() {}

    public static Identifier stillIcon() {
        return ClientPacketHandlers.useClassicCompass ? CLASSIC_ICON : ExplorersCompass.STILL_COMPASS_ICON;
    }

    public static Identifier frame(int index) {
        String directory = ClientPacketHandlers.useClassicCompass ? "textures/item/classic/" : "textures/item/";
        return new Identifier(ExplorersCompass.MODID, directory + String.format(Locale.ROOT, "explorerscompass_%02d.png", index));
    }
}
