package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.network.unlocking.UnlockToastPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/** Sends one notification per newly earned batch after the first compass. */
public final class UnlockNotifications {
    private UnlockNotifications() {}

    public static void newlyAdded(ServerPlayerEntity player, int count) {
        if (count > 0) io.github.enriquemetner.explorerscompass.network.unlocking.PartyRefreshPacket.send(player);
        toast(player, count, false);
    }

    static void toast(ServerPlayerEntity player, int count, boolean shared) {
        if (count > 0 && UnlockedStructures.get(player.getServer()).notificationsEnabled(player.getUuid())
                && UnlockingRules.get().shouldShowUnlockToast(player)) {
            UnlockToastPacket.send(player, count, shared);
        }
    }

    public static void compassAdvancementCompleted(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        if (state.notificationsEnabled(player.getUuid())) {
            // A revoked and re-earned compass is another opportunity to restore
            // currently satisfied defaults and rules, but only new IDs get a toast.
            UnlockingRules.get().evaluateOnOpen(player);
            return;
        }
        UnlockingRules rules = UnlockingRules.get();
        rules.evaluateOnOpen(player);
        PartySharing.synchronize(player.getServer());
        if (state.enableNotifications(player.getUuid())) {
            toast(player, state.getStructures(player.getUuid()).size(), false);
        }
    }

    public static void deliverPending(ServerPlayerEntity player) {
        UnlockedStructures state = UnlockedStructures.get(player.getServer());
        if (!player.networkHandler.isConnectionOpen() || !state.notificationsEnabled(player.getUuid())) return;
        toast(player, state.takePendingNotificationCount(player.getUuid()), false);
    }
}
