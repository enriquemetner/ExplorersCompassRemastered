package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.network.PartyRefreshPacket;
import com.chaosthedude.explorerscompass.network.UnlockToastPacket;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Optional party boundary and batched, nonrecursive propagation of saved unlocks. */
public final class PartySharing {
    public interface Provider { List<Set<UUID>> parties(MinecraftServer server); }
    private static Provider provider;
    private static boolean dirty = true;
    private static boolean merging;
    private PartySharing() {}
    public static void register(Provider source) { provider = source; }
    public static boolean enabled() { return provider != null && ExplorersCompassConfig.opacPartySharing; }
    public static void changed() { dirty = true; }
    public static void clear() { dirty = true; merging = false; }

    public static Set<UUID> members(MinecraftServer server, UUID player) {
        if (enabled()) for (Set<UUID> party : provider.parties(server)) if (party.contains(player)) return Set.copyOf(party);
        return Set.of(player);
    }

    static Map<UUID, Set<Identifier>> merge(UnlockedStructures state, Set<UUID> members, Predicate<Identifier> allowed) {
        Set<Identifier> union = new HashSet<>();
        members.forEach(member -> union.addAll(state.getStructures(member)));
        union.removeIf(allowed.negate());
        Map<UUID, Set<Identifier>> additions = new HashMap<>();
        for (UUID member : members) {
            Set<Identifier> added = new HashSet<>(union);
            added.removeAll(state.getStructures(member));
            if (!added.isEmpty()) {
                state.addShared(member, added);
                additions.put(member, added);
            }
        }
        return additions;
    }

    public static void tick(MinecraftServer server) {
        if (enabled() && (dirty || server.getTicks() % 20 == 0)) synchronize(server);
    }

    public static void synchronize(MinecraftServer server) {
        synchronize(server, true);
    }

    public static void synchronize(MinecraftServer server, boolean refresh) {
        if (!enabled() || merging) return;
        merging = true;
        try {
            dirty = false;
            UnlockedStructures state = UnlockedStructures.get(server);
            state.preparePartyRelocks();
            var known = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
            for (Set<UUID> party : provider.parties(server)) {
                Map<UUID, Set<Identifier>> additions = merge(state, party,
                        id -> known.containsId(id) && !StructureUtils.structureIdIsBlacklisted(id));
                additions.forEach((member, ids) -> {
                    Set<Identifier> notify = new HashSet<>(ids);
                    notify.removeAll(state.relockBaseline(member));
                    var connected = server.getPlayerManager().getPlayer(member);
                    var online = connected != null && connected.networkHandler.isConnectionOpen() ? connected : null;
                    if (state.notificationsEnabled(member)) {
                        if (online == null) state.queuePartyNotification(member, notify);
                        else UnlockToastPacket.send(online, notify.size(), true);
                    }
                    if (refresh && online != null) PartyRefreshPacket.send(online);
                });
            }
            // Offline baselines survive until login so restored IDs cannot generate duplicate toasts.
            for (var player : server.getPlayerManager().getPlayerList())
                if (UnlockReloads.ready(player.getUuid())) state.finishPartyRelock(player.getUuid());
        } finally { merging = false; }
    }
}
