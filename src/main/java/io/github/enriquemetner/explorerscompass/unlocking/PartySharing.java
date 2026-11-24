package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.network.unlocking.PartyRefreshPacket;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures.Visit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Temporary unlocks and visits supplied by current OPAC party members. */
public final class PartySharing {
    public interface Provider { Map<UUID, Set<UUID>> parties(MinecraftServer server); }

    private static Provider provider;
    private static boolean dirty = true;
    private static final VisitCache visits = new VisitCache();
    private static Snapshot snapshot = Snapshot.EMPTY;

    private PartySharing() {}

    public static void register(Provider source) { provider = source; }
    public static boolean enabled() { return provider != null && ExplorersCompassConfig.opacPartySharing; }
    public static void changed() { dirty = true; }
    public static void visitsChanged() { visits.invalidate(); }

    static boolean visitsEnabled() {
        return enabled() && ExplorersCompassConfig.visitsSharing;
    }

    public static void clear() {
        dirty = true;
        visits.invalidate();
        snapshot = Snapshot.EMPTY;
    }

    static boolean grants(UUID player, Identifier id) {
        return enabled() && snapshot.forPlayer(player).contains(id);
    }

    record Snapshot(Map<UUID, Set<UUID>> membership, Map<UUID, Set<Identifier>> unlocksByParty,
                    Map<UUID, UUID> partyByPlayer) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of());

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
                union.addAll(state.getStructures(member));
            }
            union.removeIf(allowed.negate());
            unlocksByParty.put(party, Set.copyOf(union));
        });
        return new Snapshot(parties, Map.copyOf(unlocksByParty), Map.copyOf(partyByPlayer));
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
        Snapshot current = snapshot;
        UUID party = current.partyByPlayer().get(player);
        if (party == null) return false;
        return visits.refresh(true, current.membership(), () -> VisitedStructures.get(server))
                .getOrDefault(party, Set.of()).contains(visit);
    }

    static final class VisitCache {
        private Map<UUID, Set<UUID>> membership;
        private Map<UUID, Set<Visit>> unions = Map.of();

        void invalidate() {
            membership = null;
            unions = Map.of();
        }

        Map<UUID, Set<Visit>> refresh(boolean enabled, Map<UUID, Set<UUID>> parties, Supplier<VisitedStructures> state) {
            if (!enabled || parties.isEmpty()) {
                invalidate();
            } else if (membership != parties) {
                // Synchronization reuses the same membership instance until it changes.
                unions = buildVisitUnions(state.get(), parties);
                membership = parties;
            }
            return unions;
        }
    }

    public static void synchronize(MinecraftServer server) {
        Snapshot before = snapshot;
        Map<UUID, Set<UUID>> parties = enabled() ? provider.parties(server) : Map.of();
        boolean membershipChanged = !parties.equals(before.membership());
        Map<UUID, Set<UUID>> membership = membershipChanged ? copyMembership(parties) : before.membership();
        visits.refresh(visitsEnabled(), membership, () -> VisitedStructures.get(server));
        if (!membershipChanged && (!dirty || parties.isEmpty())) return;

        UnlockedStructures state = UnlockedStructures.get(server);
        var known = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Snapshot current = build(state, membership, id -> known.containsId(id) && !StructureUtils.structureIdIsExcluded(id));
        snapshot = current;
        dirty = false;
        var compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        for (var player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            if (!player.networkHandler.isConnectionOpen()) continue;
            Set<Identifier> changes = current.changesSince(before, id, state);
            if (changes.isEmpty()) continue;

            int gained = 0;
            boolean lost = false;
            for (Identifier structure : changes) {
                if (current.forPlayer(id).contains(structure)) gained++;
                else lost = true;
            }
            if (lost) compass.revalidateAccess(player);
            UnlockNotifications.toast(player, gained, true);
            PartyRefreshPacket.send(player);
        }
    }

    private static Map<UUID, Set<UUID>> copyMembership(Map<UUID, Set<UUID>> parties) {
        Map<UUID, Set<UUID>> copy = new LinkedHashMap<>();
        parties.forEach((party, members) -> copy.put(party, Set.copyOf(members)));
        return Collections.unmodifiableMap(copy);
    }
}
