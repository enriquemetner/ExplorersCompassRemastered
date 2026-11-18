package com.chaosthedude.explorerscompass.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompassTransferTest {
    private static final Identifier CITY = new Identifier("minecraft:ancient_city");
    private static final Identifier FORTRESS = new Identifier("minecraft:fortress");
    private static final Identifier MISSING = new Identifier("missing:structure");

    @Test void firstHolderAndSamePlayerRecoveryNeverResetOrReadOtherPlayers() {
        NbtCompound data = new NbtCompound();
        UUID player = UUID.randomUUID();
        var first = CompassTransfer.acquire(data, player, false, true,
                ignored -> { fail("No previous holder"); return Set.of(); }, ignored -> true);
        assertFalse(first.resetTarget());
        assertNull(first.sharingSource());
        assertEquals(player, data.getUuid(CompassTransfer.HOLDER));
        data = data.copy(); // Frames, containers, and dropped entities may copy stacks.
        assertFalse(CompassTransfer.acquire(data, player, false, true,
                ignored -> { fail("Same player must not import"); return Set.of(); }, ignored -> true).resetTarget());
    }

    @Test void differentPlayerWithoutSharingResetsOnlyTargetAndDoesNotImport() {
        NbtCompound data = new NbtCompound();
        UUID previous = UUID.randomUUID(), next = UUID.randomUUID();
        data.putUuid(CompassTransfer.HOLDER, previous);
        data.putInt("SearchEnergy", 12);
        data.putString("CustomName", "Keep me");
        var result = CompassTransfer.acquire(data, next, false, true,
                ignored -> { fail("Sharing disabled"); return Set.of(); }, ignored -> true);
        assertTrue(result.resetTarget());
        assertNull(result.sharingSource());
        CompassSearchData.clear(data);
        assertEquals(next, data.getUuid(CompassTransfer.HOLDER));
        assertEquals(12, data.getInt("SearchEnergy"));
        assertEquals("Keep me", data.getString("CustomName"));
        assertTrue(result.unlocks().isEmpty());
    }

    @Test void sharingReadsCurrentSavedUnlocksAndFiltersUnavailableTargetsOnlyOnce() {
        UUID previous = UUID.randomUUID(), next = UUID.randomUUID();
        Map<UUID, Set<Identifier>> offlineData = new HashMap<>();
        offlineData.put(previous, Set.of(CITY));
        NbtCompound data = new NbtCompound();
        data.putUuid(CompassTransfer.HOLDER, previous);
        data = data.copy();
        offlineData.put(previous, Set.of(CITY, FORTRESS, MISSING)); // Changes after dropping.
        var result = CompassTransfer.acquire(data, next, true, true, offlineData::get,
                id -> !id.equals(MISSING) && !id.equals(FORTRESS));
        assertEquals(Set.of(CITY), result.unlocks());
        assertTrue(data.getBoolean(CompassTransfer.INHERITED));
        assertEquals(next, data.getUuid(CompassTransfer.HOLDER));
        assertTrue(CompassTransfer.acquire(data, next, true, true,
                ignored -> { fail("Repeated inventory tick"); return Set.of(); }, ignored -> true).unlocks().isEmpty());
        NbtCompound empty = new NbtCompound();
        empty.putUuid(CompassTransfer.HOLDER, previous);
        var emptyTransfer = CompassTransfer.acquire(empty, next, true, false, ignored -> Set.of(), ignored -> true);
        assertEquals(previous, emptyTransfer.sharingSource());
        assertNull(CompassTransfer.acquire(empty, next, true, false, ignored -> Set.of(), ignored -> true).sharingSource());
        assertFalse(empty.getBoolean(CompassTransfer.INHERITED));
    }

    @Test void searchRollbackPreservesOwnershipDurabilityAndIgnoresStaleTokens() {
        NbtCompound data = new NbtCompound();
        data.putInt("State", CompassState.FOUND.getID());
        data.putString("StructureID", CITY.toString());
        data.putInt("FoundX", 100);
        data.putLong(CompassSearchData.FOUND_START_CHUNK, 42L);
        data.putBoolean(CompassTransfer.INHERITED, true);
        UUID token = UUID.randomUUID();
        CompassSearchData.begin(data, token, CompassSearchData.snapshot(data));
        data.putInt("State", CompassState.SEARCHING.getID());
        NbtCompound copy = data.copy();
        UUID newHolder = UUID.randomUUID();
        copy.putUuid(CompassTransfer.HOLDER, newHolder);
        copy.putInt("SearchEnergy", 9);
        assertEquals(100, CompassSearchData.pointingData(copy).getInt("FoundX"));
        assertTrue(CompassSearchData.restore(copy, token));
        assertEquals(newHolder, copy.getUuid(CompassTransfer.HOLDER));
        assertEquals(9, copy.getInt("SearchEnergy"));
        assertEquals(CompassState.FOUND.getID(), copy.getInt("State"));
        assertEquals(42L, copy.getLong(CompassSearchData.FOUND_START_CHUNK));
        assertTrue(copy.getBoolean(CompassTransfer.INHERITED));
        UUID newer = UUID.randomUUID();
        CompassSearchData.begin(copy, newer, CompassSearchData.snapshot(copy));
        assertFalse(CompassSearchData.restore(copy, token));
        assertEquals(newer, copy.getUuid(CompassSearchData.TOKEN));
        CompassSearchData.clear(copy);
        assertFalse(copy.contains(CompassSearchData.FOUND_START_CHUNK));
        assertFalse(copy.contains(CompassTransfer.INHERITED));
        assertFalse(copy.contains(CompassSearchData.BACKUP));
        assertEquals(9, copy.getInt("SearchEnergy"));
    }
}
