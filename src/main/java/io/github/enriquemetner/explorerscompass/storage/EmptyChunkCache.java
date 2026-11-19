package io.github.enriquemetner.explorerscompass.storage;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Search-owned occupancy snapshots. A clear bit skips only the saved-data scan. */
public final class EmptyChunkCache {
    public static final int MAX_REGIONS = 512;
    private final LinkedHashMap<Long, long[]> regions = new LinkedHashMap<>(16, 0.75f, true);
    private WriteState owner;
    private long version = -1;

    /** Shared by one storage worker, never contains world/chunk data. */
    public static final class WriteState {
        private long version;
        private int submitting;
        private boolean closed;

        public synchronized void beginWrite() { submitting++; version++; }
        public synchronized void endWrite() { submitting--; version++; }
        public synchronized void close() { closed = true; version++; }
        public synchronized long stamp() { return closed || submitting != 0 ? -1 : version; }
    }

    public boolean isKnownEmpty(WriteState state, int chunkX, int chunkZ, Supplier<long[]> snapshot) {
        long stamp = state.stamp();
        if (stamp < 0) return false;
        if (owner != state || version != stamp) {
            clear();
            owner = state;
            version = stamp;
        }
        long key = ((long) (chunkX >> 5) & 0xffffffffL) | ((long) (chunkZ >> 5) << 32);
        long[] occupied = regions.get(key);
        if (occupied == null) {
            occupied = snapshot.get();
            // A queued save can precede its appearance in the storage thread's pending map.
            // Reject snapshots spanning any submission, even if it has already finished.
            if (occupied == null || occupied.length != 16 || state.stamp() != stamp) return false;
            regions.put(key, occupied.clone());
            if (regions.size() > MAX_REGIONS) regions.remove(regions.keySet().iterator().next());
        }
        int index = (chunkX & 31) + ((chunkZ & 31) << 5);
        return (occupied[index >>> 6] & (1L << (index & 63))) == 0 && state.stamp() == stamp;
    }

    public void clear() { regions.clear(); owner = null; version = -1; }
}
