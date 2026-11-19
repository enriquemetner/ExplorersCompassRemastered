package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.util.VisitedStructures;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitTaskMatchTest {
    private static final Identifier FORTRESS = new Identifier("minecraft:fortress");
    private static final Identifier CITY = new Identifier("minecraft:ancient_city");

    @Test void onePlayerMustSatisfyAllAndClearedHistoryStopsRecompletion() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        NbtList players = new NbtList();
        players.add(player(first, FORTRESS));
        players.add(player(second, CITY));
        NbtCompound data = new NbtCompound();
        data.put("Players", players);
        VisitedStructures splitHistory = VisitedStructures.fromNbt(data);
        Set<Identifier> targets = Set.of(FORTRESS, CITY);

        assertFalse(VisitTaskMatch.satisfied(Set.of(), false, id -> splitHistory.hasVisitedType(first, id)));
        assertTrue(VisitTaskMatch.satisfied(targets, false, id -> splitHistory.hasVisitedType(second, id)));
        assertFalse(VisitTaskMatch.satisfied(targets, true, id -> splitHistory.hasVisitedType(first, id)));
        assertFalse(VisitTaskMatch.satisfied(targets, true, id -> splitHistory.hasVisitedType(second, id)));

        players.set(0, player(first, FORTRESS, CITY));
        VisitedStructures completedHistory = VisitedStructures.fromNbt(data);
        assertTrue(VisitTaskMatch.satisfied(targets, true, id -> completedHistory.hasVisitedType(first, id)));

        completedHistory.clear(first, FORTRESS);
        assertFalse(VisitTaskMatch.satisfied(targets, true, id -> completedHistory.hasVisitedType(first, id)));
    }

    private static NbtCompound player(UUID uuid, Identifier... structures) {
        NbtCompound entry = new NbtCompound();
        entry.putUuid("UUID", uuid);
        NbtList visits = new NbtList();
        for (int i = 0; i < structures.length; i++) {
            NbtCompound visit = new NbtCompound();
            visit.putString("Dimension", "minecraft:overworld");
            visit.putString("Structure", structures[i].toString());
            visit.putInt("ChunkX", i);
            visits.add(visit);
        }
        entry.put("Visits", visits);
        return entry;
    }
}
