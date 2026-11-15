package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.network.PartyRefreshPacket;
import com.chaosthedude.explorerscompass.network.UnlockToastPacket;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.util.VisitedStructures.Visit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Temporary unlocks and visits supplied by current OPAC party members. */
public final class PartySharing {
    public interface Provider { Map<UUID, Set<UUID>> parties(MinecraftServer server); }

    private static Provider provider;
    private static boolean dirty = true;
    private static boolean visitsDirty = true;
    private static Map<UUID, Set<Visit>> visitsByParty = Map.of();
    private static Map<UUID, Set<UUID>> membership = Map.of();
    private static Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    private PartySharing() {}

    public static void register(Provider source) { provider = source; }
    public static boolean enabled() { return provider != null && ExplorersCompassConfig.opacPartySharing; }
    public static void changed() { dirty = true; }
    public static void visitsChanged() { visitsDirty = true; }

    static boolean visitsEnabled() {
        return enabled() && ExplorersCompassConfig.visitsSharing;
    }

    public static void clear() {
        dirty = true;
        visitsDirty = true;
        visitsByParty = Map.of();
        membership = Map.of();
        snapshot = new Snapshot(Map.of(), Map.of());
    }

    static boolean grants(UUID player, Identifier id) {
        return enabled() && snapshot.forPlayer(player).contains(id);
    }

    record Snapshot(Map<UUID, Set<Identifier>> unlocksByParty, Map<UUID, UUID> partyByPlayer) {
        Set<Identifier> forPlayer(UUID player) {
            UUID party = partyByPlayer.get(player);
            return party == null ? Set.of() : unlocksByParty.get(party);
        }

        Set<Identifier> changesSince(Snapshot before, UUID player, UnlockedStructures state) {
            Set<Identifier> changes = new HashSet<>(forPlayer(player));
            changes.removeAll(before.forPlayer(player));
            Set<Identifier> lost = new HashSet<>(before.forPlayer(player));
            lost.removeAll(forPlayer(player));
            changes.addAll(lost);
            changes.removeAll(state.getStructures(player));
            return changes;
        }
    }

    static Snapshot build(UnlockedStructures state, Map<UUID, Set<UUID>> parties, Predicate<Identifier> allowed) {
        Map<UUID, Set<Identifier>> unlocksByParty = new HashMap<>();
        Map<UUID, UUID> partyByPlayer = new HashMap<>();
        parties.forEach((party, members) -> {
            Set<Identifier> union = new HashSet<>();
            for (UUID member : members) {
                partyByPlayer.put(member, party);
                if (!state.hasPendingRelock(member)) union.addAll(state.getStructures(member));
            }
            union.removeIf(allowed.negate());
            unlocksByParty.put(party, Set.copyOf(union));
        });
        return new Snapshot(Map.copyOf(unlocksByParty), Map.copyOf(partyByPlayer));
    }

    static Map<UUID, Set<Visit>> buildVisitUnions(VisitedStructures state, Map<UUID, Set<UUID>> parties) {
        Map<UUID, Set<Visit>> unions = new HashMap<>();
        parties.forEach((party, members) -> {
            Set<Visit> union = new HashSet<>();
            for (UUID member : members) union.addAll(state.savedVisits(member));
            unions.put(party, Set.copyOf(union));
        });
        return Map.copyOf(unions);
    }

    public static boolean hasVisit(MinecraftServer server, UUID player, Visit visit) {
        if (!visitsEnabled()) return false;
        UUID party = snapshot.partyByPlayer().get(player);
        if (party == null) return false;
        refreshVisitUnions(server, membership, false);
        return visitsByParty.getOrDefault(party, Set.of()).contains(visit);
    }

    private static void refreshVisitUnions(MinecraftServer server, Map<UUID, Set<UUID>> parties, boolean membershipChanged) {
        if (!visitsEnabled() || parties.isEmpty()) {
            visitsByParty = Map.of();
            visitsDirty = true;
        } else if (visitsDirty || membershipChanged) {
            visitsByParty = buildVisitUnions(VisitedStructures.get(server), parties);
            visitsDirty = false;
        }
    }

    public static void synchronize(MinecraftServer server) {
        Map<UUID, Set<UUID>> parties = enabled() ? provider.parties(server) : Map.of();
        boolean membershipChanged = !parties.equals(membership);
        refreshVisitUnions(server, parties, membershipChanged);
        if (!membershipChanged && (!dirty || parties.isEmpty())) return;

        UnlockedStructures state = UnlockedStructures.get(server);
        var known = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Snapshot before = snapshot;
        snapshot = build(state, parties, id -> known.containsId(id) && !StructureUtils.structureIdIsExcluded(id));
        membership = parties;
        dirty = false;
        var compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        for (var player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            if (!player.networkHandler.isConnectionOpen()) continue;
            Set<Identifier> changes = snapshot.changesSince(before, id, state);
            if (changes.isEmpty()) continue;

            int gained = 0;
            boolean lost = false;
            for (Identifier structure : changes) {
                if (snapshot.forPlayer(id).contains(structure)) gained++;
                else lost = true;
            }
            if (lost) compass.revalidateAccess(player);
            if (gained > 0 && state.notificationsEnabled(id)) UnlockToastPacket.send(player, gained, true);
            PartyRefreshPacket.send(player);
        }
    }
}
