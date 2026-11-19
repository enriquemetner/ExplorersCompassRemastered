package io.github.enriquemetner.explorerscompass.workers;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Cooperative, server-thread-only scheduling; this does not parallelize structure checks. */
final class SearchTaskQueue {
    private static final long SLICE_NANOS = 5_000_000L;
    private static final int MAX_STEPS_PER_SLICE = 1024;

    private final Deque<WorldWorkerManager.IWorker> workers = new ArrayDeque<>();
    private final Set<WorldWorkerManager.IWorker> enqueued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<Runnable> enqueue;
    private final Runnable validateOwnership;
    private final LongSupplier nanoTime;
    private boolean scheduled;
    private boolean closed;

    /** enqueue must defer execution, including when called from inside a slice. */
    SearchTaskQueue(Consumer<Runnable> enqueue, Runnable validateOwnership, LongSupplier nanoTime) {
        this.enqueue = enqueue;
        this.validateOwnership = validateOwnership;
        this.nanoTime = nanoTime;
    }

    void add(WorldWorkerManager.IWorker worker) {
        if (closed || !enqueued.add(worker)) return;
        workers.addLast(worker);
        schedule();
    }

    void close() {
        closed = true;
        workers.clear();
        enqueued.clear();
        // An already submitted server task cannot be withdrawn; it will see closed and do nothing.
    }

    private void schedule() {
        if (closed || scheduled || workers.isEmpty()) return;
        scheduled = true;
        enqueue.accept(this::runSlice);
    }

    private void resume(WorldWorkerManager.IWorker worker) {
        if (closed) return;
        if (worker.hasWork()) {
            workers.addLast(worker);
            schedule();
        } else {
            enqueued.remove(worker);
        }
    }

    private void runSlice() {
        if (closed) return;
        try {
            validateOwnership.run();
            long start = nanoTime.getAsLong();
            for (int steps = 0; !closed && !workers.isEmpty() && steps < MAX_STEPS_PER_SLICE; steps++) {
                if (nanoTime.getAsLong() - start >= SLICE_NANOS) break;
                WorldWorkerManager.IWorker worker = workers.removeFirst();
                if (!worker.hasWork()) {
                    enqueued.remove(worker);
                    continue;
                }
                boolean again;
                try {
                    again = worker.doWork();
                } catch (RuntimeException exception) {
                    enqueued.remove(worker);
                    worker.onSearchError(exception);
                    continue;
                }
                if (!closed && worker.hasWork()) {
                    var pending = worker.pendingWork();
                    if (pending == null) workers.addLast(worker);
                    else pending.whenComplete((result, error) -> enqueue.accept(() -> resume(worker)));
                } else {
                    enqueued.remove(worker);
                }
                // Other searches get a turn too. A worker may explicitly yield the rest of this slice.
                if (!again) break;
            }
        } finally {
            scheduled = false;
            schedule();
        }
    }
}
