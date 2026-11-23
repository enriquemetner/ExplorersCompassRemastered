package io.github.enriquemetner.explorerscompass.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTest {
    @Test void resultCanMatchBeforeProgressAndOldRequestsCannotMatchLaterSearches() {
        var first = new SearchRequest(5, null, 3, false);
        assertTrue(first.accepts(5));
        assertFalse(first.confirmed());

        var replacement = new SearchRequest(6, null, 2, true);
        assertFalse(replacement.accepts(5));
        assertTrue(replacement.accepts(6));
        assertFalse(replacement.hasUnavailable());
    }

    @Test void progressKeepsItsFirstConfirmationTimeAndHighestRadius() {
        var request = new SearchRequest(6, null, 2, true);
        request.updateAvailability(1, 0);
        request.progress(25, 100);
        request.progress(10, 110);
        assertEquals(25, request.radius());
        assertEquals(100, request.confirmedAt());
        assertEquals("Wrong dimension: 1   Disabled: 0", request.unavailableCounts());
    }
}
