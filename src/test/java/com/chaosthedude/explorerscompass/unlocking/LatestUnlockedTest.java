package com.chaosthedude.explorerscompass.unlocking;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatestUnlockedTest {
    private final UUID player = UUID.randomUUID();
    private final Identifier a = new Identifier("minecraft:fortress");
    private final Identifier b = new Identifier("minecraft:stronghold");
    private final Identifier c = new Identifier("minecraft:ancient_city");

    @Test void remembersOnlyNewIdsFromTheMostRecentEventAcrossRestart() {
        UnlockedStructures state = new UnlockedStructures();
        assertTrue(state.latestUnlocked(player).isEmpty());
        state.add(player, Set.of(a));
        state.add(player, Set.of(a, b, c));
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(Set.of(b, c), state.latestUnlocked(player));
        state.add(player, Set.of(a));
        assertEquals(Set.of(b, c), state.latestUnlocked(player));
        state.remove(player, Set.of(b));
        assertEquals(Set.of(c), state.latestUnlocked(player));
    }

    @Test void restoringSavedStructuresDoesNotReplaceTheLastEvent() {
        UnlockedStructures state = new UnlockedStructures();
        state.add(player, Set.of(a));
        state.add(player, Set.of(b));
        state.beginUnlockEvent(player, state.getStructures(player));
        state.clearStructures(player);
        try { state.add(player, Set.of(a, b)); }
        finally { state.finishUnlockEvent(player); }
        assertEquals(Set.of(b), state.latestUnlocked(player));
        state.beginUnlockEvent(player, state.getStructures(player));
        state.clearStructures(player);
        try { state.add(player, Set.of(a, b, c)); }
        finally { state.finishUnlockEvent(player); }
        assertEquals(Set.of(c), state.latestUnlocked(player));
    }
}
