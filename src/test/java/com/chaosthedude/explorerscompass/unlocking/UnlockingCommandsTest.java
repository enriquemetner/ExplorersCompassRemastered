package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnlockingCommandsTest {
    @Test void reportHighlightsDetailsWithoutColoringTheMessage() {
        var error = UnlockingCommands.formatDiagnostic(new UnlockingRules.Diagnostic(UnlockingRules.Severity.ERROR,
                "Could not load unlocking_rules.json: Malformed JSON at line 2 column 39 path $.default"));
        assertEquals(TextColor.fromFormatting(Formatting.WHITE), error.getStyle().getColor());
        assertEquals(TextColor.fromFormatting(Formatting.RED), error.getSiblings().get(0).getStyle().getColor());
        assertEquals(List.of("unlocking_rules.json", "2", "39", "$.default"), yellowParts(error));

        var warning = UnlockingCommands.formatDiagnostic(new UnlockingRules.Diagnostic(UnlockingRules.Severity.WARNING,
                "unlocking_rules.json: minecraft:fortress is listed more than once under \"unlocks\" on lines 4, 7"));
        assertEquals(TextColor.fromFormatting(Formatting.GOLD), warning.getSiblings().get(0).getStyle().getColor());
        assertEquals(List.of("unlocking_rules.json", "minecraft:fortress", "unlocks", "4, 7"), yellowParts(warning));

        var rule = UnlockingCommands.formatDiagnostic(new UnlockingRules.Diagnostic(UnlockingRules.Severity.ERROR,
                "unlocking_rules.json: Ignored invalid rule \"typo\" at line 6: No structures match selector ancient_citys"));
        assertEquals(List.of("unlocking_rules.json", "typo", "6", "ancient_citys"), yellowParts(rule));
    }

    private static List<String> yellowParts(net.minecraft.text.Text text) {
        return text.getSiblings().stream()
                .filter(part -> TextColor.fromFormatting(Formatting.YELLOW).equals(part.getStyle().getColor()))
                .map(net.minecraft.text.Text::getString)
                .toList();
    }

    @Test void visitCoordinatesSuggestCompassTeleportWithoutExecutingIt() {
        var text = UnlockingCommands.visitCoordinates(new Identifier("minecraft:fortress"), -64, 120);
        assertEquals("[-64, 120]", text.getString());
        assertEquals(TextColor.fromFormatting(Formatting.GREEN), text.getStyle().getColor());
        assertEquals(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/explorerscompass tp minecraft:fortress -64 120"),
                text.getStyle().getClickEvent());
    }

    @Test void blacklistedIdsAreUnknownAndExcludedFromCommandTargetsAndSuggestions() {
        List<String> original = ExplorersCompassConfig.structureBlacklist;
        Identifier city = new Identifier("minecraft:ancient_city");
        Identifier grave = new Identifier("graveyard:small_graveyard");
        Identifier fortress = new Identifier("minecraft:fortress");
        Set<Identifier> registry = Set.of(city, grave, fortress);
        try {
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_city", "graveyard:*");
            assertEquals(Set.of(fortress), UnlockingCommands.commandStructures(registry));
            for (Identifier id : List.of(city, grave)) {
                CommandSyntaxException error = assertThrows(CommandSyntaxException.class,
                        () -> UnlockingCommands.requireNotExcluded(id));
                assertEquals("Unknown structure: " + id, error.getRawMessage().getString());
            }
            assertDoesNotThrow(() -> UnlockingCommands.requireNotExcluded(fortress));
            assertEquals(3, registry.size());
            ExplorersCompassConfig.structureBlacklist = List.of();
            assertEquals(registry, UnlockingCommands.commandStructures(registry));
        } finally {
            ExplorersCompassConfig.structureBlacklist = original;
        }
    }
}
