package io.github.enriquemetner.explorerscompass.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.enriquemetner.explorerscompass.gui.CompassSettingsScreen;

public final class CompassModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CompassSettingsScreen::new;
    }
}
