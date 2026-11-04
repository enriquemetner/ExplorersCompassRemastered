package com.chaosthedude.explorerscompass.workers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import net.fabricmc.loader.api.FabricLoader;
import java.util.HashMap;
import java.util.Map;
import com.chaosthedude.explorerscompass.ExplorersCompass;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Temporary benchmark-only instrumentation. All access is on the server thread. */
final class SearchBenchmark {
    private static final String VARIANT = readVariant();
    private static final Path RESULTS = FabricLoader.getInstance().getGameDir()
            .resolve("logs/explorerscompass").resolve(VARIANT + ".csv");
    private static String readVariant() {
        try (var stream = SearchBenchmark.class.getResourceAsStream("/search-benchmark-variant.txt")) {
            if (stream == null) throw new IllegalStateException("Missing benchmark variant");
            String variant = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!variant.equals("baseline") && !variant.equals("nearest-first"))
                throw new IllegalStateException("Invalid benchmark variant");
            return variant;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static final Map<String, SearchBenchmark> ACTIVE = new HashMap<>();
    private final long started = System.nanoTime();
    private final BlockPos origin;
    private final Identifier search;
    private long candidates, checks, chunkRequests, checkNanos;
    private String worker = "none";
    private SearchBenchmark(BlockPos origin, Identifier search) { this.origin = origin; this.search = search; }
    static void begin(String id, BlockPos origin, Identifier search) {
        ACTIVE.computeIfAbsent(id, ignored -> {
            ExplorersCompass.LOGGER.info("Compass benchmark ({}) results: {}", VARIANT, RESULTS.toAbsolutePath());
            return new SearchBenchmark(origin, search);
        });
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
        double elapsedMs = (System.nanoTime()-b.started)/1_000_000.0;
        double checkMs = b.checkNanos/1_000_000.0;
        try {
            BenchmarkResults.append(RESULTS, Instant.now(), VARIANT, id, b.search, b.worker, outcome,
                    b.origin.getX(), b.origin.getZ(), result == null ? null : result.getX(),
                    result == null ? null : result.getZ(), distance, elapsedMs, b.candidates,
                    b.checks, b.chunkRequests, checkMs);
        } catch (IOException e) {
            ExplorersCompass.LOGGER.error("Could not write compass benchmark results to " + RESULTS, e);
        }
        ExplorersCompass.LOGGER.info("COMPASS_BENCH id={} search={} worker={} outcome={} origin={},{} result={} distance={} elapsed_ms={} candidates={} checks={} chunk_requests={} check_ms={}",
                id, b.search, b.worker, outcome, b.origin.getX(), b.origin.getZ(),
                result == null ? "none" : result.getX()+","+result.getZ(), distance,
                elapsedMs, b.candidates, b.checks,
                b.chunkRequests, checkMs);
    }
}
