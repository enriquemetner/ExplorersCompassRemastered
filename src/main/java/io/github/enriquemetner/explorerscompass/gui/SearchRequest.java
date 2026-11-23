package io.github.enriquemetner.explorerscompass.gui;

import net.minecraft.util.Identifier;

final class SearchRequest {
    final int id;
    final Identifier structure;
    final int selectedCount;
    final boolean excludeVisited;
    private int wrongDimension;
    private int disabled;
    private int radius;
    private boolean confirmed;
    private long confirmedAt;

    SearchRequest(int id, Identifier structure, int selectedCount, boolean excludeVisited) {
        this.id = id;
        this.structure = structure;
        this.selectedCount = selectedCount;
        this.excludeVisited = excludeVisited;
    }

    boolean listed() { return structure == null; }

    boolean accepts(int requestId) { return id == requestId; }

    boolean confirmed() { return confirmed; }

    long confirmedAt() { return confirmedAt; }

    int radius() { return radius; }

    boolean hasUnavailable() { return wrongDimension > 0 || disabled > 0; }

    String unavailableCounts() { return "Wrong dimension: " + wrongDimension + "   Disabled: " + disabled; }

    void updateAvailability(int wrongDimension, int disabled) {
        this.wrongDimension = wrongDimension;
        this.disabled = disabled;
    }

    void progress(int radius, long now) {
        if (!confirmed) {
            confirmed = true;
            confirmedAt = now;
        }
        this.radius = Math.max(this.radius, radius);
    }
}
