package com.chaosthedude.explorerscompass.workers;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

/** A lazy best-first traversal. Region bounds and exact candidates share one queue. */
final class NearestFirstQueue<T> {
    record Candidate<T>(long x, long z, T value) {}
    private record Entry<T>(long distance, long order, Candidate<T> candidate, Runnable expand) {}
    private final long originX, originZ;
    private long sequence;
    private final PriorityQueue<Entry<T>> pending = new PriorityQueue<>(
            Comparator.<Entry<T>>comparingLong(Entry::distance).thenComparingLong(Entry::order));

    NearestFirstQueue(long x, long z) { originX = x; originZ = z; }

    void addCandidate(Candidate<T> candidate) {
        pending.add(new Entry<>(squared(candidate.x - originX, candidate.z - originZ),
                sequence++, candidate, null));
    }

    void addArea(long x, long z, int spanX, int spanZ, Runnable expand) {
        long bound = squared(gap(originX, x, x + spanX), gap(originZ, z, z + spanZ));
        pending.add(new Entry<>(bound, sequence++, null, expand));
    }

    /** span is the inclusive maximum candidate offset, in blocks, within a cell. */
    void addGrid(int step, int span, int offsetX, int offsetZ,
                 BiFunction<Integer, Integer, Candidate<T>> calculate) {
        if (step <= 0 || span < 0 || span >= step) throw new IllegalArgumentException("Invalid grid");
        new Grid(step, span, span, offsetX, offsetZ, (x, z) -> {
            Candidate<T> candidate = calculate.apply(x, z);
            if (candidate != null) addCandidate(candidate);
        }).seed();
    }

    void addOverlappingGrid(int step, int spanX, int spanZ, int offsetX, int offsetZ,
                            BiConsumer<Integer, Integer> expand) {
        new Grid(step, spanX, spanZ, offsetX, offsetZ, expand).seed();
    }

    boolean isEmpty() { return pending.isEmpty(); }
    long nextDistanceSquared() { return pending.isEmpty() ? Long.MAX_VALUE : pending.peek().distance; }

    /** One bounded preparation step or one safe-to-check candidate. Never scans a whole ring. */
    Candidate<T> advance() {
        Entry<T> entry = pending.remove();
        if (entry.expand != null) entry.expand.run();
        return entry.candidate;
    }

    private static long squared(long x, long z) { return x * x + z * z; }
    private static long gap(long value, long min, long max) {
        return value < min ? min - value : Math.max(0, value - max);
    }

    private final class Grid {
        final int step, spanX, spanZ, offsetX, offsetZ, seedX, seedZ;
        final BiConsumer<Integer, Integer> expand;
        Grid(int step, int spanX, int spanZ, int offsetX, int offsetZ,
             BiConsumer<Integer, Integer> expand) {
            this.step = step; this.spanX = spanX;
            this.spanZ = spanZ; this.offsetX = offsetX; this.offsetZ = offsetZ;
            this.expand = expand;
            seedX = closestCell(originX, offsetX, spanX);
            seedZ = closestCell(originZ, offsetZ, spanZ);
        }
        int closestCell(long origin, int offset, int span) {
            int cell = (int) Math.floorDiv(origin - offset, step);
            long min = (long) cell * step + offset;
            return gap(origin, min, min + span) <= gap(origin, min + step, min + step + span)
                    ? cell : cell + 1;
        }
        void seed() { add(seedX, seedZ); }
        void add(int x, int z) {
            long minX = (long) x * step + offsetX, minZ = (long) z * step + offsetZ;
            long bound = squared(gap(originX, minX, minX + spanX), gap(originZ, minZ, minZ + spanZ));
            pending.add(new Entry<>(bound, sequence++, null, () -> {
                expand.accept(x, z);
                // Unique parent per cell: grow columns along the seed row, then each column outward.
                // Bounds never decrease along these paths, so the frontier covers all unseen cells.
                if (z == seedZ) {
                    if (x <= seedX) add(x - 1, z);
                    if (x >= seedX) add(x + 1, z);
                }
                if (z <= seedZ) add(x, z - 1);
                if (z >= seedZ) add(x, z + 1);
            }));
        }
    }
}
