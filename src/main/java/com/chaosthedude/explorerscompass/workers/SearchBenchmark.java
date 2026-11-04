package com.chaosthedude.explorerscompass.workers;

import java.util.HashMap;
import java.util.Map;
import com.chaosthedude.explorerscompass.ExplorersCompass;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Temporary benchmark-only instrumentation. All access is on the server thread. */
final class SearchBenchmark {
    private static final Map<String, SearchBenchmark> ACTIVE = new HashMap<>();
    private final long started = System.nanoTime();
    private final BlockPos origin;
    private final Identifier search;
    private long candidates, checks, chunkRequests, checkNanos;
    private String worker = "none";
    private SearchBenchmark(BlockPos origin, Identifier search) { this.origin = origin; this.search = search; }
    static void begin(String id, BlockPos origin, Identifier search) {
        ACTIVE.computeIfAbsent(id, ignored -> new SearchBenchmark(origin, search));
    }
    static void candidate(String id) {
        SearchBenchmark b = ACTIVE.get(id); if (b != null) b.candidates++;
    }
    static long check(String id, String worker) {
        SearchBenchmark b = ACTIVE.get(id);
        if (b != null) { b.checks++; b.worker = worker; }
        return System.nanoTime();
    }
    static void checked(String id, long start) {
        SearchBenchmark b = ACTIVE.get(id); if (b != null) b.checkNanos += System.nanoTime() - start;
    }
    static void chunkRequest(String id) {
        SearchBenchmark b = ACTIVE.get(id); if (b != null) b.chunkRequests++;
    }
    static void finish(String id, String outcome, BlockPos result) {
        SearchBenchmark b = ACTIVE.remove(id);
        if (b == null) return;
        double distance = result == null ? -1 : Math.hypot((double)result.getX()-b.origin.getX(),
                (double)result.getZ()-b.origin.getZ());
        ExplorersCompass.LOGGER.info("COMPASS_BENCH id={} search={} worker={} outcome={} origin={},{} result={} distance={} elapsed_ms={} candidates={} checks={} chunk_requests={} check_ms={}",
                id, b.search, b.worker, outcome, b.origin.getX(), b.origin.getZ(),
                result == null ? "none" : result.getX()+","+result.getZ(), distance,
                (System.nanoTime()-b.started)/1_000_000.0, b.candidates, b.checks,
                b.chunkRequests, b.checkNanos/1_000_000.0);
    }
}
