package io.github.enriquemetner.explorerscompass.gui;

final class OverlayInput {
    enum Layer { PAGE, SORT, ENTRY_FILTERS, SEARCH, LOCKED, DISABLED, DETAILS, CURRENT_STRUCTURE }
    enum Event { ACTION, POINTER, KEY }

    private OverlayInput() {}

    static Layer layer(Event event, boolean current, boolean details, boolean entryFilters,
                       boolean sort, Layer search) {
        // Wheel, drag, and release give disabled results priority over covering overlays.
        if (event == Event.POINTER && search == Layer.DISABLED) return search;
        if (current) return Layer.CURRENT_STRUCTURE;
        if (details) return Layer.DETAILS;
        if (event == Event.ACTION && search == Layer.DISABLED) return search;
        if (entryFilters) return Layer.ENTRY_FILTERS;
        if (sort && event != Event.POINTER) return Layer.SORT;
        return search;
    }
}
