package io.github.enriquemetner.explorerscompass.mixins.search;

import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("frequency") float explorerscompass$getFrequency();
}
