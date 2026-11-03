package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartySharingTest {
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), other = UUID.randomUUID();
    private static final Identifier A = new Identifier("minecraft:fortress"), B = new Identifier("minecraft:ancient_city");

    @Test void sharingRequiresBothTheOptionalAdapterAndEnabledSetting() {
        boolean previous = ExplorersCompassConfig.opacPartySharing;
        try {
            PartySharing.register(null);
            ExplorersCompassConfig.opacPartySharing = true;
            assertFalse(PartySharing.enabled());
            PartySharing.register(server -> List.of());
            assertTrue(PartySharing.enabled());
            ExplorersCompassConfig.opacPartySharing = false;
            assertFalse(PartySharing.enabled());
        } finally {
            PartySharing.register(null);
            ExplorersCompassConfig.opacPartySharing = previous;
        }
    }

    @Test void mergesExistingProgressOnceWithoutTouchingOtherParties() {
        UnlockedStructures state = new UnlockedStructures();
        state.add(alice, Set.of(A));
        state.add(bob, Set.of(B));
        var additions = PartySharing.merge(state, Set.of(alice, bob), id -> true);
        assertEquals(Set.of(B), additions.get(alice));
        assertEquals(Set.of(A), additions.get(bob));
        assertTrue(PartySharing.merge(state, Set.of(alice, bob), id -> true).isEmpty());
        assertTrue(state.getStructures(other).isEmpty());
        // Leaving does not remove imported progress; joining another party can share it.
        PartySharing.merge(state, Set.of(bob, other), id -> true);
        assertEquals(Set.of(A, B), state.getStructures(other));
    }

    @Test void excludedIdsAreNotImported() {
        UnlockedStructures state = new UnlockedStructures();
        state.add(alice, Set.of(A, B));
        PartySharing.merge(state, Set.of(alice, bob), id -> id.equals(A));
        assertEquals(Set.of(A), state.getStructures(bob));
    }

    @Test void offlineNotificationsPersistAreDeduplicatedAndOnlyCountStillUnlocked() {
        UnlockedStructures state = new UnlockedStructures();
        state.add(bob, Set.of(A, B));
        state.enableNotifications(bob);
        state.queueNotification(bob, Set.of(B));
        state.queuePartyNotification(bob, Set.of(A));
        state.queuePartyNotification(bob, Set.of(A));
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(1, state.takePendingNotificationCount(bob));
        assertEquals(1, state.takePartyNotificationCount(bob));
        assertEquals(0, state.takePartyNotificationCount(bob));
        state.queuePartyNotification(bob, Set.of(A));
        state.remove(bob, Set.of(A));
        assertEquals(0, state.takePartyNotificationCount(bob));
    }

    @Test void firstCompassAbsorbsQueuedSharedNotifications() {
        UnlockedStructures state = new UnlockedStructures();
        state.add(bob, Set.of(A));
        state.queuePartyNotification(bob, Set.of(A));
        assertTrue(state.enableNotifications(bob));
        assertEquals(0, state.takePartyNotificationCount(bob));
        assertEquals(Set.of(A), state.getStructures(bob));
    }

    @Test void reloadClearsOfflineCopiesBeforeUnionAndRetainsNewAdditionsForLogin() {
        boolean previous = ExplorersCompassConfig.opacPartySharing;
        PartySharing.register(server -> List.of(Set.of(alice, bob)));
        ExplorersCompassConfig.opacPartySharing = true;
        try {
            UnlockedStructures state = new UnlockedStructures();
            state.add(alice, Set.of(A));
            state.add(bob, Set.of(A));
            state.queueReload(Set.of(alice, bob), true, true);
            assertTrue(state.getStructures(bob).isEmpty());
            state.add(alice, Set.of(B));
            PartySharing.merge(state, Set.of(alice, bob), id -> true);
            state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
            assertEquals(Set.of(A), state.beforeRelock(bob));
            assertTrue(state.takePendingRelock(bob));
            assertTrue(state.hasPartyRelock(bob));
            assertEquals(Set.of(B), state.getStructures(bob));
            state.finishPartyRelock(bob);
            assertFalse(state.hasPartyRelock(bob));
            assertFalse(state.contains(alice, A));
            assertFalse(state.contains(bob, A));
        } finally {
            ExplorersCompassConfig.opacPartySharing = previous;
            PartySharing.register(null);
        }
    }

    @Test void aSecondReloadInvalidatesImportsReceivedWhileOffline() {
        boolean previous = ExplorersCompassConfig.opacPartySharing;
        PartySharing.register(server -> List.of(Set.of(alice, bob)));
        ExplorersCompassConfig.opacPartySharing = true;
        try {
            UnlockedStructures state = new UnlockedStructures();
            state.add(alice, Set.of(A));
            PartySharing.merge(state, Set.of(alice, bob), id -> true);
            state.queueReload(Set.of(alice, bob), true, true);
            state.add(alice, Set.of(B));
            PartySharing.merge(state, Set.of(alice, bob), id -> true);
            state.queueReload(Set.of(alice, bob), true, true);
            state.preparePartyRelocks();
            PartySharing.merge(state, Set.of(alice, bob), id -> true);
            assertTrue(state.getStructures(alice).isEmpty());
            assertTrue(state.getStructures(bob).isEmpty());
        } finally {
            PartySharing.register(null);
            ExplorersCompassConfig.opacPartySharing = previous;
        }
    }

    @Test void partyWideLockDoesNotLeaveAnotherCopyToRestoreTheTarget() {
        UnlockedStructures state = new UnlockedStructures();
        state.add(alice, Set.of(A, B));
        PartySharing.merge(state, Set.of(alice, bob), id -> true);
        for (UUID member : Set.of(alice, bob)) state.remove(member, Set.of(A));
        PartySharing.merge(state, Set.of(alice, bob), id -> true);
        assertEquals(Set.of(B), state.getStructures(alice));
        assertEquals(Set.of(B), state.getStructures(bob));
    }
}
