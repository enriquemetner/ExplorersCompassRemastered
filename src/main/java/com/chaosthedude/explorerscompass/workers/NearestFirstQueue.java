package com.chaosthedude.explorerscompass.workers;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.BiFunction;

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

    /** span is the inclusive maximum candidate offset, in blocks, within a cell. */
    void addGrid(int step, int span, int offsetX, int offsetZ,
                 BiFunction<Integer, Integer, Candidate<T>> calculate) {
        if (step <= 0 || span < 0 || span >= step) throw new IllegalArgumentException("Invalid grid");
        new Grid(step, span, offsetX, offsetZ, calculate).seed();
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
        final int step, span, offsetX, offsetZ, seedX, seedZ;
        final BiFunction<Integer, Integer, Candidate<T>> calculate;
        Grid(int step, int span, int offsetX, int offsetZ,
             BiFunction<Integer, Integer, Candidate<T>> calculate) {
            this.step = step; this.span = span; this.offsetX = offsetX; this.offsetZ = offsetZ;
            this.calculate = calculate;
            seedX = closestCell(originX, offsetX);
            seedZ = closestCell(originZ, offsetZ);
        }
        int closestCell(long origin, int offset) {
            int cell = (int) Math.floorDiv(origin - offset, step);
            long min = (long) cell * step + offset;
            return gap(origin, min, min + span) <= gap(origin, min + step, min + step + span)
                    ? cell : cell + 1;
        }
        void seed() { add(seedX, seedZ); }
        void add(int x, int z) {
            long minX = (long) x * step + offsetX, minZ = (long) z * step + offsetZ;
            long bound = squared(gap(originX, minX, minX + span), gap(originZ, minZ, minZ + span));
            pending.add(new Entry<>(bound, sequence++, null, () -> {
                Candidate<T> candidate = calculate.apply(x, z);
                if (candidate != null) addCandidate(candidate);
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
