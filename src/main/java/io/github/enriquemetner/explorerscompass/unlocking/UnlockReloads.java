package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side reconciliation of saved unlocks after definition changes. */
public final class UnlockReloads {
    private static String observedDefinition;
    private static final Set<UUID> joined = new HashSet<>();
    private UnlockReloads() {}

    public static void clear() {
        observedDefinition = null;
        joined.clear();
        FtbStructureRewards.clear();
    }

    public static boolean ready(UUID player) { return joined.contains(player); }

    public static void left(UUID player) { joined.remove(player); }

    private static String definition(MinecraftServer server) {
        if (UnlockingRules.definition().isEmpty()) return null;
        String ftb = FtbStructureRewards.capture(server);
        if (ftb == null) return null;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (UnlockingRules.definition() + "\n" + ftb).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static void tick(MinecraftServer server) {
        if (observedDefinition == null) {
            String current = definition(server);
            if (current == null) return;
            prepareStartup(UnlockedStructures.get(server), current, ExplorersCompassConfig.relockOnReload);
            observedDefinition = current;
        }
        var reconciled = new java.util.ArrayList<ServerPlayerEntity>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (joined.add(player.getUuid())) {
                reconcile(player);
                reconciled.add(player);
            }
        }
        if (!reconciled.isEmpty()) {
            PartySharing.synchronize(server);
            reconciled.forEach(io.github.enriquemetner.explorerscompass.ExplorersCompass.EXPLORERS_COMPASS_ITEM::revalidateAccess);
        }
    }

    static void prepareStartup(UnlockedStructures state, String definition, boolean relock) {
        if (state.updateDefinitionHistory(definition) && relock) {
            state.queueRelock(state.knownPlayers());
        }
    }

    private static void reconcile(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        UUID id = player.getUuid();
        io.github.enriquemetner.explorerscompass.ExplorersCompass.EXPLORERS_COMPASS_ITEM.cancelSearch(player, -1);
        Set<net.minecraft.util.Identifier> before = state.getStructures(id);
        boolean relocked = state.takePendingRelock(id);
        state.beginUnlockEvent(id, before);
        try {
            if (relocked) state.clearStructures(id);
            UnlockingRules.get().evaluateSilently(player);
        } finally { state.finishUnlockEvent(id); }
        Set<net.minecraft.util.Identifier> added = new HashSet<>(state.getStructures(id));
        added.removeAll(before);
        UnlockNotifications.newlyAdded(player, added.size());
    }
}
