package com.chaosthedude.explorerscompass.gui;

import java.util.List;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureSearchQueryTest {
    private static final Identifier STRUCTURE = new Identifier("example_mod", "village_plains");
    private static final Identifier SET = new Identifier("example_mod", "settlements");
    private static final List<Identifier> DIMENSIONS = List.of(
            new Identifier("minecraft", "overworld"), new Identifier("example_mod", "moon"));

    @Test
    void combinesPartialAndFullIdsWithoutAllowIdSearches() {
        assertTrue(matches("&example_mod:settlements $MOD:moon @EXAMPLE Village Plains", false, false, SET));
        assertFalse(matches("@Example-Mod", false, false, SET));
        assertFalse(matches("&settle", false, false, null));
        assertFalse(matches("$nether", false, false, SET));
    }

    @Test
    void matchesPlainTextUsingTheSelectedMode() {
        assertTrue(matches("pl $over @example vil &settle", true, false, SET));
        assertFalse(matches("@example Plains Village", false, false, SET));
        assertFalse(matches("@example village_plains", true, false, SET));
        assertTrue(matches("@example village_plains", true, true, SET));
    }

    @Test
    void barePrefixesMatchAnyAvailableId() {
        assertTrue(matches("@ $ & Village Plains", false, false, SET));
        assertFalse(matches("&", false, false, null));
        assertFalse(StructureSearchQuery.parse("$", false).matches(STRUCTURE, "Village Plains", List.of(), SET, false));
    }

    @Test
    void rejectsMalformedQueries() {
        assertNull(StructureSearchQuery.parse("@example @other", true));
        assertNull(StructureSearchQuery.parse("Village &settle", false));
    }

    private boolean matches(String query, boolean anyOrder, boolean allowIds, Identifier set) {
        return StructureSearchQuery.parse(query, anyOrder).matches(STRUCTURE, "Village Plains", DIMENSIONS, set, allowIds);
    }
}
