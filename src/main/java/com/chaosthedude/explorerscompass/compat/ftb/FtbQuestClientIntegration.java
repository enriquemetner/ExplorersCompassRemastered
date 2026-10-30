package com.chaosthedude.explorerscompass.compat.ftb;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;

/** Loaded only on clients with FTB Quests installed. */
public final class FtbQuestClientIntegration {
    private static final Map<Long, FtbRewardPreview> pendingPreviews = new HashMap<>();
    private static final Map<Long, FtbRewardPreview> pendingTaskPreviews = new HashMap<>();
    private static ClientQuestFile pendingFile;

    private FtbQuestClientIntegration() {}

    public static void register() {
        FtbQuestNavigation.register(new FtbQuestNavigation.Provider() {
            @Override public FtbQuestNavigation.QuestInfo info(long questId) {
                Quest quest = quest(questId);
                ClientQuestFile file = ClientQuestFile.INSTANCE;
                if (quest == null || file == null || file.selfTeamData == null) return null;
                TeamData team = file.selfTeamData;
                boolean completed = team.isCompleted(quest);
                NbtCompound settings = new NbtCompound();
                quest.writeData(settings);
                boolean visible = quest.isVisible(team) && quest.getChapter().isVisible(team);
                return new FtbQuestNavigation.QuestInfo(quest.getTitle(),
                        visible && (file.canEdit() || !quest.hideDetailsUntilStartable() || team.canStartTasks(quest)),
                        quest.getHideTextUntilComplete().get(false) && !completed,
                        !visible && settings.getBoolean("invisible") && !completed);
            }

            @Override public boolean openQuest(long questId) {
                Quest quest = quest(questId);
                return quest != null && ClientQuestFile.openGui(quest, true) != null;
            }

            @Override public boolean openChapter(long questId) {
                Quest quest = quest(questId);
                if (quest == null) return false;
                QuestScreen screen = ClientQuestFile.openGui();
                if (screen != null) {
                    screen.closeQuest();
                    screen.open(quest.getChapter(), false);
                    return true;
                }
                return false;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingPreviews.clear();
            pendingTaskPreviews.clear();
            pendingFile = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(FtbVisitStructuresTask.PREVIEW_PACKET_ID, (client, handler, buf, sender) -> {
            long taskId = buf.readLong();
            FtbRewardPreview preview = FtbRewardPreview.read(buf);
            client.execute(() -> {
                ClientQuestFile file = ClientQuestFile.INSTANCE;
                if (file == null) return;
                useFile(file);
                if (file.getBase(taskId) instanceof FtbVisitStructuresTask task) {
                    task.setPreview(preview);
                    task.editedFromGUI();
                } else {
                    pendingTaskPreviews.put(taskId, preview);
                }
            });
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

    private static Quest quest(long questId) {
        ClientQuestFile file = ClientQuestFile.INSTANCE;
        if (file == null) return null;
        QuestObject object = file.get(questId);
        return object instanceof Quest quest ? quest : null;
    }

    static void applyPendingPreview(FtbQuestIntegration.UnlockStructuresReward reward) {
        if (!(reward.getQuestFile() instanceof ClientQuestFile file)) return;
        useFile(file);
        FtbRewardPreview preview = pendingPreviews.remove(reward.getId());
        if (preview != null) reward.setPreview(preview);
        // FTB refreshes its ID map and visible buttons immediately after onCreated returns.
    }

    static void applyPendingTaskPreview(FtbVisitStructuresTask task) {
        if (!(task.getQuestFile() instanceof ClientQuestFile file)) return;
        useFile(file);
        FtbRewardPreview preview = pendingTaskPreviews.remove(task.getId());
        if (preview != null) task.setPreview(preview);
    }

    private static void useFile(ClientQuestFile file) {
        if (pendingFile != file) {
            pendingPreviews.clear();
            pendingTaskPreviews.clear();
            pendingFile = file;
        }
    }
}
