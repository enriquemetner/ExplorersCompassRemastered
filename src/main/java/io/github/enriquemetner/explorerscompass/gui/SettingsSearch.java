package io.github.enriquemetner.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

final class SettingsSearch {
    private SettingsSearch() {}

    static <T> List<T> matchingRows(List<T> entries, Function<T, String> label, Predicate<T> isDivider,
                                    String query, Predicate<T> isAvailable) {
        String[] fragments = fragments(query);
        List<T> rows = new ArrayList<>();
        T section = null;
        boolean sectionShown = false;
        String sectionName = "";
        for (T row : entries) {
            if (isDivider.test(row)) {
                section = row;
                sectionShown = false;
                sectionName = label.apply(row);
                continue;
            }
            String searchable = (sectionName + " " + label.apply(row)).toLowerCase(Locale.ROOT);
            if (!isAvailable.test(row) || !matches(searchable, fragments, null)) continue;
            if (!sectionShown && section != null) {
                rows.add(section);
                sectionShown = true;
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
