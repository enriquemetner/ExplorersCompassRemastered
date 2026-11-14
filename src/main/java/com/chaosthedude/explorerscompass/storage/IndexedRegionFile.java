package com.chaosthedude.explorerscompass.storage;

import net.minecraft.util.math.ChunkPos;

public interface IndexedRegionFile {
    void explorerscompass$trackPadding(SavedStructureIndex index, ChunkPos pos);
}
