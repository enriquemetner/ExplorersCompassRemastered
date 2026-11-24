package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures.Visit;
import java.util.List;
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
        boolean previousVisits = ExplorersCompassConfig.visitsSharing;
        try {
            PartySharing.register(null);
            ExplorersCompassConfig.visitsSharing = true;
            ExplorersCompassConfig.opacPartySharing = true;
            assertFalse(PartySharing.enabled());
            assertFalse(PartySharing.visitsEnabled());
            assertFalse(PartySharing.grants(alice, fortress));
            PartySharing.register(server -> Map.of());
            assertTrue(PartySharing.enabled());
            assertTrue(PartySharing.visitsEnabled());
            ExplorersCompassConfig.visitsSharing = false;
            assertFalse(PartySharing.visitsEnabled());
            ExplorersCompassConfig.visitsSharing = true;
            ExplorersCompassConfig.opacPartySharing = false;
            assertFalse(PartySharing.enabled());
            assertFalse(PartySharing.visitsEnabled());
            assertFalse(PartySharing.grants(alice, fortress));
        } finally {
            PartySharing.register(null);
            ExplorersCompassConfig.opacPartySharing = previous;
            ExplorersCompassConfig.visitsSharing = previousVisits;
        }
    }

    @Test void clearingWorldStateKeepsTheRegisteredProvider() {
        boolean previous = ExplorersCompassConfig.opacPartySharing;
        try {
            ExplorersCompassConfig.opacPartySharing = true;
            PartySharing.register(server -> Map.of(firstParty, Set.of(alice, bob)));
            PartySharing.clear();
            assertTrue(PartySharing.enabled());
            assertFalse(PartySharing.grants(alice, fortress));
        } finally {
            PartySharing.register(null);
            PartySharing.clear();
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

    @Test void rebuildsFromOfflinePersonalSavesAndSharesOnlyUnaffectedUnlocks() {
        var state = new UnlockedStructures();
        state.add(alice, Set.of(fortress, stronghold));
        state.add(bob, Set.of(village));
        var parties = Map.of(firstParty, Set.of(alice, bob));
        PartySharing.build(state, parties, id -> true);
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(Set.of(village), state.getStructures(bob));
        assertEquals(Set.of(fortress, village), PartySharing.build(state, parties, id -> !id.equals(stronghold)).forPlayer(bob));

        state.relock(Set.of(stronghold), Map.of());
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(Set.of(stronghold), state.takePendingRelock(alice).direct());
        assertEquals(Set.of(fortress, village), PartySharing.build(state, parties, id -> true).forPlayer(bob));
    }
    @Test void visitUnionsDeduplicateAndFollowMembershipWithoutChangingPersonalHistory() {
        var state = new VisitedStructures();
        var nether = new Identifier("minecraft:the_nether");
        var overworld = new Identifier("minecraft:overworld");
        var shared = new Visit(nether, fortress, 2, -3);
        var otherDimension = new Visit(overworld, fortress, 2, -3);
        var otherChunk = new Visit(nether, fortress, 3, -3);
        var otherStructure = new Visit(nether, stronghold, 2, -3);
        state.addVisits(alice, List.of(shared, otherDimension, otherStructure));
        state.addVisits(bob, List.of(otherChunk, shared));
        var parties = Map.of(firstParty, Set.of(alice, bob), secondParty, Set.of(carol));

        assertEquals(Set.of(shared, otherDimension, otherChunk, otherStructure),
                PartySharing.buildVisitUnions(state, parties).get(firstParty));
        assertFalse(state.hasVisitedType(bob, stronghold));
        assertTrue(state.list(bob, Set.of(stronghold)).isEmpty());
        assertTrue(PartySharing.buildVisitUnions(state, parties).get(secondParty).isEmpty());

        state.remove(alice, List.of(shared, otherDimension));
        assertEquals(Set.of(shared, otherChunk, otherStructure), PartySharing.buildVisitUnions(state, parties).get(firstParty));
        var switched = Map.of(firstParty, Set.of(alice), secondParty, Set.of(bob, carol));
        assertEquals(Map.of(firstParty, Set.of(otherStructure), secondParty, Set.of(shared, otherChunk)),
                PartySharing.buildVisitUnions(state, switched));
        assertEquals(Map.of(), PartySharing.buildVisitUnions(state, Map.of()));
    }

    @Test void copyingSavedVisitsAppendsNewEntriesAndKeepsTheirOrderAfterRestart() {
        var state = new VisitedStructures();
        var dimension = new Identifier("minecraft:the_nether");
        var first = new Visit(dimension, fortress, 0, 0);
        var duplicate = new Visit(dimension, fortress, -2, 3);
        var newFirst = new Visit(dimension, fortress, 20, -2);
        var newSecond = new Visit(dimension, fortress, 3, 8);
        state.addVisits(alice, List.of(newFirst, duplicate, newSecond));
        state.addVisits(bob, List.of(first, duplicate));

        assertEquals(2, state.addVisits(bob, state.savedVisits(alice)));
        assertEquals(0, state.addVisits(bob, state.savedVisits(alice)));
        state.clear(alice, null);
        var reloaded = VisitedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(List.of(first, duplicate, newFirst, newSecond), reloaded.list(bob, Set.of(fortress)));
        assertTrue(reloaded.hasVisitedType(bob, fortress));
        assertTrue(reloaded.savedVisits(alice).isEmpty());
    }

    @Test void visitsRefreshAfterAnEditWithTheSameMembershipAndStayCachedBetweenReads() {
        var visits = new VisitedStructures();
        var unlocked = new UnlockedStructures();
        unlocked.add(alice, Set.of(fortress));
        var visit = new Visit(new Identifier("minecraft:the_nether"), fortress, 2, -3);
        var snapshot = PartySharing.build(unlocked, Map.of(firstParty, Set.of(alice, bob)), id -> true);
        var cache = new PartySharing.VisitCache();
        assertTrue(cache.refresh(true, snapshot.membership(), () -> visits).get(firstParty).isEmpty());

        visits.addVisits(alice, List.of(visit));
        cache.invalidate();
        var refreshed = cache.refresh(true, snapshot.membership(), () -> visits);
        assertEquals(Set.of(visit), refreshed.get(firstParty));
        assertSame(refreshed, cache.refresh(true, snapshot.membership(), () -> fail("Cache was already current")));
        assertEquals(Set.of(fortress), snapshot.forPlayer(bob));
        assertTrue(visits.savedVisits(bob).isEmpty());
    }
}
