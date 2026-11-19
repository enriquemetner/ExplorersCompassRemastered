package io.github.enriquemetner.explorerscompass.compat.ftb;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.gui.CompassTextures;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.unlocking.VisitTaskMatch;
import io.github.enriquemetner.explorerscompass.util.VisitedStructures;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** FTB boolean task backed by one player's persistent structure visit history. */
public final class FtbVisitStructuresTask extends AbstractBooleanTask {
    public static final Identifier PREVIEW_PACKET_ID = new Identifier(ExplorersCompass.MODID, "ftb_visit_task_preview");
    private enum Match { ANY, ALL }
    private static final NameMap<Match> MATCH_NAMES = NameMap.of(Match.ANY, Match.values())
            .id(value -> value.name().toLowerCase(Locale.ROOT))
            .name(value -> Text.translatable("ftb_task.explorerscompass.match_" + value.name().toLowerCase(Locale.ROOT)))
            .create();
    private static TaskType type;

    private List<String> structures = List.of();
    private List<String> structureSets = List.of();
    private Match match = Match.ANY;
    private FtbRewardPreview preview = FtbRewardPreview.EMPTY;
    private MinecraftServer cachedServer;
    private Set<Identifier> cachedTargets;

    static void register() {
        type = TaskTypes.register(new Identifier(ExplorersCompass.MODID, "visit_structures"),
                FtbVisitStructuresTask::new, () -> Icon.getIcon(CompassTextures.stillIcon()));
        type.setDisplayName(Text.translatable("ftb_task.explorerscompass.visit_structures"));
    }

    public FtbVisitStructuresTask(long id, Quest quest) { super(id, quest); }
    @Override public TaskType getType() { return type; }
    @Override public Text getAltTitle() {
        return Text.translatable(preview.totalCount() == 1
                ? "ftb_task.explorerscompass.visit_structure" : "ftb_task.explorerscompass.visit_structures");
    }
    @Override public int autoSubmitOnPlayerTick() { return 5; }

    @Override public boolean canSubmit(TeamData team, ServerPlayerEntity player) {
        Set<Identifier> targets = resolve(player.getServer());
        VisitedStructures visits = VisitedStructures.get(player.getServer());
        return VisitTaskMatch.satisfied(targets, match == Match.ALL,
                id -> visits.hasVisitedType(player.getUuid(), id));
    }

    @Override public void writeData(NbtCompound nbt) {
        super.writeData(nbt);
        NbtList values = new NbtList();
        structures.forEach(value -> values.add(NbtString.of(value)));
        nbt.put("structures", values);
        NbtList sets = new NbtList();
        structureSets.forEach(value -> sets.add(NbtString.of(value)));
        nbt.put("structure_sets", sets);
        nbt.putString("match", match.name().toLowerCase(Locale.ROOT));
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
        match = "all".equalsIgnoreCase(nbt.getString("match")) ? Match.ALL : Match.ANY;
        invalidateTargets();
        if (getQuestFile() instanceof ServerQuestFile) syncPreview();
        else FtbQuestClientIntegration.applyPendingTaskPreview(this);
    }

    @Override public void writeNetData(PacketByteBuf buf) {
        super.writeNetData(buf);
        buf.writeVarInt(structures.size());
        structures.forEach(value -> buf.writeString(value));
        buf.writeVarInt(structureSets.size());
        structureSets.forEach(value -> buf.writeString(value));
        buf.writeEnumConstant(match);
        currentPreview().write(buf);
    }

    @Override public void readNetData(PacketByteBuf buf) {
        super.readNetData(buf);
        int count = buf.readVarInt();
        if (count < 0 || count > 1024) throw new IllegalArgumentException("Invalid FTB visit task size: " + count);
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(buf.readString(32767));
        structures = List.copyOf(result);
        int setCount = buf.readVarInt();
        if (setCount < 0 || setCount > 1024) throw new IllegalArgumentException("Invalid FTB structure set list size: " + setCount);
        List<String> setResult = new ArrayList<>(setCount);
        for (int i = 0; i < setCount; i++) setResult.add(buf.readString(32767));
        structureSets = List.copyOf(setResult);
        match = buf.readEnumConstant(Match.class);
        setPreview(FtbRewardPreview.read(buf));
        invalidateTargets();
    }

    @Override public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        group.addList("structures", structures, new StringConfig(), values -> {
                    structures = List.copyOf(values);
                    invalidateTargets();
                }, "")
                .setNameKey("ftb_task.explorerscompass.structures");
        group.addList("structure_sets", structureSets, new StringConfig(), values -> {
                    structureSets = List.copyOf(values);
                    invalidateTargets();
                }, "")
                .setNameKey("ftb_task.explorerscompass.structure_sets");
        group.addEnum("match", match, value -> match = value, MATCH_NAMES)
                .setNameKey("ftb_task.explorerscompass.match");
    }

    @Override public void editedFromGUIOnServer() {
        super.editedFromGUIOnServer();
        syncPreview();
    }

    @Override public void addMouseOverText(TooltipList tooltip, TeamData team) {
        if (!Widget.isKeyDown(GLFW.GLFW_KEY_LEFT_ALT)) {
            tooltip.add(Text.translatable("ftb_reward.explorerscompass.hold_alt").formatted(Formatting.DARK_GRAY));
            return;
        }
        if (preview.totalCount() == 0) {
            tooltip.add(Text.translatable("ftb_reward.explorerscompass.no_matches").formatted(Formatting.GRAY));
            return;
        }
        if (preview.totalCount() > 1) {
            tooltip.add(Text.translatable(match == Match.ALL
                    ? "ftb_task.explorerscompass.visit_all" : "ftb_task.explorerscompass.visit_any",
                    Text.translatable(match == Match.ALL
                            ? "ftb_task.explorerscompass.all" : "ftb_task.explorerscompass.any")
                            .formatted(Formatting.UNDERLINE)).formatted(Formatting.GRAY));
        }
        FtbStructureTooltip.addNames(tooltip, preview.structures());
    }

    void setPreview(FtbRewardPreview updated) {
        preview = updated;
        clearCachedData();
    }

    private FtbRewardPreview currentPreview() {
        return getQuestFile() instanceof ServerQuestFile file ? FtbRewardPreview.of(resolve(file.server)) : preview;
    }

    private void syncPreview() {
        if (!(getQuestFile() instanceof ServerQuestFile file) || file.isLoading()) return;
        setPreview(currentPreview());
        for (ServerPlayerEntity player : file.server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, PREVIEW_PACKET_ID)) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeLong(getId());
            preview.write(buf);
            ServerPlayNetworking.send(player, PREVIEW_PACKET_ID, buf);
        }
    }

    private Set<Identifier> resolve(MinecraftServer server) {
        if (server == cachedServer && cachedTargets != null) return cachedTargets;
        cachedServer = server;
        try {
            cachedTargets = UnlockingRules.resolveRewardTargets(server,
                    structures, structureSets);
        } catch (IllegalArgumentException e) {
            ExplorersCompass.LOGGER.warn("Invalid structure selectors in FTB visit task {}: {}", getCodeString(), e.getMessage());
            cachedTargets = Set.of();
        }
        return cachedTargets;
    }

    private void invalidateTargets() {
        cachedServer = null;
        cachedTargets = null;
        clearCachedData();
    }
}
