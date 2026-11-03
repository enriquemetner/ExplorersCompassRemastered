package com.chaosthedude.explorerscompass.workers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchTaskQueueTest {
    private final Deque<Runnable> serverTasks = new ArrayDeque<>();
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger validations = new AtomicInteger();
    private final SearchTaskQueue queue = new SearchTaskQueue(serverTasks::addLast,
            validations::incrementAndGet, clock::get);

    @Test void finishesInOrderUsingOnlyQueuedTasksWithoutWorldTicks() {
        List<Integer> candidates = new ArrayList<>();
        Worker worker = new Worker(12, () -> {
            candidates.add(candidates.size());
            clock.addAndGet(1_000_000);
        });
        queue.add(worker);
        assertEquals(0, worker.calls, "Adding work must not execute it inline");
        serverTasks.removeFirst().run();
        assertEquals(5, worker.calls);
        assertEquals(1, serverTasks.size(), "Only one continuation should be pending");
        drain(); // No tick callback or tick counter is advanced.
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), candidates);
        assertEquals(3, validations.get());
        assertTrue(serverTasks.isEmpty(), "An idle search must stop scheduling tasks");
    }

    @Test void yieldsToCancellationBetweenSlicesAndSkipsStaleWork() {
        Worker worker = new Worker(20, () -> clock.addAndGet(1_000_000));
        queue.add(worker);
        serverTasks.addLast(() -> worker.cancelled = true);
        serverTasks.removeFirst().run();
        assertEquals(5, worker.calls);
        drain();
        assertEquals(5, worker.calls, "Queued cancellation must run before another search slice");
    }

    @Test void ownershipLossIsCheckedBeforeAnyStructureWork() {
        Worker worker = new Worker(1, () -> fail("A detached search must not check structures"));
        SearchTaskQueue detached = new SearchTaskQueue(serverTasks::addLast,
                () -> worker.cancelled = true, clock::get);
        detached.add(worker);
        drain();
        assertEquals(0, worker.calls);
    }

    @Test void handoffAndContinuationCanAddWorkersWithoutInlineExecutionOrDuplicates() {
        Worker next = new Worker(2, () -> {});
        Worker first = new Worker(1, () -> {
            queue.add(next);
            queue.add(next);
            assertEquals(0, next.calls);
            assertTrue(serverTasks.isEmpty(), "Adding during a slice must not enqueue a second slice");
        });
        queue.add(first);
        queue.add(first);
        drain();
        assertEquals(1, first.calls);
        assertEquals(2, next.calls);

        // A retained placement worker can become eligible again after "Keep searching".
        next.remaining = 2;
        queue.add(next);
        drain();
        assertEquals(4, next.calls);
    }

    @Test void searchesShareSlicesWithoutChangingTheirOwnCandidateOrder() {
        List<String> order = new ArrayList<>();
        queue.add(new Worker(3, () -> order.add("A")));
        queue.add(new Worker(3, () -> order.add("B")));
        drain();
        assertEquals(List.of("A", "B", "A", "B", "A", "B"), order);
    }

    @Test void aSlowCheckYieldsImmediatelyAfterItCompletes() {
        Worker slow = new Worker(2, () -> clock.addAndGet(100_000_000));
        Worker other = new Worker(1, () -> {});
        queue.add(slow);
        queue.add(other);
        serverTasks.removeFirst().run();
        assertEquals(1, slow.calls);
        assertEquals(0, other.calls);
        drain();
        assertEquals(2, slow.calls);
        assertEquals(1, other.calls);
    }

    @Test void extremelyCheapWorkStillYieldsAndExplicitYieldIsHonored() {
        Worker cheap = new Worker(100_000, () -> {});
        queue.add(cheap);
        serverTasks.removeFirst().run();
        assertTrue(cheap.calls > 0 && cheap.calls < 100_000);
        cheap.cancelled = true;
        drain();

        Worker yielding = new Worker(2, () -> {});
        yielding.again = false;
        queue.add(yielding);
        serverTasks.removeFirst().run();
        assertEquals(1, yielding.calls);
        drain();
        assertEquals(2, yielding.calls);
    }

    @Test void shutdownInvalidatesOldCallbacksAndCannotAffectANewServerQueue() {
        Worker old = new Worker(1, () -> fail("Work from the closed server must not run"));
        queue.add(old);
        queue.close();
        queue.add(old);

        SearchTaskQueue restarted = new SearchTaskQueue(serverTasks::addLast, () -> {}, clock::get);
        Worker fresh = new Worker(1, () -> {});
        restarted.add(fresh);
        drain();
        assertEquals(0, old.calls);
        assertEquals(1, fresh.calls);
        assertEquals(0, validations.get());
    }

    @Test void shutdownDuringWorkDoesNotRescheduleTheRunningWorker() {
        Worker worker = new Worker(2, queue::close);
        queue.add(worker);
        drain();
        assertEquals(1, worker.calls);
    }

    private void drain() {
        int tasks = 0;
        while (!serverTasks.isEmpty()) {
            assertTrue(++tasks < 100, "The queue must eventually become idle");
            serverTasks.removeFirst().run();
        }
    }

    private static final class Worker implements WorldWorkerManager.IWorker {
        private int remaining;
        private int calls;
        private boolean cancelled;
        private boolean again = true;
        private final Runnable step;

        Worker(int remaining, Runnable step) {
            this.remaining = remaining;
            this.step = step;
        }

        @Override public boolean hasWork() { return !cancelled && remaining > 0; }

        @Override public boolean doWork() {
            calls++;
            remaining--;
            step.run();
            return again;
        }
    }
}
