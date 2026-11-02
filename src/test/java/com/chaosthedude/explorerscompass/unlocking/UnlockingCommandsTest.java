package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnlockingCommandsTest {
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
                        () -> UnlockingCommands.requireNotBlacklisted(id));
                assertEquals("Unknown structure: " + id, error.getRawMessage().getString());
            }
            assertDoesNotThrow(() -> UnlockingCommands.requireNotBlacklisted(fortress));
            assertEquals(3, registry.size());
            ExplorersCompassConfig.structureBlacklist = List.of();
            assertEquals(registry, UnlockingCommands.commandStructures(registry));
        } finally {
            ExplorersCompassConfig.structureBlacklist = original;
        }
    }
}
