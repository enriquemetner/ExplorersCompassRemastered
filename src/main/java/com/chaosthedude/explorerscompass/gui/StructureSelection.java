package com.chaosthedude.explorerscompass.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.Identifier;

/** Selection belongs to the screen and survives rebuilding its visible rows. */
final class StructureSelection {
    private final Set<Identifier> selected = new LinkedHashSet<>();
    private Identifier anchor;
    private Identifier lead;

    void click(Identifier id, List<Identifier> visible, boolean control, boolean shift, boolean selectedOnly) {
        if (shift && !selectedOnly) {
            int end = visible.indexOf(id);
            int start = anchor == null ? -1 : visible.indexOf(anchor);
            if (start < 0) {
                anchor = id;
                start = end;
            }
            if (!control) selected.clear();
            selected.addAll(visible.subList(Math.min(start, end), Math.max(start, end) + 1));
        } else {
            if (!control || shift) selected.clear();
            if (!selected.add(id)) selected.remove(id);
            anchor = id;
        }
        if (selected.contains(id)) lead = id;
        else restoreFocus();
    }

    void addAll(List<Identifier> visible) {
        selected.addAll(visible);
        if (anchor == null && !visible.isEmpty()) anchor = visible.get(0);
        if (lead == null && selected.contains(anchor)) lead = anchor;
        restoreFocus();
    }

    void clear() {
        selected.clear();
        anchor = null;
        lead = null;
    }

    void retain(Predicate<Identifier> valid) {
        selected.removeIf(valid.negate());
        if (anchor != null && !valid.test(anchor)) anchor = null;
        restoreFocus();
    }

    private void restoreFocus() {
        if (selected.contains(lead)) return;
        lead = null;
        for (Identifier id : selected) lead = id;
    }

    boolean contains(Identifier id) { return selected.contains(id); }
    boolean isEmpty() { return selected.isEmpty(); }
    int size() { return selected.size(); }
    Identifier lead() { return lead; }
    List<Identifier> ids() { return List.copyOf(selected); }
}
