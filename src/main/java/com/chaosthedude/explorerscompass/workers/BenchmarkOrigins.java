package com.chaosthedude.explorerscompass.workers;

import net.minecraft.util.math.BlockPos;

final class BenchmarkOrigins {
    private BenchmarkOrigins() {}
    static net.minecraft.util.math.ChunkPos startChunk(BlockPos locatePos, BlockPos locateOffset) {
        return new net.minecraft.util.math.ChunkPos(Math.floorDiv(locatePos.getX()-locateOffset.getX(),16),
                Math.floorDiv(locatePos.getZ()-locateOffset.getZ(),16));
    }
    static BlockPos at(int index) {
        if(index==0)return new BlockPos(0,64,0);
        // Fixed 80k lattice; search circles up to radius30k do not overlap.
        int row=(index-1)/10, column=(index-1)%10;
        return new BlockPos((column-5)*80000,64,(row+1)*80000);
    }
}
