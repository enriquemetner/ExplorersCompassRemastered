package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartySharingTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID firstParty = UUID.randomUUID();
    private final UUID secondParty = UUID.randomUUID();
    private final Identifier fortress = new Identifier("minecraft:fortress");
    private final Identifier stronghold = new Identifier("minecraft:stronghold");
    private final Identifier village = new Identifier("minecraft:village_plains");

    @Test void sharingRequiresBothProviderAndSetting() {
        boolean previous = ExplorersCompassConfig.opacPartySharing;
        try {
            PartySharing.register(null);
            ExplorersCompassConfig.opacPartySharing = true;
            assertFalse(PartySharing.enabled());
            assertFalse(PartySharing.grants(alice, fortress));
            PartySharing.register(server -> Map.of());
            assertTrue(PartySharing.enabled());
            ExplorersCompassConfig.opacPartySharing = false;
            assertFalse(PartySharing.enabled());
            assertFalse(PartySharing.grants(alice, fortress));
        } finally {
            PartySharing.register(null);
            ExplorersCompassConfig.opacPartySharing = previous;
        }
    }

    @Test void switchingPartiesAndDisbandingUpdateBothSides() {
        var state = new UnlockedStructures();
        state.add(alice, Set.of(fortress));
        state.add(bob, Set.of(stronghold));
        state.add(carol, Set.of(village));
        var before = PartySharing.build(state, Map.of(firstParty, Set.of(alice, bob), secondParty, Set.of(carol)), id -> true);
        assertEquals(Set.of(fortress, stronghold), before.unlocksByParty().get(firstParty));
        assertEquals(Set.of(stronghold), state.getStructures(bob));
        assertEquals(Set.of(stronghold), state.latestUnlocked(bob));

        var switched = PartySharing.build(state, Map.of(firstParty, Set.of(alice), secondParty, Set.of(bob, carol)), id -> true);
        assertEquals(Set.of(fortress), switched.forPlayer(alice));
        assertEquals(Set.of(stronghold, village), switched.forPlayer(bob));
        assertEquals(Set.of(stronghold), switched.changesSince(before, alice, state));
        assertEquals(Set.of(fortress, village), switched.changesSince(before, bob, state));
        assertEquals(Set.of(stronghold), switched.changesSince(before, carol, state));

        var disbanded = PartySharing.build(state, Map.of(firstParty, Set.of(alice)), id -> true);
        assertTrue(disbanded.forPlayer(bob).isEmpty());
        assertEquals(Set.of(village), disbanded.changesSince(switched, bob, state));
        assertEquals(Set.of(stronghold), disbanded.changesSince(switched, carol, state));
    }

    @Test void independentlyEarnedUnlockSurvivesDepartureAndOverlapSurvivesRemoval() {
        var state = new UnlockedStructures();
        state.add(alice, Set.of(fortress));
        var members = Map.of(firstParty, Set.of(alice, bob, carol));
        var borrowed = PartySharing.build(state, members, id -> true);
        assertEquals(Set.of(fortress), borrowed.forPlayer(bob));
        assertTrue(state.latestUnlocked(bob).isEmpty());

        state.add(bob, Set.of(fortress));
        var earned = PartySharing.build(state, members, id -> true);
        assertTrue(earned.changesSince(borrowed, bob, state).isEmpty());
        assertEquals(Set.of(fortress), state.latestUnlocked(bob));

        state.remove(alice, Set.of(fortress));
        var afterLock = PartySharing.build(state, members, id -> true);
        assertEquals(Set.of(fortress), afterLock.forPlayer(alice));
        var afterLeave = PartySharing.build(state, Map.of(firstParty, Set.of(alice, carol)), id -> true);
        assertTrue(afterLeave.forPlayer(alice).isEmpty());
        assertTrue(afterLeave.changesSince(afterLock, bob, state).isEmpty());
        assertEquals(Set.of(fortress), afterLeave.changesSince(afterLock, carol, state));
        assertEquals(Set.of(fortress), state.getStructures(bob));
    }

    @Test void rebuildsFromOfflinePersonalSavesAndSkipsIneligibleOrPendingResetEntries() {
        var state = new UnlockedStructures();
        state.add(alice, Set.of(fortress, stronghold));
        state.add(bob, Set.of(village));
        var parties = Map.of(firstParty, Set.of(alice, bob));
        PartySharing.build(state, parties, id -> true);
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(Set.of(village), state.getStructures(bob));
        assertEquals(Set.of(fortress, village), PartySharing.build(state, parties, id -> !id.equals(stronghold)).forPlayer(bob));

        state.queueReload(Set.of(alice), true, false);
        assertEquals(Set.of(village), PartySharing.build(state, parties, id -> true).forPlayer(bob));
        assertTrue(state.takePendingRelock(alice));
        state.clearStructures(alice);
        state.add(alice, Set.of(fortress));
        assertEquals(Set.of(fortress, village), PartySharing.build(state, parties, id -> true).forPlayer(bob));
    }
}
