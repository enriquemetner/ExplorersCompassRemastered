package com.chaosthedude.explorerscompass.compat.ftb;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.CompassTextures;
import com.chaosthedude.explorerscompass.unlocking.FtbStructureRewards;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Loaded only when FTB Quests is installed. */
public final class FtbQuestIntegration {
    public static final Identifier PREVIEW_PACKET_ID = new Identifier(ExplorersCompass.MODID, "ftb_reward_preview");
    private static RewardType type;

    private FtbQuestIntegration() {}

    public static void register() {
        type = RewardTypes.register(new Identifier(ExplorersCompass.MODID, "unlock_structures"),
                UnlockStructuresReward::new, () -> Icon.getIcon(CompassTextures.stillIcon()));
        type.setDisplayName(Text.translatable("ftb_reward.explorerscompass.unlock_structures"));
        FtbVisitStructuresTask.register();
        FtbStructureRewards.register(FtbQuestIntegration::capture);
    }

    private static String definition(ServerQuestFile file) {
        java.util.SortedMap<Long, String> relevant = new java.util.TreeMap<>();
        file.forAllQuests(quest -> {
            boolean used = quest.getTasks().stream().anyMatch(task -> task instanceof FtbVisitStructuresTask)
                    || quest.getRewards().stream().anyMatch(reward -> reward instanceof UnlockStructuresReward);
            if (!used) return;
            NbtCompound data = new NbtCompound();
            quest.writeData(data);
            java.util.SortedMap<Long, String> components = new java.util.TreeMap<>();
            quest.getTasks().forEach(task -> {
                NbtCompound part = new NbtCompound();
                task.writeData(part);
                components.put(task.getId(), part.toString());
            });
            quest.getRewards().forEach(reward -> {
                NbtCompound part = new NbtCompound();
                reward.writeData(part);
                components.put(reward.getId(), part.toString());
            });
            relevant.put(quest.getId(), data + ":" + components);
        });
        return relevant.toString();
    }

    private static FtbStructureRewards.Session capture(net.minecraft.server.MinecraftServer server) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || file.isLoading() || file.server != server) return null;
        Map<Identifier, LinkedHashMap<Long, FtbStructureRewards.Quest>> quests = new LinkedHashMap<>();
        Map<Long, Set<Identifier>> targets = new LinkedHashMap<>();
        List<UnlockStructuresReward> rewards = new ArrayList<>();
        file.forAllQuests(quest -> {
            for (Reward reward : quest.getRewards()) {
                if (!(reward instanceof UnlockStructuresReward unlock)) continue;
                Set<Identifier> resolved = Set.copyOf(unlock.resolve(server));
                targets.put(unlock.getId(), resolved);
                rewards.add(unlock);
                for (Identifier id : resolved) quests.computeIfAbsent(id, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(quest.getId(), new FtbStructureRewards.Quest(quest.getId(), serverQuestTitle(quest)));
            }
        });
        Map<Identifier, List<FtbStructureRewards.Quest>> entries = new LinkedHashMap<>();
        quests.forEach((id, named) -> entries.put(id, List.copyOf(named.values())));
        var frozenQuests = Map.copyOf(entries);
        var frozenTargets = Map.copyOf(targets);
        return new FtbStructureRewards.Session(definition(file), player -> {
            TeamData team = TeamData.get(player);
            Set<Long> claimedRewardIds = new LinkedHashSet<>();
            for (UnlockStructuresReward reward : rewards) {
                if (team.isRewardClaimed(player.getUuid(), reward)) claimedRewardIds.add(reward.getId());
            }
            return new FtbStructureRewards.Snapshot(frozenQuests, frozenTargets, Set.copyOf(claimedRewardIds));
        });
    }

    // FTB's getTitle/getMutableTitle are stripped from dedicated servers.
    private static Text serverQuestTitle(Quest quest) {
        String title = quest.getRawTitle();
        if (title.isEmpty()) return Text.literal("Quest " + quest.getCodeString());
        if (title.stripLeading().startsWith("{") || title.stripLeading().startsWith("[")) {
            try {
                Text parsed = Text.Serializer.fromJson(title);
                if (parsed != null) return parsed;
            } catch (RuntimeException ignored) { }
        }
        return Text.literal(title);
    }

    public static final class UnlockStructuresReward extends Reward {
        private List<String> structures = List.of();
        private List<String> structureSets = List.of();
        private FtbRewardPreview preview = FtbRewardPreview.EMPTY;

        public UnlockStructuresReward(long id, Quest quest) { super(id, quest); }
        @Override public RewardType getType() { return type; }

        @Override public void claim(ServerPlayerEntity player, boolean notify) {
            FtbStructureRewards.claimed(player, getId());
        }

        @Override public void writeData(NbtCompound nbt) {
            super.writeData(nbt);
            NbtList values = new NbtList();
            structures.forEach(value -> values.add(NbtString.of(value)));
            nbt.put("structures", values);
            NbtList sets = new NbtList();
            structureSets.forEach(value -> sets.add(NbtString.of(value)));
            nbt.put("structure_sets", sets);
        }

        @Override public void readData(NbtCompound nbt) {
            super.readData(nbt);
            NbtList values = nbt.getList("structures", NbtElement.STRING_TYPE);
            List<String> result = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) result.add(values.getString(i));
            structures = List.copyOf(result);
            NbtList sets = nbt.getList("structure_sets", NbtElement.STRING_TYPE);
            List<String> setResult = new ArrayList<>(sets.size());
            for (int i = 0; i < sets.size(); i++) setResult.add(sets.getString(i));
            structureSets = List.copyOf(setResult);
            clearCachedData();
        }

        @Override public void writeNetData(PacketByteBuf buf) {
            super.writeNetData(buf);
            buf.writeVarInt(structures.size());
            structures.forEach(value -> buf.writeString(value));
            buf.writeVarInt(structureSets.size());
            structureSets.forEach(value -> buf.writeString(value));
            currentPreview().write(buf);
        }

        @Override public void readNetData(PacketByteBuf buf) {
            super.readNetData(buf);
            int count = buf.readVarInt();
            if (count < 0 || count > 1024) throw new IllegalArgumentException("Invalid FTB structure reward size: " + count);
            List<String> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) result.add(buf.readString(32767));
            structures = List.copyOf(result);
            int setCount = buf.readVarInt();
            if (setCount < 0 || setCount > 1024) throw new IllegalArgumentException("Invalid FTB structure set list size: " + setCount);
            List<String> setResult = new ArrayList<>(setCount);
            for (int i = 0; i < setCount; i++) setResult.add(buf.readString(32767));
            structureSets = List.copyOf(setResult);
            setPreview(FtbRewardPreview.read(buf));
        }

        void setPreview(FtbRewardPreview updated) {
            preview = updated;
            clearCachedData();
        }

        private FtbRewardPreview currentPreview() {
            return getQuestFile() instanceof ServerQuestFile file ? FtbRewardPreview.of(resolve(file.server)) : preview;
        }

        @Override public void editedFromGUIOnServer() {
            super.editedFromGUIOnServer();
            syncPreview();
        }

        @Override public void onCreated() {
            super.onCreated();
            if (getQuestFile() instanceof ServerQuestFile) syncPreview();
            else FtbQuestClientIntegration.applyPendingPreview(this);
        }

        private void syncPreview() {
            if (!(getQuestFile() instanceof ServerQuestFile file)) return;
            setPreview(currentPreview());
            // FTB's create/edit responses use readData/writeData, bypassing full-sync net data.
            // Send the derived preview separately; it is not part of the saved quest config.
            for (ServerPlayerEntity player : file.server.getPlayerManager().getPlayerList()) {
                if (!ServerPlayNetworking.canSend(player, PREVIEW_PACKET_ID)) continue;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeLong(getId());
                preview.write(buf);
                ServerPlayNetworking.send(player, PREVIEW_PACKET_ID, buf);
            }
        }

        @Override public void fillConfigGroup(ConfigGroup group) {
            super.fillConfigGroup(group);
            group.addList("structures", structures, new StringConfig(), values -> {
                        structures = List.copyOf(values);
                        clearCachedData();
                    }, "")
                    .setNameKey("ftb_reward.explorerscompass.structures");
            group.addList("structure_sets", structureSets, new StringConfig(), values -> {
                        structureSets = List.copyOf(values);
                        clearCachedData();
                    }, "")
                    .setNameKey("ftb_reward.explorerscompass.structure_sets");
        }

        @Override public Text getAltTitle() {
            int resolvedCount = preview.totalCount();
            return resolvedCount == 1 ? Text.translatable("ftb_reward.explorerscompass.unlock_structure")
                    : resolvedCount == 0 ? Text.translatable("ftb_reward.explorerscompass.unlock_structures")
                    : Text.translatable("ftb_reward.explorerscompass.unlock_count", resolvedCount);
        }

        @Override public void addMouseOverText(TooltipList tooltip) {
            if (!Widget.isKeyDown(GLFW.GLFW_KEY_LEFT_ALT)) {
                tooltip.add(Text.translatable("ftb_reward.explorerscompass.hold_alt").formatted(Formatting.DARK_GRAY));
                return;
            }
            if (preview.totalCount() == 0) {
                tooltip.add(Text.translatable("ftb_reward.explorerscompass.no_matches").formatted(Formatting.GRAY));
                return;
            }
            FtbStructureTooltip.addNames(tooltip, preview.structures());
        }

        private Set<Identifier> resolve(net.minecraft.server.MinecraftServer server) {
            try {
                return UnlockingRules.resolveRewardTargets(server, structures, structureSets);
            } catch (IllegalArgumentException e) {
                ExplorersCompass.LOGGER.warn("Invalid structure selectors in FTB reward {}: {}", getCodeString(), e.getMessage());
                return Set.of();
            }
        }

    }
}
