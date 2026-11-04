package com.chaosthedude.explorerscompass.workers;

import net.minecraft.util.math.BlockPos;

final class BenchmarkOrigins {
    private BenchmarkOrigins() {}
    static BlockPos at(int index) {
        if(index==0)return new BlockPos(0,64,0);
        // Fixed 80k lattice; search circles up to radius30k do not overlap.
        int row=(index-1)/10, column=(index-1)%10;
        return new BlockPos((column-5)*80000,64,(row+1)*80000);
    }
}
