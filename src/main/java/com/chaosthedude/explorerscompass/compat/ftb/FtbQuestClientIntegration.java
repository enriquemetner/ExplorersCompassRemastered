package com.chaosthedude.explorerscompass.compat.ftb;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Loaded only on clients with FTB Quests installed. */
public final class FtbQuestClientIntegration {
    private static final Map<Long, FtbRewardPreview> pendingPreviews = new HashMap<>();
    private static ClientQuestFile pendingFile;

    private FtbQuestClientIntegration() {}

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingPreviews.clear();
            pendingFile = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(FtbQuestIntegration.PREVIEW_PACKET_ID, (client, handler, buf, sender) -> {
            long rewardId = buf.readLong();
            FtbRewardPreview preview = FtbRewardPreview.read(buf);
            client.execute(() -> {
                ClientQuestFile file = ClientQuestFile.INSTANCE;
                if (file == null) return;
                useFile(file);
                if (file.getBase(rewardId) instanceof FtbQuestIntegration.UnlockStructuresReward reward) {
                    reward.setPreview(preview);
                    // FTB buttons cache their titles too; rebuild the visible reward buttons.
                    reward.editedFromGUI();
                } else {
                    // The server sends this before FTB's create response introduces the reward.
                    pendingPreviews.put(rewardId, preview);
                }
            });
        });
    }

    static void applyPendingPreview(FtbQuestIntegration.UnlockStructuresReward reward) {
        if (!(reward.getQuestFile() instanceof ClientQuestFile file)) return;
        useFile(file);
        FtbRewardPreview preview = pendingPreviews.remove(reward.getId());
        if (preview != null) reward.setPreview(preview);
        // FTB refreshes its ID map and visible buttons immediately after onCreated returns.
    }

    private static void useFile(ClientQuestFile file) {
        if (pendingFile != file) {
            pendingPreviews.clear();
            pendingFile = file;
        }
    }
}
