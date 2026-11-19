package io.github.enriquemetner.explorerscompass.storage;

import net.minecraft.util.math.ChunkPos;

/** Implemented only by the vanilla storage worker adapter. Unknown backends use normal scans. */
public interface EmptyChunkStorage {
    record RegionSnapshot(long[] occupied, long[] pending, String revision, long writeStamp) {
        public boolean hasPendingWrite(int slot) {
            return (pending[slot >>> 6] & (1L << (slot & 63))) != 0;
        }
    }

    default void explorerscompass$attachIndex(SavedStructureIndex index, Runnable persist) {}

    default void explorerscompass$saveIndex() {}

    default long explorerscompass$writeStamp() { return -1; }

    default RegionSnapshot explorerscompass$savedRegion(ChunkPos pos) { return null; }

    boolean explorerscompass$isKnownEmpty(EmptyChunkCache cache, ChunkPos pos);
}
