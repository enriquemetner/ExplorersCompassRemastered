package com.chaosthedude.explorerscompass.workers;

import java.util.function.BooleanSupplier;
import com.chaosthedude.explorerscompass.storage.EmptyChunkCache;
import java.util.function.Supplier;

/** Applies a placement predicate only to the matching compass presence lookup on this thread. */
public final class SearchPlacementFilter {
    private static final ThreadLocal<Lookup> ACTIVE = new ThreadLocal<>();

    private SearchPlacementFilter() {}

    public static <T> T lookup(long chunk, Object structure, BooleanSupplier permitted, Supplier<T> lookup) {
        return lookup(chunk, structure, permitted, null, lookup);
    }

    public static <T> T lookup(long chunk, Object structure, BooleanSupplier permitted, EmptyChunkCache cache, Supplier<T> lookup) {
        Lookup previous = ACTIVE.get();
        ACTIVE.set(new Lookup(chunk, structure, permitted, cache));
        try {
            return lookup.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static EmptyChunkCache storageCache(long chunk) {
        Lookup lookup = ACTIVE.get();
        return lookup != null && lookup.chunk == chunk && !lookup.evaluating ? lookup.cache : null;
    }

    /** Called after Minecraft has tried cached and saved records, before generation feasibility. */
    public static boolean rejects(long chunk, Object structure) {
        Lookup lookup = ACTIVE.get();
        if (lookup == null || lookup.chunk != chunk || lookup.structure != structure || lookup.evaluating) return false;
        // A mod's predicate may perform another lookup. Do not recursively invoke that same predicate.
        lookup.evaluating = true;
        try {
            return !lookup.permitted.getAsBoolean();
        } finally {
            lookup.evaluating = false;
        }
    }

    private static final class Lookup {
        final long chunk;
        final Object structure;
        final BooleanSupplier permitted;
        final EmptyChunkCache cache;
        boolean evaluating;
        Lookup(long chunk, Object structure, BooleanSupplier permitted, EmptyChunkCache cache) {
            this.chunk = chunk;
            this.structure = structure;
            this.permitted = permitted;
            this.cache = cache;
        }
    }
}
