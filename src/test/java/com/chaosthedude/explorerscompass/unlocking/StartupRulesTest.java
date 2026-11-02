package com.chaosthedude.explorerscompass.unlocking;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StartupRulesTest {
    @Test void unchangedStartupPreservesUnlocksAndDoesNotQueueReload() {
        UUID player = UUID.randomUUID();
        Identifier structure = new Identifier("minecraft:ancient_city");
        UnlockedStructures state = new UnlockedStructures();
        state.add(player, Set.of(structure));
        UnlockReloads.prepareStartup(state, "rules-and-ftb", true);
        assertFalse(state.takePendingRelock(player));
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        UnlockReloads.prepareStartup(state, "rules-and-ftb", true);
        assertFalse(state.takePendingRelock(player));
        assertFalse(state.takePendingReloadNotice(player));
        assertEquals(Set.of(structure), state.getStructures(player));
        UnlockReloads.prepareStartup(state, "changed-rules-or-ftb", true);
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertTrue(state.takePendingRelock(player));
        assertTrue(state.takePendingReloadNotice(player));
    }

    @Test void changedDefinitionsRespectDisabledRelocking() {
        UUID player = UUID.randomUUID();
        UnlockedStructures state = new UnlockedStructures();
        state.add(player, Set.of(new Identifier("minecraft:fortress")));
        UnlockReloads.prepareStartup(state, "original", false);
        UnlockReloads.prepareStartup(state, "changed", false);
        assertFalse(state.takePendingRelock(player));
        assertTrue(state.takePendingReloadNotice(player));
    }
}
