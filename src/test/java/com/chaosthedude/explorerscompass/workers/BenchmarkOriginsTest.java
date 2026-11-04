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
}
