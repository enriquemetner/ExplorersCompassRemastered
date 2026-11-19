package io.github.enriquemetner.explorerscompass.mixins;

import io.github.enriquemetner.explorerscompass.storage.EmptyChunkStorage;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThreadedAnvilChunkStorage.class)
abstract class ChunkSaveIndexMixin {

    @Inject(method = "close", at = @At("RETURN"))
    private void explorerscompass$saveFinalIndex(CallbackInfo ci) {
        // The last chunk writes and region padding occur during storage shutdown.
        var storage = (ThreadedAnvilChunkStorage) (Object) this;
        if (storage.getWorker() instanceof EmptyChunkStorage backend) backend.explorerscompass$saveIndex();
    }
}
