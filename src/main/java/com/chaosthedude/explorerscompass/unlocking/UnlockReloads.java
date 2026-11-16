package com.chaosthedude.explorerscompass.unlocking;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.network.RulesChangedPacket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Server-side reconciliation of saved unlocks after definition changes. */
public final class UnlockReloads {
    private static String observedDefinition;
    private static final Set<UUID> joined = new HashSet<>();
    private static final Set<UUID> recentEditors = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean definitionDirty = true;

    private UnlockReloads() {}

    public static void clear() {
        observedDefinition = null;
        joined.clear();
        recentEditors.clear();
        definitionDirty = true;
    }

    public static boolean ready(UUID player) { return joined.contains(player); }

    public static void left(UUID player) { joined.remove(player); }

    public static void editedBy(UUID player) { recentEditors.add(player); }

    public static void definitionChanged() { definitionDirty = true; }

    private static String definition() {
        if (UnlockingRules.definition().isEmpty()) return null;
        String ftb = FtbStructureRewards.definition();
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
        if (!definitionDirty && server.getTicks() % 5 == 0) recentEditors.clear();
        if (definitionDirty && server.getTicks() % 5 == 0) {
            String current = definition();
            if (current != null) {
                definitionDirty = false;
            }
            if (current != null && !current.equals(observedDefinition)) {
                boolean startup = observedDefinition == null;
                observedDefinition = current;
                UnlockedStructures state = UnlockedStructures.get(server);
                if (startup) {
                    prepareStartup(state, current, ExplorersCompassConfig.relockOnReload);
                } else {
                    state.updateDefinitionHistory(current);
                    updatePlayers(server, Set.copyOf(recentEditors), true);
                }
            }
            recentEditors.clear();
        }
        if (observedDefinition == null) return;
        var reconciled = new java.util.ArrayList<ServerPlayerEntity>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (joined.add(player.getUuid())) {
                reconcile(player);
                reconciled.add(player);
            }
        }
        if (!reconciled.isEmpty()) {
            PartySharing.synchronize(server);
            reconciled.forEach(com.chaosthedude.explorerscompass.ExplorersCompass.EXPLORERS_COMPASS_ITEM::revalidateAccess);
        }
    }

    static void prepareStartup(UnlockedStructures state, String definition, boolean relock) {
        if (state.updateDefinitionHistory(definition)) {
            state.queueReload(state.knownPlayers(), relock, true);
        }
    }

    /** Called after a reload activates rules or generated defaults. */
    public static void commandReload(MinecraftServer server) {
        String current = definition();
        if (current != null) {
            observedDefinition = current;
            UnlockedStructures.get(server).updateDefinitionHistory(current);
            definitionDirty = false;
        }
        updatePlayers(server, Set.of(), true);
    }

    private static void updatePlayers(MinecraftServer server, Set<UUID> editors, boolean notice) {
        UnlockedStructures state = UnlockedStructures.get(server);
        state.queueReload(state.knownPlayers(), ExplorersCompassConfig.relockOnReload, notice);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Newly connected players may not have saved state yet.
            if (ExplorersCompassConfig.relockOnReload || notice)
                state.queueReload(Set.of(player.getUuid()), ExplorersCompassConfig.relockOnReload,
                        notice && !editors.contains(player.getUuid()));
            if (editors.contains(player.getUuid())) state.takePendingReloadNotice(player.getUuid());
            reconcile(player);
            joined.add(player.getUuid());
        }
        PartySharing.synchronize(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            com.chaosthedude.explorerscompass.ExplorersCompass.EXPLORERS_COMPASS_ITEM.revalidateAccess(player);
            RulesChangedPacket.send(player);
        }
    }

    private static void reconcile(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        UUID id = player.getUuid();
        com.chaosthedude.explorerscompass.ExplorersCompass.EXPLORERS_COMPASS_ITEM.cancelSearch(player, -1);
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
        if (!state.notificationsEnabled(id)) state.takePendingReloadNotice(id);
    }
}
