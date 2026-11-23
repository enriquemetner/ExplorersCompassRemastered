package io.github.enriquemetner.explorerscompass.storage;

import java.util.function.Predicate;
import net.minecraft.util.math.ChunkPos;

/** A region's 32 by 32 chunk slots, with X changing fastest. */
public final class RegionSlotMask {
    private static final int REGION_SIZE = 32;
    private static final int SLOT_COUNT = REGION_SIZE * REGION_SIZE;
    static final int WORD_COUNT = SLOT_COUNT / Long.SIZE;

    private RegionSlotMask() {}

    static int slot(int chunkX, int chunkZ) {
        return (chunkX & (REGION_SIZE - 1)) + (chunkZ & (REGION_SIZE - 1)) * REGION_SIZE;
    }

    static boolean contains(long[] mask, int slot) {
        return (mask[slot >>> 6] & (1L << (slot & 63))) != 0;
    }

    private static void mark(long[] mask, int slot) {
        mask[slot >>> 6] |= 1L << (slot & 63);
    }

    public static long[] pending(ChunkPos pos, Iterable<ChunkPos> pendingWrites) {
        int regionX = pos.getRegionX();
        int regionZ = pos.getRegionZ();
        long[] mask = new long[WORD_COUNT];
        for (ChunkPos chunk : pendingWrites) {
            if (chunk.getRegionX() == regionX && chunk.getRegionZ() == regionZ) {
                mark(mask, slot(chunk.x, chunk.z));
            }
        }
        return mask;
    }

    public static long[] withDiskOccupancy(ChunkPos pos, long[] pending, Predicate<ChunkPos> onDisk) {
        long[] occupied = pending.clone();
        int baseX = pos.getRegionX() * REGION_SIZE;
        int baseZ = pos.getRegionZ() * REGION_SIZE;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ChunkPos chunk = new ChunkPos(baseX + slot % REGION_SIZE, baseZ + slot / REGION_SIZE);
            if (onDisk.test(chunk)) mark(occupied, slot);
        }
        return occupied;
    }
}
