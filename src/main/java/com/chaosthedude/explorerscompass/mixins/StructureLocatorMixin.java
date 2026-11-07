package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.workers.SearchPlacementFilter;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureLocator;
import net.minecraft.world.StructurePresence;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.chaosthedude.explorerscompass.storage.EmptyChunkStorage;
import net.minecraft.world.storage.NbtScannable;
import net.minecraft.nbt.scanner.NbtScanner;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureLocator.class)
public abstract class StructureLocatorMixin {
    @Redirect(method = "getStructurePresence(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/gen/structure/Structure;ZJ)Lnet/minecraft/world/StructurePresence;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/NbtScannable;scanChunk(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/scanner/NbtScanner;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1, allow = 1)
    private CompletableFuture<Void> explorerscompass$scanSavedChunk(NbtScannable storage, ChunkPos pos, NbtScanner scanner) {
        var cache = SearchPlacementFilter.storageCache(pos.toLong());
        if (cache != null && storage instanceof EmptyChunkStorage knownStorage
                && knownStorage.explorerscompass$isKnownEmpty(cache, pos)) {
            // Leave the collector empty, exactly like a successful scan of an empty slot.
            // The caller continues through placement and generation checks normally.
            return CompletableFuture.completedFuture(null);
        }
        return storage.scanChunk(pos, scanner);
    }

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
