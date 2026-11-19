package io.github.enriquemetner.explorerscompass.mixins;

import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DrawContext.class)
public interface DrawContextAccessor {
    @Invoker("drawTooltip")
    void explorersCompass$drawTooltip(TextRenderer font, List<TooltipComponent> rows, int x, int y,
                                      TooltipPositioner positioner);
}
