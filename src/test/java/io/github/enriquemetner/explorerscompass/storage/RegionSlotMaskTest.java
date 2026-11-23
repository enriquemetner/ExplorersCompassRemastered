package io.github.enriquemetner.explorerscompass.storage;

import java.util.List;
import java.util.Set;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSlotMaskTest {
    @Test void mergesDiskAndPendingSlotsWithoutChangingThePendingMask() {
        var diskOnly = new ChunkPos(-33, -32);
        var pendingOnly = new ChunkPos(-64, -31);
        var both = new ChunkPos(-33, -1);
        var adjacentRegion = new ChunkPos(-32, -32);
        var onDisk = Set.of(diskOnly, both, adjacentRegion);
        long[] pending = RegionSlotMask.pending(both, List.of(pendingOnly, both, adjacentRegion));
        long[] occupied = RegionSlotMask.withDiskOccupancy(both, pending, onDisk::contains);
        var snapshot = new EmptyChunkStorage.RegionSnapshot(occupied, pending, "revision", 0);

        assertTrue(RegionSlotMask.contains(occupied, 31)); // Disk only
        assertTrue(RegionSlotMask.contains(occupied, 32)); // Pending only
        assertTrue(RegionSlotMask.contains(occupied, 1023)); // Both
        assertFalse(RegionSlotMask.contains(occupied, 0)); // Absent, but present in an adjacent region
        assertFalse(snapshot.hasPendingWrite(31));
        assertTrue(snapshot.hasPendingWrite(32));
        assertTrue(snapshot.hasPendingWrite(1023));
        assertFalse(snapshot.hasPendingWrite(0));

        assertNotSame(pending, occupied);
        occupied[0] = 0;
        assertTrue(snapshot.hasPendingWrite(32));
    }
}
