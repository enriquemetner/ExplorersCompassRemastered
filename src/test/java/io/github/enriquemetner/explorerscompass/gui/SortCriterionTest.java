package io.github.enriquemetner.explorerscompass.gui;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SortCriterionTest {
    @Test void equalSourceKeysStayEqualForDifferentNames() {
        var collator = Collator.getInstance(Locale.US);
        var fortress = new Identifier("minecraft:fortress");
        var stronghold = new Identifier("minecraft:stronghold");
        assertEquals(0, SortCriterion.SOURCE.compare(fortress, stronghold, collator));
        assertTrue(SortCriterion.NAME.compare(fortress, stronghold, collator) < 0);
    }

    @Test void criteriaKeepTheirSavedNamesAndDialogLabels() {
        var criteria = Arrays.asList(SortCriterion.values());
        assertEquals(List.of("NAME", "SOURCE", "DIMENSION", "SET"), criteria.stream().map(Enum::name).toList());
        assertEquals(List.of("Name", "Source", "Dimension", "Set"),
                criteria.stream().map(criterion -> criterion.getLabel().getString()).toList());
    }
}
