package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.storage.EmptyChunkCache;
import com.chaosthedude.explorerscompass.storage.EmptyChunkStorage;
import com.mojang.datafixers.util.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StorageIoWorker.class)
public abstract class StorageIoWorkerMixin implements EmptyChunkStorage {
    @Shadow @Final private RegionBasedStorage storage;
    // Generic type is intentionally opaque: pending keys are sufficient, including deletion requests.
    @Shadow @Final private Map<ChunkPos, ?> results;
    @Shadow private <T> CompletableFuture<T> run(Supplier<Either<T, Exception>> task) { throw new AssertionError(); }
    @Unique private final EmptyChunkCache.WriteState explorerscompass$writes = new EmptyChunkCache.WriteState();

    @Inject(method = "setResult", at = @At("HEAD"))
    private void explorerscompass$beginWrite(ChunkPos pos, NbtCompound nbt, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        explorerscompass$writes.beginWrite();
    }

    @Inject(method = "setResult", at = @At("RETURN"))
    private void explorerscompass$endWrite(ChunkPos pos, NbtCompound nbt, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        explorerscompass$writes.endWrite();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void explorerscompass$close(CallbackInfo ci) { explorerscompass$writes.close(); }

    @Override
    public boolean explorerscompass$isKnownEmpty(EmptyChunkCache cache, ChunkPos pos) {
        // A subclass may provide a different scan backend; do not assume its data lives in these headers.
        if (((Object) this).getClass() != StorageIoWorker.class) return false;
        return cache.isKnownEmpty(explorerscompass$writes, pos.x, pos.z, () -> {
            try {
                return this.<long[]>run(() -> {
                    try {
                        // Both header and pending-save map are read exclusively on Minecraft's IO executor.
                        return Either.left(explorerscompass$snapshotRegion(pos));
                    } catch (Exception exception) {
                        return Either.right(exception);
                    }
                }).join();
            } catch (CompletionException exception) {
                // Preserve vanilla's error handling and chunk-load fallback rather than treating failure as empty.
                return null;
            }
        });
    }

    @Unique
    private long[] explorerscompass$snapshotRegion(ChunkPos pos) throws IOException {
        var regionStorage = (RegionBasedStorageAccessor) (Object) storage;
        int regionX = pos.getRegionX();
        int regionZ = pos.getRegionZ();
        long[] occupied = new long[16];
        // Preserve the normal lookup for open files and paths whose absence we cannot confirm.
        if (!regionStorage.explorerscompass$getCachedRegionFiles().containsKey(ChunkPos.toLong(regionX, regionZ))
                && Files.notExists(regionStorage.explorerscompass$getDirectory().resolve(
                        "r." + regionX + "." + regionZ + RegionBasedStorage.MCA_EXTENSION), LinkOption.NOFOLLOW_LINKS)) {
            for (ChunkPos pending : results.keySet()) {
                if (pending.getRegionX() == regionX && pending.getRegionZ() == regionZ) {
                    int index = pending.getRegionRelativeX() + (pending.getRegionRelativeZ() << 5);
                    occupied[index >>> 6] |= 1L << (index & 63);
                }
            }
            return occupied;
        }

        RegionFile file = regionStorage.explorerscompass$getRegionFile(pos);
        int baseX = pos.x & ~31;
        int baseZ = pos.z & ~31;
        for (int index = 0; index < 1024; index++) {
            ChunkPos chunk = new ChunkPos(baseX + (index & 31), baseZ + (index >> 5));
            if (file.hasChunk(chunk) || results.containsKey(chunk)) {
                occupied[index >>> 6] |= 1L << (index & 63);
            }
        }
        return occupied;
    }
}
