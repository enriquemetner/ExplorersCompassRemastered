package com.chaosthedude.explorerscompass.workers;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkOriginsTest {
    @Test void originsAreRepeatableAndSearchCirclesDoNotOverlap() {
        for(int i=0;i<100;i++) {
            assertEquals(BenchmarkOrigins.at(i),BenchmarkOrigins.at(i));
            for(int j=0;j<i;j++) {
                BlockPos a=BenchmarkOrigins.at(i),b=BenchmarkOrigins.at(j);
                assertTrue(Math.hypot((double)a.getX()-b.getX(),(double)a.getZ()-b.getZ())>60000);
            }
        }
    }
    @Test void exclusionsUseStartChunkEvenWithUnusualLocateOffsetsAndNegativePositions() {
        BlockPos offset=new BlockPos(25,0,-17);
        BlockPos target=new BlockPos(-7*16+25,0,11*16-17);
        var chunk=BenchmarkOrigins.startChunk(target,offset);
        assertEquals(-7,chunk.x);assertEquals(11,chunk.z);
        assertEquals(121,BenchmarkOrigins.startChunk(new BlockPos(1945,0,-199),new BlockPos(9,0,9)).x);
        assertEquals(-13,BenchmarkOrigins.startChunk(new BlockPos(1945,0,-199),new BlockPos(9,0,9)).z);
    }
}
