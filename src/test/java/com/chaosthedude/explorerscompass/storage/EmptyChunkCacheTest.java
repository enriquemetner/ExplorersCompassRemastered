package com.chaosthedude.explorerscompass.storage;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EmptyChunkCacheTest {
    @Test void reusesRegionForAdjacentChunksButNeverSkipsOccupiedSlots() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        var reads = new AtomicInteger();
        long[] bits = new long[16];
        bits[0] = 1L << 1;
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> { reads.incrementAndGet(); return bits; }));
        assertFalse(cache.isKnownEmpty(state, 1, 0, () -> { fail("Must reuse region"); return null; }));
        assertTrue(cache.isKnownEmpty(state, 31, 31, () -> { fail("Must reuse region"); return null; }));
        assertEquals(1, reads.get());
    }

    @Test void negativeCoordinatesAndRegionEdgesUseCorrectBits() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        long[] bits = new long[16];
        bits[15] = Long.MIN_VALUE; // (-1, -1) is slot 1023 in region (-1, -1).
        assertFalse(cache.isKnownEmpty(state, -1, -1, () -> bits));
        assertTrue(cache.isKnownEmpty(state, -32, -32, () -> null));
        assertFalse(cache.isKnownEmpty(state, -33, -32, () -> null)); // unknown adjacent region
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
    }

    @Test void saveSubmissionInvalidatesExistingSnapshotsBeforeSaveIsQueued() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
        state.beginWrite();
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> { fail("No snapshot while submitting"); return null; }));
        state.endWrite();
        long[] bits = new long[16]; bits[0] = 1;
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> bits));
    }

    @Test void snapshotSpanningSaveIsDiscardedEvenWhenSubmissionAlreadyEnded() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> {
            state.beginWrite(); state.endWrite(); return new long[16];
        }));
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> null));
    }

    @Test void overlappingWriteSubmissionsDisableSnapshotUntilAllReturn() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        state.beginWrite(); state.beginWrite(); state.endWrite();
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
        state.endWrite();
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
    }

    @Test void unknownAndFailedReadsAreNeverCachedAsEmpty() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> null));
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> new long[0]));
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
    }

    @Test void dimensionsAndStorageWorkersNeverShareSnapshots() {
        var cache = new EmptyChunkCache();
        assertTrue(cache.isKnownEmpty(new EmptyChunkCache.WriteState(), 0, 0, () -> new long[16]));
        assertFalse(cache.isKnownEmpty(new EmptyChunkCache.WriteState(), 0, 0, () -> null));
    }

    @Test void boundedCacheEvictsLeastRecentlyUsedRegion() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        for (int i = 0; i < EmptyChunkCache.MAX_REGIONS; i++)
            assertTrue(cache.isKnownEmpty(state, i * 32, 0, () -> new long[16]));
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> null));
        assertTrue(cache.isKnownEmpty(state, EmptyChunkCache.MAX_REGIONS * 32, 0, () -> new long[16]));
        assertFalse(cache.isKnownEmpty(state, 32, 0, () -> null));
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> null));
    }

    @Test void clearAndClosePreventReuse() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
        cache.clear();
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> null));
        assertTrue(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
        state.close();
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> new long[16]));
    }

    @Test void snapshotIsCopiedSoProducerCannotChangeCachedData() {
        var cache = new EmptyChunkCache();
        var state = new EmptyChunkCache.WriteState();
        long[] bits = new long[16]; bits[0] = 1;
        assertTrue(cache.isKnownEmpty(state, 1, 0, () -> bits));
        bits[0] = 0;
        assertFalse(cache.isKnownEmpty(state, 0, 0, () -> null));
    }
}
