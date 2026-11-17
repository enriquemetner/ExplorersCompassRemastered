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
    public record Snapshot(Map<Identifier, List<Quest>> quests, Map<Long, Set<Identifier>> rewardTargets,
                           Set<Long> claimedRewardIds) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Set.of());
        public boolean covers(Identifier structure) { return quests.containsKey(structure); }
        public Set<Identifier> targets(long rewardId) { return rewardTargets.getOrDefault(rewardId, Set.of()); }
    }
    public interface Provider { Snapshot snapshot(ServerPlayerEntity player); }
    public interface Definitions { String current(); }

    private static Provider provider;
    private static Definitions definitions;

    private FtbStructureRewards() {}

    public static void register(Provider source) { provider = source; }
    public static void registerDefinitions(Definitions source) { definitions = source; }
    /** Null while an installed FTB quest book has not finished loading. */
    public static String definition() { return definitions == null ? "" : definitions.current(); }
    public static Snapshot snapshot(ServerPlayerEntity player) {
        return provider == null ? Snapshot.EMPTY : provider.snapshot(player);
    }

    public static void claimed(ServerPlayerEntity player, Set<Identifier> targets) {
        UnlockingRules rules = UnlockingRules.get();
        UnlockNotifications.newlyAdded(player, rules.evaluateClaimedReward(player, targets));
    }
}
