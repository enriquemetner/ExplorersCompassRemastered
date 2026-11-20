package io.github.enriquemetner.explorerscompass.compat.ftb;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;

/** Captures quest completion and reward claim settings without cosmetic quest data. */
final class FtbRequirementFingerprint {
    private static final Set<String> QUEST_SETTINGS = Set.of("dependency_requirement", "max_completable_dependents");
    private static final Set<String> COSMETIC_TASK_FIELDS = Set.of("title", "icon", "tags", "disable_toast");

    private final MinecraftServer server;
    private final Map<Quest, String> questSignatures = new HashMap<>();
    private final Map<QuestObject, String> objectSignatures = new HashMap<>();

    FtbRequirementFingerprint(MinecraftServer server) {
        this.server = server;
    }

    String reward(Quest quest, Reward reward) {
        return digest(questSignatures.computeIfAbsent(quest, this::questGraph)
                + ":" + reward.isTeamReward() + ":" + reward.getAutoClaimType() + ":" + reward.getExcludeFromClaimAll());
    }

    private String questGraph(Quest root) {
        var pending = new ArrayDeque<QuestObject>();
        var signatures = new TreeMap<Long, String>();
        pending.add(root);
        while (!pending.isEmpty()) {
            QuestObject object = pending.removeFirst();
            if (signatures.containsKey(object.getId())) continue;
            signatures.put(object.getId(), objectSignatures.computeIfAbsent(object, this::signature));
            if (object instanceof Quest quest) {
                pending.addAll(quest.getTasks());
                quest.streamDependencies().forEach(pending::add);
            } else if (object instanceof Chapter chapter) {
                pending.addAll(chapter.getQuests());
            } else if (object instanceof Task task) {
                pending.add(task.getQuest());
            }
        }
        return digest(root.getId() + ":" + signatures);
    }

    private String signature(QuestObject object) {
        if (object instanceof Quest quest) {
            NbtCompound data = new NbtCompound();
            quest.writeData(data);
            var settings = new NbtCompound();
            for (String key : QUEST_SETTINGS) {
                if (data.contains(key)) settings.put(key, data.get(key));
            }
            var tasks = quest.getTasksAsList().stream().map(Task::getId);
            if (!quest.getRequireSequentialTasks()) tasks = tasks.sorted();
            return "quest:" + canonical(settings) + ":" + quest.getMinRequiredDependencies() + ":"
                    + quest.getProgressionMode() + ":" + quest.getRequireSequentialTasks() + ":" + quest.isOptional()
                    + ":" + quest.canBeRepeated() + ":" + quest.getRepeatCooldown() + ":" + quest.ignoreRewardBlocking()
                    + ":" + tasks.toList() + ":" + quest.streamDependencies().map(QuestObject::getId).sorted().toList();
        }
        if (object instanceof Chapter chapter) {
            return "chapter:" + chapter.getQuests().stream().map(Quest::getId).sorted().toList();
        }
        if (object instanceof Task task) {
            NbtCompound data = new NbtCompound();
            task.writeData(data);
            COSMETIC_TASK_FIELDS.forEach(data::remove);
            if (task instanceof FtbVisitStructuresTask visit) visit.normalizeDefinition(server, data);
            return "task:" + task.getType().getTypeId() + ":" + canonical(data);
        }
        return object.getClass().getName();
    }

    private static String canonical(NbtElement value) {
        if (value instanceof NbtCompound compound) {
            return compound.getKeys().stream().sorted()
                    .map(key -> NbtString.of(key) + ":" + canonical(compound.get(key)))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof NbtList list) {
            return list.stream().map(FtbRequirementFingerprint::canonical).collect(Collectors.joining(",", "[", "]"));
        }
        return value.toString();
    }

    private static String digest(String definition) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(definition.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
