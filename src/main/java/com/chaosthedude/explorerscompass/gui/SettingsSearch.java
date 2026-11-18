package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

final class SettingsSearch {
    private SettingsSearch() {}

    static List<Integer> matchingRows(String[] labels, IntPredicate isDivider, String query) {
        return matchingRows(labels, isDivider, query, row -> true);
    }

    static List<Integer> matchingRows(String[] labels, IntPredicate isDivider, String query, IntPredicate isAvailable) {
        String[] fragments = fragments(query);
        List<Integer> rows = new ArrayList<>();
        int section = -1;
        int displayedSection = -1;
        String sectionName = "";
        for (int row = 0; row < labels.length; row++) {
            if (isDivider.test(row)) {
                section = row;
                sectionName = labels[row];
                continue;
            }
            String searchable = (sectionName + " " + labels[row]).toLowerCase(Locale.ROOT);
            if (!isAvailable.test(row) || !matches(searchable, fragments, null)) continue;
            if (section != displayedSection) {
                rows.add(section);
                displayedSection = section;
            }
            rows.add(row);
        }
        return rows;
    }

    static String[] fragments(String query) {
        return query.strip().toLowerCase(Locale.ROOT).split("\\s+");
    }

    static boolean matches(String searchable, String[] fragments, String alternate) {
        for (String fragment : fragments) {
            if (!searchable.contains(fragment) && (alternate == null || !alternate.contains(fragment))) return false;
        }
        return true;
    }
}
