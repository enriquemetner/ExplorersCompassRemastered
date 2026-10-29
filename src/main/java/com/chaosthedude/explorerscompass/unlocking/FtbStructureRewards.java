package com.chaosthedude.explorerscompass.unlocking;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Optional FTB Quests boundary; this class has no FTB references at runtime. */
public final class FtbStructureRewards {
    public record Quest(long id, Text title) {}
    public record Snapshot(Map<Identifier, List<Quest>> quests, Map<Long, Set<Identifier>> rewardTargets) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
        public boolean covers(Identifier structure) { return quests.containsKey(structure); }
        public Set<Identifier> targets(long rewardId) { return rewardTargets.getOrDefault(rewardId, Set.of()); }
    }
    public interface Provider { Snapshot snapshot(ServerPlayerEntity player); }

    private static Provider provider;

    private FtbStructureRewards() {}

    public static void register(Provider source) { provider = source; }
    public static Snapshot snapshot(ServerPlayerEntity player) {
        return provider == null ? Snapshot.EMPTY : provider.snapshot(player);
    }

    public static void claimed(ServerPlayerEntity player, long rewardId, Set<Identifier> targets) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        state.recordFtbClaim(player.getUuid(), rewardId);
        UnlockNotifications.newlyAdded(player, state.add(player.getUuid(), targets));
    }
}
