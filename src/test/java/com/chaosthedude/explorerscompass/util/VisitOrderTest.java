package com.chaosthedude.explorerscompass.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VisitOrderTest {
    @Test void recordedOrderSurvivesSavingFilteringDuplicatesAndRemoval() {
        UUID player = UUID.randomUUID();
        Identifier city = new Identifier("minecraft:ancient_city");
        Identifier fortress = new Identifier("minecraft:fortress");
        NbtList entries = new NbtList();
        entries.add(visit(city, 80));
        entries.add(visit(fortress, -20));
        entries.add(visit(city, 5));
        entries.add(visit(city, 80)); // Revisiting must not move an older visit.
        NbtCompound savedPlayer = new NbtCompound();
        savedPlayer.putUuid("UUID", player);
        savedPlayer.put("Visits", entries);
        NbtList players = new NbtList();
        players.add(savedPlayer);
        NbtCompound root = new NbtCompound();
        root.put("Players", players);

        VisitedStructures state = VisitedStructures.fromNbt(root);
        Set<Identifier> both = Set.of(city, fortress);
        var ordered = state.list(player, both);
        assertEquals(List.of(80, -20, 5), ordered.stream().map(VisitedStructures.Visit::chunkX).toList());
        assertEquals(List.of(80, 5), state.list(player, Set.of(city)).stream().map(VisitedStructures.Visit::chunkX).toList());
        state = VisitedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(ordered, state.list(player, both));
        assertEquals(1, state.remove(player, state.list(player, both).subList(1, 2)));
        assertEquals(List.of(80, 5), state.list(player, both).stream().map(VisitedStructures.Visit::chunkX).toList());
        assertEquals(3, ordered.size()); // Earlier snapshots stay unchanged.
    }

    private static NbtCompound visit(Identifier structure, int chunkX) {
        NbtCompound entry = new NbtCompound();
        entry.putString("Dimension", "minecraft:overworld");
        entry.putString("Structure", structure.toString());
        entry.putInt("ChunkX", chunkX);
        entry.putInt("ChunkZ", 0);
        return entry;
    }
}
