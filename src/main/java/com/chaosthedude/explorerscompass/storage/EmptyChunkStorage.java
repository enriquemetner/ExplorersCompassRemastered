package com.chaosthedude.explorerscompass.storage;

import net.minecraft.util.math.ChunkPos;

/** Implemented only by the vanilla storage worker adapter. Unknown backends use normal scans. */
public interface EmptyChunkStorage {
    boolean explorerscompass$isKnownEmpty(EmptyChunkCache cache, ChunkPos pos);
}
