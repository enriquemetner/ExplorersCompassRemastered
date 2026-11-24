package io.github.enriquemetner.explorerscompass.compat.ftb;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import io.github.enriquemetner.explorerscompass.compat.ftb.FtbPreviewDelivery.Kind;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Loaded only on clients with FTB Quests installed. */
public final class FtbQuestClientIntegration {
    private static final FtbPreviewDelivery<ClientQuestFile> previews = new FtbPreviewDelivery<>();

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
                return new FtbQuestNavigation.QuestInfo(quest.getMutableTitle(),
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> previews.clear());
        registerPreviewReceiver(FtbVisitStructuresTask.PREVIEW_PACKET_ID, Kind.TASK);
        registerPreviewReceiver(FtbQuestIntegration.PREVIEW_PACKET_ID, Kind.REWARD);
    }

    private static void registerPreviewReceiver(Identifier channel, Kind kind) {
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, sender) -> {
            FtbPreviewPacket packet = FtbPreviewPacket.read(buf);
            client.execute(() -> {
                ClientQuestFile file = ClientQuestFile.INSTANCE;
                previews.receive(file, kind, packet, update -> deliver(file, kind, update));
            });
        });
    }

    private static boolean deliver(ClientQuestFile file, Kind kind, FtbPreviewPacket packet) {
        var object = file.getBase(packet.objectId());
        if (kind == Kind.TASK && object instanceof FtbVisitStructuresTask task) {
            task.setPreview(packet.preview());
        } else if (kind == Kind.REWARD && object instanceof FtbQuestIntegration.UnlockStructuresReward reward) {
            reward.setPreview(packet.preview());
        } else {
            return false;
        }
        // FTB's visible buttons cache titles and need to be rebuilt after an edit.
        object.editedFromGUI();
        return true;
    }

    private static Quest quest(long questId) {
        ClientQuestFile file = ClientQuestFile.INSTANCE;
        if (file == null) return null;
        QuestObject object = file.get(questId);
        return object instanceof Quest quest ? quest : null;
    }

    static void applyPendingPreview(FtbQuestIntegration.UnlockStructuresReward reward) {
        if (!(reward.getQuestFile() instanceof ClientQuestFile file)) return;
        FtbRewardPreview preview = previews.take(file, Kind.REWARD, reward.getId());
        if (preview != null) reward.setPreview(preview);
        // FTB refreshes its ID map and visible buttons after onCreated returns.
    }

    static void applyPendingTaskPreview(FtbVisitStructuresTask task) {
        if (!(task.getQuestFile() instanceof ClientQuestFile file)) return;
        FtbRewardPreview preview = previews.take(file, Kind.TASK, task.getId());
        if (preview != null) task.setPreview(preview);
    }
}
