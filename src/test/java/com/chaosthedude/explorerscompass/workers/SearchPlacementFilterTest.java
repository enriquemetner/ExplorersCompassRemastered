package com.chaosthedude.explorerscompass.workers;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchPlacementFilterTest {
    private final Object structure = new Object();

    @Test void rejectsBeforeGenerationButLeavesExistingAnswersAlone() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals("saved", SearchPlacementFilter.lookup(1, structure, () -> {
            calls.incrementAndGet(); return false;
        }, () -> "saved"));
        assertEquals(0, calls.get(), "Existing records must not invoke placement rules");
        assertTrue(SearchPlacementFilter.lookup(1, structure, () -> false,
                () -> SearchPlacementFilter.rejects(1, structure)));
        assertFalse(SearchPlacementFilter.rejects(1, structure));
    }

    @Test void rejectionDoesNotLeakToOtherPlacementsStructuresOrChunks() {
        SearchPlacementFilter.lookup(1, structure, () -> false, () -> {
            assertFalse(SearchPlacementFilter.rejects(2, structure));
            assertFalse(SearchPlacementFilter.rejects(1, new Object()));
            assertTrue(SearchPlacementFilter.rejects(1, structure));
            return null;
        });
        assertFalse(SearchPlacementFilter.lookup(1, structure, () -> true,
                () -> SearchPlacementFilter.rejects(1, structure)));
    }

    @Test void restoresOuterContextAfterNestedLookupAndException() {
        SearchPlacementFilter.lookup(1, structure, () -> false, () -> {
            assertThrows(IllegalStateException.class, () -> SearchPlacementFilter.lookup(2, structure, () -> true,
                    () -> { throw new IllegalStateException(); }));
            assertTrue(SearchPlacementFilter.rejects(1, structure));
            return null;
        });
        assertFalse(SearchPlacementFilter.rejects(1, structure));
    }

    @Test void predicateExceptionsCleanUpAndReentrantPredicatesDoNotRecurse() {
        assertThrows(IllegalArgumentException.class, () -> SearchPlacementFilter.lookup(1, structure,
                () -> { throw new IllegalArgumentException(); }, () -> SearchPlacementFilter.rejects(1, structure)));
        assertFalse(SearchPlacementFilter.rejects(1, structure));
        assertTrue(SearchPlacementFilter.lookup(1, structure,
                () -> SearchPlacementFilter.rejects(1, structure), () -> SearchPlacementFilter.rejects(1, structure)));
    }

    @Test void doesNotAffectOtherThreads() {
        SearchPlacementFilter.lookup(1, structure, () -> false, () -> {
            boolean other = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> SearchPlacementFilter.rejects(1, structure)).join();
            assertFalse(other);
            assertTrue(SearchPlacementFilter.rejects(1, structure));
            return null;
        });
    }
}
