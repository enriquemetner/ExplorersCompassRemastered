package com.chaosthedude.explorerscompass.unlocking;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
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
    public record Session(String definition, Provider progress) {}

    private static Function<MinecraftServer, Session> loader;
    private static Session session;

    private FtbStructureRewards() {}

    public static void register(Function<MinecraftServer, Session> source) { loader = source; }

    /** Capture once, after the optional FTB quest book has loaded. */
    static String capture(MinecraftServer server) {
        if (loader == null) return "";
        if (session == null) session = loader.apply(server);
        return session == null ? null : session.definition();
    }

    static void clear() { session = null; }

    public static Snapshot snapshot(ServerPlayerEntity player) {
        return session == null ? Snapshot.EMPTY : session.progress().snapshot(player);
    }

    public static void claimed(ServerPlayerEntity player, long rewardId) {
        Set<Identifier> targets = snapshot(player).targets(rewardId);
        if (targets.isEmpty()) return;
        UnlockingRules rules = UnlockingRules.get();
        UnlockNotifications.newlyAdded(player, rules.evaluateClaimedReward(player, targets));
    }
}
