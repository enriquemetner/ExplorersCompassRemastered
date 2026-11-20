package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Server-side reconciliation of saved unlocks after definition changes. */
public final class UnlockReloads {
    private static boolean initialized;
    private static boolean validDefinitions;
    private static final Set<UUID> joined = new HashSet<>();
    private UnlockReloads() {}

    public static void clear() {
        initialized = false;
        validDefinitions = false;
        joined.clear();
        FtbStructureRewards.clear();
    }

    public static boolean ready(UUID player) { return joined.contains(player); }

    public static void left(UUID player) { joined.remove(player); }

    public static void tick(MinecraftServer server) {
        if (!initialized) {
            if (!UnlockingRules.loaded()) return;
            var ftb = FtbStructureRewards.capture(server);
            if (ftb == null) return;
            var rules = UnlockingRules.get();
            var current = ftb.valid() ? rules.definitions(ftb.rewards()) : null;
            validDefinitions = current != null;
            if (!validDefinitions && ExplorersCompassConfig.relockOnReload) {
                String source = ftb.valid() ? "unlocking_rules.json" : "FTB structure rewards or tasks";
                ExplorersCompass.LOGGER.warn(
                        "Skipped startup relock because {} contains invalid requirements. The last valid baseline was kept.", source);
            }
            prepareStartup(UnlockedStructures.get(server), current, ExplorersCompassConfig.relockOnReload,
                    rules.prerequisiteGates());
            initialized = true;
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
            reconciled.forEach(ExplorersCompass.EXPLORERS_COMPASS_ITEM::revalidateAccess);
        }
    }

    static void prepareStartup(UnlockedStructures state, UnlockDefinitions current, boolean relock,
                               Map<Identifier, UnlockPrerequisites.Gate> gates) {
        if (current == null) return;
        var previous = state.definitions();
        if (relock && previous != null && !current.equals(previous)) {
            Set<String> savedIds = current.defaultMode().equals(previous.defaultMode()) ? Set.of() : state.savedStructureIds();
            state.relock(current.changedStructures(previous, savedIds), gates);
        }
        state.saveDefinitions(current);
    }

    private static void reconcile(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        UUID id = player.getUuid();
        ExplorersCompass.EXPLORERS_COMPASS_ITEM.cancelSearch(player, -1);
        int added = reconcileUnlocks(state, id, validDefinitions,
                dependent -> UnlockingRules.get().evaluateSilently(player, dependent));
        UnlockNotifications.newlyAdded(player, added);
    }

    static int reconcileUnlocks(UnlockedStructures state, UUID id, boolean valid,
                               Consumer<Set<Identifier>> evaluate) {
        if (!valid && state.hasPendingRelock(id)) return 0;
        Set<Identifier> before = state.getStructures(id);
        var pending = state.takePendingRelock(id);
        Set<Identifier> latestBaseline = new HashSet<>(before);
        latestBaseline.addAll(pending.direct());
        latestBaseline.addAll(pending.dependent());
        state.beginUnlockEvent(id, latestBaseline);
        try {
            evaluate.accept(pending.dependent());
        } finally { state.finishUnlockEvent(id); }
        Set<Identifier> added = new HashSet<>(state.getStructures(id));
        added.removeAll(before);
        return added.size();
    }
}
