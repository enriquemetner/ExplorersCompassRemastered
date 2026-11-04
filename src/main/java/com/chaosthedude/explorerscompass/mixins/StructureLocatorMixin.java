package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.workers.SearchPlacementFilter;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureLocator;
import net.minecraft.world.StructurePresence;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureLocator.class)
public abstract class StructureLocatorMixin {
    // Keep vanilla's cache/disk answers (including its fallback for old/unreadable chunks).
    // Do not cache a placement rejection as a structure-wide generation failure: another
    // placement for the same structure may permit this chunk.
    @Inject(method = "getStructurePresence(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/gen/structure/Structure;Z)Lnet/minecraft/world/StructurePresence;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/StructureLocator;generationPossibilityByStructure:Ljava/util/Map;"),
            cancellable = true, require = 1, allow = 1)
    private void explorerscompass$filterPlacement(ChunkPos pos, Structure structure, boolean skipReferencedStructures,
            CallbackInfoReturnable<StructurePresence> cir) {
        if (SearchPlacementFilter.rejects(pos.toLong(), structure)) {
            cir.setReturnValue(StructurePresence.START_NOT_PRESENT);
        }
    }
}
