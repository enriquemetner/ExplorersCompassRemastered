package io.github.enriquemetner.explorerscompass.compat.jes;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class JesThumbnailsTest {
    private final List<Identifier> rows = IntStream.range(0, 9).mapToObj(i -> new Identifier("row_" + i)).toList();

    @Test void visibleMissesComeBeforeVisibleHitsAndOffscreenRowsAlternate() {
        var cached = Set.of(rows.get(3), rows.get(5));
        var previewable = Set.copyOf(rows.subList(0, 8));

        assertEquals(ids(4, 3, 5, 2, 6, 1, 7, 0), JesThumbnails.orderCandidates(
                rows.size(), 3, 6, rows::get, previewable, Set.of(), cached::contains));
    }

    @Test void ignoresIneligibleRowsAndSkipsAlreadyPreloadedRowsOnlyOffscreen() {
        assertEquals(ids(4, 3, 7, 0), JesThumbnails.orderCandidates(
                rows.size(), 3, 6, rows::get, Set.copyOf(ids(0, 1, 2, 3, 4, 6, 7)),
                Set.copyOf(ids(1, 2, 3, 6)), Set.copyOf(ids(3))::contains));
    }

    @Test void emptyViewportStartsAtTheNearestOffscreenRows() {
        assertEquals(ids(1, 2, 0, 3), JesThumbnails.orderCandidates(
                4, 2, 2, rows::get, Set.copyOf(ids(0, 1, 2, 3)), Set.of(),
                id -> fail("An offscreen row should not be checked for a cache hit")));
        assertEquals(List.of(), JesThumbnails.orderCandidates(
                0, 0, 0, index -> fail("There are no rows"), Set.of(), Set.of(), id -> false));
    }

    private List<Identifier> ids(int... indices) {
        return IntStream.of(indices).mapToObj(rows::get).toList();
    }
}
