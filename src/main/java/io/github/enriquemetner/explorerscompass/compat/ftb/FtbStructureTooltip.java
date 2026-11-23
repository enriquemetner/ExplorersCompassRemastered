package io.github.enriquemetner.explorerscompass.compat.ftb;

import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Displays every resolved structure, cycling through names when the tooltip cannot fit them. */
final class FtbStructureTooltip {
    private FtbStructureTooltip() {}

    static void addNames(TooltipList tooltip, List<Identifier> structures) {
        BaseScreen screen = ClientUtils.getCurrentGuiAs(BaseScreen.class);
        int height = screen == null ? MinecraftClient.getInstance().getWindow().getScaledHeight() : screen.height;
        int fontHeight = screen == null ? 9 : screen.getTheme().getFontHeight();
        int rows = Math.max(1, (height - 20) / (fontHeight + 2) - tooltip.getLines().size());
        long step = System.currentTimeMillis() / 500L;
        for (Identifier id : visibleNames(structures, rows, step)) {
            tooltip.add(Text.literal(StructureUtils.getStructureName(id)).formatted(Formatting.AQUA));
        }
    }

    static List<Identifier> visibleNames(List<Identifier> structures, int rows, long step) {
        if (structures.isEmpty() || rows <= 0) return List.of();
        if (structures.size() <= rows) return structures;
        int start = (int) Math.floorMod(step, structures.size());
        List<Identifier> visible = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) visible.add(structures.get((start + i) % structures.size()));
        return visible;
    }
}
