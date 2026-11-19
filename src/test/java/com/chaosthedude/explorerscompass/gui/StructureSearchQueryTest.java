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
    void combinesPartialAndFullIds() {
        assertTrue(matches("&example_mod:settlements %MOD:moon @EXAMPLE Village Plains", false, SET));
        assertFalse(matches("@Example-Mod", false, SET));
        assertFalse(matches("&settle", false, null));
        assertFalse(matches("%nether", false, SET));
    }

    @Test
    void matchesPlainTextUsingTheSelectedMode() {
        assertTrue(matches("pl %over @example vil &settle", true, SET));
        assertFalse(matches("@example Plains Village", false, SET));
        assertFalse(matches("@example village_plains", true, SET));
        assertTrue(matches("@example $village_plains", true, SET));
    }

    @Test
    void allowsPrefixesOnBothEndsOfAnOrderedPhrase() {
        assertTrue(matches("Village Plains @example %over &settle", false, SET));
        assertTrue(matches("@example Village Plains %over &settle", false, SET));
        assertFalse(matches("@example Plains Village %over", false, SET));
    }

    @Test
    void barePrefixesMatchAnyAvailableId() {
        assertTrue(matches("@ % & Village Plains", false, SET));
        assertFalse(matches("&", false, null));
        assertFalse(StructureSearchQuery.parse("%", false).matches(STRUCTURE, "Village Plains", List.of(), SET));
    }

    @Test
    void structureIdPrefixCombinesWithOtherPrefixes() {
        assertTrue(matches("$EXAMPLE_MOD:village_", false, SET));
        assertTrue(matches("$plains &settle %moon @example", true, SET));
        assertFalse(matches("$settlements", false, SET));
        assertFalse(matches("$Village-Plains", false, SET));
        assertTrue(matches("$", false, null));
        assertNull(StructureSearchQuery.parse("$one $two", true));
        assertNull(StructureSearchQuery.parse("&one &two", true));
    }

    @Test
    void filterSearchRequiresPrefixForIds() {
        assertTrue(StructureSearchQuery.matchesFilter("Village", "Village Plains", "example:village_plains"));
        assertFalse(StructureSearchQuery.matchesFilter("example:", "Village Plains", "example:village_plains"));
        assertTrue(StructureSearchQuery.matchesFilter("$EXAMPLE:vil", "Village Plains", "example:village_plains"));
        assertTrue(StructureSearchQuery.matchesFilter("$", "Village Plains", "example:village_plains"));
        assertFalse(StructureSearchQuery.matchesFilter("$", "Current", null));
    }

    @Test
    void rejectsMalformedQueries() {
        assertNull(StructureSearchQuery.parse("@example @other", true));
        assertNull(StructureSearchQuery.parse("Village &settle Plains", false));
        assertNull(StructureSearchQuery.parse("@example Village @other", false));
    }

    private boolean matches(String query, boolean anyOrder, Identifier set) {
        return StructureSearchQuery.parse(query, anyOrder).matches(STRUCTURE, "Village Plains", DIMENSIONS, set);
    }
}
