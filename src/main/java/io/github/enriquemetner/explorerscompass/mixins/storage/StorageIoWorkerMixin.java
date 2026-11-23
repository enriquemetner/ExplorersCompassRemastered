package io.github.enriquemetner.explorerscompass.mixins.storage;

import io.github.enriquemetner.explorerscompass.storage.EmptyChunkCache;
import io.github.enriquemetner.explorerscompass.storage.EmptyChunkStorage;
import com.mojang.datafixers.util.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import io.github.enriquemetner.explorerscompass.storage.IndexedRegionFile;
import io.github.enriquemetner.explorerscompass.storage.RegionSlotMask;
import io.github.enriquemetner.explorerscompass.storage.SavedStructureIndex;
import io.github.enriquemetner.explorerscompass.storage.SavedStructureReader;
import net.minecraft.SharedConstants;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StorageIoWorker.class)
public abstract class StorageIoWorkerMixin implements EmptyChunkStorage {
    @Shadow @Final private RegionBasedStorage storage;
    // Generic type is intentionally opaque: pending keys are sufficient, including deletion requests.
    @Shadow @Final private Map<ChunkPos, ?> results;
    @Shadow private <T> CompletableFuture<T> run(Supplier<Either<T, Exception>> task) { throw new AssertionError(); }
    @Unique private final EmptyChunkCache.WriteState explorerscompass$writes = new EmptyChunkCache.WriteState();

    @Unique private volatile SavedStructureIndex explorerscompass$index;
    @Unique private Runnable explorerscompass$persistIndex;

    @Override
    public void explorerscompass$attachIndex(SavedStructureIndex index, Runnable persist) {
        if (((Object) this).getClass() != StorageIoWorker.class) return;
        explorerscompass$persistIndex = persist;
        explorerscompass$index = index;
    }

    @Override
    public void explorerscompass$saveIndex() {
        if (explorerscompass$persistIndex != null && explorerscompass$index.isDirty()) {
            explorerscompass$persistIndex.run();
        }
    }

    @Redirect(method = "write", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/storage/RegionBasedStorage;write(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void explorerscompass$indexWrite(RegionBasedStorage target, ChunkPos pos, NbtCompound nbt) throws IOException {
        explorerscompass$writes.beginWrite();
        try {
            explorerscompass$writeAndIndex(target, pos, nbt);
        } finally {
            explorerscompass$writes.endWrite();
        }
    }

    @Unique
    private void explorerscompass$writeAndIndex(RegionBasedStorage target, ChunkPos pos, NbtCompound nbt)
            throws IOException {
        var index = explorerscompass$index;
        var access = (RegionBasedStorageAccessor) (Object) target;
        Path path = explorerscompass$regionPath(access, pos);
        String before = index == null ? null : SavedStructureIndex.tryRevision(path);
        try {
            access.explorerscompass$write(pos, nbt);
        } catch (IOException | RuntimeException e) {
            if (index != null) index.written(pos, before, null, null);
            throw e;
        }
        if (index == null) return;
        var starts = nbt != null && nbt.getInt("DataVersion") == SharedConstants.getGameVersion().getSaveVersion().getId()
                ? SavedStructureReader.readStarts(nbt.getCompound("structures").getCompound("starts"), pos) : null;
        index.written(pos, before, SavedStructureIndex.tryRevision(path), starts);
        var file = access.explorerscompass$getCachedRegionFiles().get(ChunkPos.toLong(pos.getRegionX(), pos.getRegionZ()));
        if (file instanceof IndexedRegionFile indexed) indexed.explorerscompass$trackPadding(index, pos);
    }

    @Unique
    private static Path explorerscompass$regionPath(RegionBasedStorageAccessor storage, ChunkPos pos) {
        return storage.explorerscompass$getDirectory().resolve(
                "r." + pos.getRegionX() + "." + pos.getRegionZ() + RegionBasedStorage.MCA_EXTENSION);
    }

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
                        long[] pending = RegionSlotMask.pending(pos, results.keySet());
                        return Either.left(explorerscompass$snapshotRegion(pos, pending));
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

    @Override
    public long explorerscompass$writeStamp() { return explorerscompass$writes.stamp(); }

    @Override
    public RegionSnapshot explorerscompass$savedRegion(ChunkPos pos) {
        if (((Object) this).getClass() != StorageIoWorker.class) return null;
        long stamp = explorerscompass$writes.stamp();
        return this.<RegionSnapshot>run(() -> {
            try {
                var access = (RegionBasedStorageAccessor) (Object) storage;
                String revision = SavedStructureIndex.revision(explorerscompass$regionPath(access, pos));
                long[] pending = RegionSlotMask.pending(pos, results.keySet());
                return Either.left(new RegionSnapshot(explorerscompass$snapshotRegion(pos, pending), pending, revision, stamp));
            } catch (IOException e) {
                return Either.right(e);
            }
        }).join();
    }

    @Unique
    private long[] explorerscompass$snapshotRegion(ChunkPos pos, long[] pending) throws IOException {
        var regionStorage = (RegionBasedStorageAccessor) (Object) storage;
        long key = ChunkPos.toLong(pos.getRegionX(), pos.getRegionZ());
        // Preserve the normal lookup for open files and paths whose absence we cannot confirm.
        if (!regionStorage.explorerscompass$getCachedRegionFiles().containsKey(key)
                && Files.notExists(explorerscompass$regionPath(regionStorage, pos), LinkOption.NOFOLLOW_LINKS)) {
            return pending.clone();
        }

        RegionFile file = regionStorage.explorerscompass$getRegionFile(pos);
        return RegionSlotMask.withDiskOccupancy(pos, pending, file::hasChunk);
    }
}
