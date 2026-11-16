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
    void allowsPrefixesOnBothEndsOfAnOrderedPhrase() {
        assertTrue(matches("Village Plains @example $over &settle", false, false, SET));
        assertTrue(matches("@example Village Plains $over &settle", false, false, SET));
        assertFalse(matches("@example Plains Village $over", false, false, SET));
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
        assertNull(StructureSearchQuery.parse("Village &settle Plains", false));
        assertNull(StructureSearchQuery.parse("@example Village @other", false));
    }

    private boolean matches(String query, boolean anyOrder, boolean allowIds, Identifier set) {
        return StructureSearchQuery.parse(query, anyOrder).matches(STRUCTURE, "Village Plains", DIMENSIONS, set, allowIds);
    }
}
