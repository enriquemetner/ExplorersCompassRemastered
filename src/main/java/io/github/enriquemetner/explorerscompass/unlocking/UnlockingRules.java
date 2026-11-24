package io.github.enriquemetner.explorerscompass.unlocking;

import com.google.gson.JsonObject;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.structure.StructureSelector;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRuleParser.DefaultMode;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRuleParser.Requirement;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRuleParser.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-owned rule definitions. Reloading replaces definitions without touching player data. */
public final class UnlockingRules {
    private static final Logger LOGGER = LogManager.getLogger("explorerscompass");
    private static UnlockingRules active = new UnlockingRules();
    private static boolean loaded;

    public enum Severity { ERROR, WARNING }
    public record Diagnostic(Severity severity, String message) {}

    private final Result parsed;

    public UnlockingRules() {
        this(Result.empty());
    }

    private UnlockingRules(Result parsed) {
        this.parsed = parsed;
    }

    static List<Diagnostic> report(Severity severity) {
        return active.diagnostics(severity);
    }

    List<Diagnostic> diagnostics(Severity severity) {
        return parsed.diagnostics().stream().filter(entry -> severity == null || severity == entry.severity()).toList();
    }

    public static UnlockingRules get() { return active; }

    interface Catalog {
        Set<Identifier> structures();
        Set<Identifier> structureSets();
        Set<Identifier> structureSet(Identifier id);
        boolean advancementExists(Identifier id);
        default Identifier advancementTab(Identifier id) { return null; }
        default boolean advancementHasDisplay(Identifier id) { return true; }
        Text advancementTitle(Identifier id);
        Text advancementDescription(Identifier id);
        Formatting advancementColor(Identifier id);
    }

    interface Progress {
        boolean visited(Identifier id);
        boolean completed(Identifier id);
    }

    private static Catalog catalog(MinecraftServer server) {
        return new Catalog() {
            public Set<Identifier> structures() { return server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds(); }
            public Set<Identifier> structureSets() { return server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).getIds(); }
            public Set<Identifier> structureSet(Identifier id) { return setMembers(server, id); }
            public boolean advancementExists(Identifier id) { return server.getAdvancementLoader().get(id) != null; }
            public Identifier advancementTab(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                return advancement == null ? null : advancement.getRoot().getId();
            }
            public boolean advancementHasDisplay(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                return advancement != null && advancement.getDisplay() != null;
            }
            public Text advancementTitle(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                return advancement != null && advancement.getDisplay() != null ? advancement.getDisplay().getTitle() : Text.literal(readable(id));
            }
            public Text advancementDescription(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                return advancement != null && advancement.getDisplay() != null ? advancement.getDisplay().getDescription() : null;
            }
            public Formatting advancementColor(Identifier id) {
                Advancement advancement = server.getAdvancementLoader().get(id);
                AdvancementFrame frame = advancement != null && advancement.getDisplay() != null ? advancement.getDisplay().getFrame() : AdvancementFrame.TASK;
                return frame.getTitleFormat();
            }
        };
    }

    private static Progress progress(ServerPlayerEntity player) {
        return new Progress() {
            public boolean visited(Identifier id) { return VisitedStructures.get(player.getServer()).hasVisitedType(player.getUuid(), id); }
            public boolean completed(Identifier id) {
                Advancement advancement = player.getServer().getAdvancementLoader().get(id);
                return advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone();
            }
        };
    }

    public static void clear() { active = new UnlockingRules(); loaded = false; }

    static boolean loaded() { return loaded; }

    UnlockDefinitions definitions(List<FtbStructureRewards.RewardDefinition> rewards) {
        if (parsed.diagnostics().stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)) return null;
        var rewardDefinitions = rewards.stream().map(reward -> new UnlockDefinitions.Rule(Long.toString(reward.id()),
                UnlockDefinitions.ids(reward.targets()), reward.requirement()))
                .sorted(java.util.Comparator.comparing(UnlockDefinitions.Rule::id)).toList();
        return new UnlockDefinitions(parsed.defaultMode().name(), parsed.visitSetDefinitions(),
                parsed.requirementDefinitions(), parsed.prerequisiteDefinitions(), rewardDefinitions);
    }

    Map<Identifier, UnlockPrerequisites.Gate> prerequisiteGates() { return parsed.prerequisites(); }

    public static io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section reload(MinecraftServer server) {
        Path file = ExplorersCompassConfig.serverConfigFile(server, ExplorersCompassConfig.UNLOCKING_RULES);
        int count = reload(file, catalog(server));
        var details = active.diagnostics(null);
        int prerequisites = active.parsed.prerequisiteCount();
        String counts = count + (count == 1 ? " unlocking requirement and " : " unlocking requirements and ")
                + prerequisites + (prerequisites == 1 ? " prerequisite" : " prerequisites");
        if (count < 0) {
            return new io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section(
                    "Could not load unlocking_rules.json", Severity.ERROR, details, file);
        }
        boolean rejectedEntries = details.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        String summary = rejectedEntries ? "Loaded " + counts + ". Some entries were rejected"
                : "Successfully loaded " + counts;
        return new io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section(
                summary, rejectedEntries ? Severity.WARNING : null, details, file);
    }

    static int reload(Path file, Catalog catalog) {
        var result = readFile(file, catalog);
        active = result.rules();
        loaded = true;
        PartySharing.changed();
        return result.fallback() ? -1 : active.parsed.rules().size();
    }

    private record LoadedRules(UnlockingRules rules, boolean fallback) {}

    private static LoadedRules readFile(Path file, Catalog catalog) {
        try {
            String source = Files.readString(file);
            return new LoadedRules(new UnlockingRules(UnlockingRuleParser.parse(source, catalog, file)), false);
        } catch (IOException | IllegalArgumentException e) {
            UnlockingRules fallback = new UnlockingRules(Result.empty(
                    new Diagnostic(Severity.ERROR, "Could not load unlocking_rules.json: " + failureReason(e))));
            return new LoadedRules(fallback, true);
        }
    }

    private static String failureReason(Exception error) {
        if (error instanceof java.nio.file.FileSystemException failure) {
            return java.util.Objects.requireNonNullElse(failure.getReason(), failure.getClass().getSimpleName());
        }
        return error.getMessage();
    }

    static UnlockingRules loadFile(Path file, Catalog catalog) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, ExplorersCompassConfig.DEFAULT_UNLOCKING_RULES);
        } catch (IOException e) {
            return new UnlockingRules(Result.empty(
                    new Diagnostic(Severity.ERROR, file.getFileName() + ": Could not create rules: " + failureReason(e))));
        }
        return readFile(file, catalog).rules();
    }

    static UnlockingRules parse(JsonObject root, Catalog catalog) {
        return parse(root.toString(), catalog);
    }

    static UnlockingRules parse(String source, Catalog catalog) {
        return new UnlockingRules(UnlockingRuleParser.parse(source, catalog, null));
    }

    private Set<Identifier> defaultVisitTypes(Identifier id) {
        var sets = parsed.visitSetsByStructure().get(id);
        if (sets == null || sets.size() == 1) return sets == null ? Set.of(id) : sets.get(0);
        Set<Identifier> types = new LinkedHashSet<>();
        sets.forEach(types::addAll);
        return types;
    }

    public boolean hasUnlockingRequirements(ServerPlayerEntity player) {
        return hasUnlockingRequirements(FtbStructureRewards.snapshot(player));
    }

    boolean hasUnlockingRequirements(FtbStructureRewards.Snapshot rewards) {
        return parsed.defaultMode() != DefaultMode.UNLOCKED || !parsed.prerequisites().isEmpty()
                || parsed.byStructure().values().stream().anyMatch(requirement ->
                        !requirement.condition().visits().isEmpty() || !requirement.condition().advancements().isEmpty())
                || !rewards.rewardTargets().isEmpty();
    }

    boolean shouldShowUnlockToast(ServerPlayerEntity player) {
        return !parsed.usesGeneratedDefaults() || shouldShowUnlockToast(FtbStructureRewards.snapshot(player));
    }

    boolean shouldShowUnlockToast(FtbStructureRewards.Snapshot rewards) {
        return !parsed.usesGeneratedDefaults() || !rewards.rewardTargets().isEmpty();
    }

    public boolean shouldShowRequirements(ServerPlayerEntity player) {
        return !parsed.usesGeneratedDefaults() || FtbStructureRewards.snapshot(player).rewardTargets().values().stream()
                .anyMatch(targets -> !targets.isEmpty());
    }

    public Set<String> requirementNames() { return Set.copyOf(parsed.rules().keySet()); }
    public Set<Identifier> requirementTargets(String id) {
        Requirement rule = parsed.rules().get(id);
        return rule == null ? null : rule.targets();
    }

    public static boolean isUnlocked(MinecraftServer server, UUID player, Identifier structure) {
        return UnlockedStructures.get(server).contains(player, structure) || PartySharing.grants(player, structure);
    }

    /** Called on compass open or when the compass advancement is earned. */
    public void evaluateOnOpen(ServerPlayerEntity player) {
        int added = evaluateSilently(player);
        UnlockNotifications.newlyAdded(player, added);
    }

    public int evaluateSilently(ServerPlayerEntity player) {
        return evaluateSilently(player, Set.of());
    }

    public int evaluateSilently(ServerPlayerEntity player, Set<Identifier> importedUnlocks) {
        FtbStructureRewards.Snapshot rewards = FtbStructureRewards.snapshot(player);
        var state = UnlockedStructures.get(player.getServer());
        Set<Identifier> candidates = openCandidates(catalog(player.getServer()).structures(), progress(player), rewards);
        candidates.addAll(importedUnlocks);
        return state.add(player.getUuid(), applyPrerequisites(candidates, state.getStructures(player.getUuid())));
    }

    int evaluateClaimedReward(ServerPlayerEntity player, Set<Identifier> targets) {
        if (!parsed.prerequisites().isEmpty()) return evaluateSilently(player, targets);
        return UnlockedStructures.get(player.getServer()).add(player.getUuid(), targets);
    }

    public Set<Identifier> applyPrerequisites(Set<Identifier> candidates, Set<Identifier> saved) {
        if (parsed.prerequisites().isEmpty() || candidates.isEmpty()) return candidates;
        Set<Identifier> unlocked = new HashSet<>(saved);
        Set<Identifier> earned = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Identifier id : candidates) {
                if (unlocked.contains(id)) continue;
                var gate = parsed.prerequisites().get(id);
                if (gate == null || gate.satisfied(unlocked::contains)) {
                    unlocked.add(id);
                    earned.add(id);
                    changed = true;
                }
            }
        } while (changed);
        return earned;
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress) {
        return earnedOnOpen(structures, progress, Set.of());
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress, FtbStructureRewards.Snapshot rewards) {
        return applyPrerequisites(openCandidates(structures, progress, rewards), Set.of());
    }

    private Set<Identifier> openCandidates(Set<Identifier> structures, Progress progress, FtbStructureRewards.Snapshot rewards) {
        Set<Identifier> candidates = openCandidates(structures, progress, rewards.quests().keySet());
        for (long rewardId : rewards.claimedRewardIds()) candidates.addAll(rewards.targets(rewardId));
        return candidates;
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress, Set<Identifier> rewardCovered) {
        return applyPrerequisites(openCandidates(structures, progress, rewardCovered), Set.of());
    }

    private Set<Identifier> openCandidates(Set<Identifier> structures, Progress progress, Set<Identifier> rewardCovered) {
        Set<Identifier> earned = new LinkedHashSet<>();
        Predicate<Identifier> defaultSatisfied = switch (parsed.defaultMode()) {
            case UNLOCKED -> id -> true;
            case LOCKED_UNTIL_VISITED -> progress::visited;
            case LOCKED_UNTIL_VISITED_SET -> {
                // The same membership set is shared by every member in the index.
                Map<Set<Identifier>, Boolean> visitedSets = new IdentityHashMap<>();
                yield id -> {
                    var sets = parsed.visitSetsByStructure().get(id);
                    return sets == null ? progress.visited(id) : sets.stream().anyMatch(set ->
                            visitedSets.computeIfAbsent(set, members -> members.stream().anyMatch(progress::visited)));
                };
            }
        };
        for (Identifier id : structures) {
            if (!rewardCovered.contains(id) && !parsed.byStructure().containsKey(id)
                    && defaultSatisfied.test(id)) earned.add(id);
        }
        for (Map.Entry<Identifier, Requirement> entry : parsed.byStructure().entrySet()) {
            if (!rewardCovered.contains(entry.getKey()) && entry.getValue().condition().satisfied().test(progress))
                earned.add(entry.getKey());
        }
        return earned;
    }

    /** Evaluate only rules affected by newly recorded progress. */
    public void onProgress(ServerPlayerEntity player, Identifier visit, Identifier advancement) {
        MinecraftServer server = player.getServer();
        if (server == null || server.getOverworld() == null) return;
        if (!parsed.prerequisites().isEmpty()) {
            evaluateOnOpen(player);
            return;
        }
        int added = UnlockedStructures.get(server).add(player.getUuid(), earnedOnProgress(progress(player), visit, advancement,
                FtbStructureRewards.snapshot(player).quests().keySet()));
        UnlockNotifications.newlyAdded(player, added);
    }

    Set<Identifier> earnedOnProgress(Progress progress, Identifier visit, Identifier advancement) {
        return earnedOnProgress(progress, visit, advancement, Set.of());
    }

    Set<Identifier> earnedOnProgress(Progress progress, Identifier visit, Identifier advancement, Set<Identifier> rewardCovered) {
        Set<Identifier> earned = new LinkedHashSet<>();
        if (visit != null) {
            for (Identifier id : defaultVisitTypes(visit)) {
                if (!rewardCovered.contains(id) && !parsed.byStructure().containsKey(id)) earned.add(id);
            }
        }
        for (Map.Entry<Identifier, Requirement> entry : parsed.byStructure().entrySet()) {
            Requirement rule = entry.getValue();
            if (!rewardCovered.contains(entry.getKey()) && ((visit != null && rule.condition().visits().contains(visit))
                    || (advancement != null && rule.condition().advancements().contains(advancement)))
                    && rule.condition().satisfied().test(progress)) earned.add(entry.getKey());
        }
        return earned;
    }

    public Map<Identifier, RequirementNode> personalRequirements(ServerPlayerEntity player, List<Identifier> structures) {
        Map<Identifier, RequirementNode> result = new LinkedHashMap<>();
        UnlockedStructures saved = UnlockedStructures.get(player.getServer());
        Progress progress = progress(player);
        FtbStructureRewards.Snapshot rewards = FtbStructureRewards.snapshot(player);
        Set<Identifier> unlocked = saved.getStructures(player.getUuid());
        for (Identifier id : structures) {
            if (unlocked.contains(id)) continue;
            RequirementNode prerequisite = prerequisiteNode(id, unlocked);
            List<FtbStructureRewards.Quest> quests = rewards.quests().get(id);
            result.put(id, prerequisite != null ? prerequisite
                    : quests == null ? requirements(id, progress) : questNode(quests));
        }
        return result;
    }

    public RequirementNode inspectRequirements(ServerPlayerEntity player, Identifier id) {
        var unlocked = UnlockedStructures.get(player.getServer()).getStructures(player.getUuid());
        var rewards = FtbStructureRewards.snapshot(player);
        var quests = rewards.quests().get(id);
        var rule = parsed.byStructure().get(id);
        RequirementNode requirement = null;
        if (quests != null) {
            requirement = questNode(quests);
        } else if (rule != null || parsed.defaultMode() != DefaultMode.UNLOCKED) {
            requirement = requirements(id, progress(player));
        }
        RequirementNode prerequisite = describePrerequisite(id, unlocked);
        if (prerequisite == null) {
            return requirement != null ? requirement
                    : new RequirementNode(Text.literal("No unlocking requirements"), true, List.of());
        }
        if (requirement == null) return prerequisite;
        return new RequirementNode(Text.literal("All of the following:"),
                prerequisite.checked() && requirement.checked(), List.of(prerequisite, requirement));
    }

    RequirementNode prerequisiteNode(Identifier id, Set<Identifier> unlocked) {
        var gate = parsed.prerequisites().get(id);
        return gate == null || gate.satisfied(unlocked::contains) ? null : describePrerequisite(id, unlocked);
    }

    private RequirementNode describePrerequisite(Identifier id, Set<Identifier> unlocked) {
        var gate = parsed.prerequisites().get(id);
        if (gate == null) return null;
        List<RequirementNode> children = gate.required().keySet().stream()
                .map(required -> RequirementNode.structure(Text.literal(readable(required)).formatted(Formatting.AQUA),
                        unlocked.contains(required), required))
                .toList();
        String label = children.size() == 1 ? "Unlock Structure:"
                : gate.all() ? "Unlock all Structures:" : "Unlock any Structure:";
        return new RequirementNode(Text.literal(label), gate.satisfied(unlocked::contains), gate.all(), children);
    }

    RequirementNode requirements(Identifier id, Progress progress) {
        Requirement rule = parsed.byStructure().get(id);
        if (rule == null) {
            Set<Identifier> required = defaultVisitTypes(id);
            return visitNode(required, false, required, progress);
        }
        return rule.condition().description().apply(progress);
    }

    static RequirementNode questNode(List<FtbStructureRewards.Quest> quests) {
        Map<Long, FtbStructureRewards.Quest> distinct = new LinkedHashMap<>();
        quests.forEach(quest -> distinct.putIfAbsent(quest.id(), quest));
        List<RequirementNode> children = distinct.values().stream()
                .map(quest -> new RequirementNode(quest.title(), false, List.of(), quest.id())).toList();
        return new RequirementNode(Text.literal(children.size() == 1 ? "Complete Quest:" : "Complete any Quest:"),
                false, children);
    }

    /** Resolve the two FTB selector fields strictly against their respective registries. */
    public static Set<Identifier> resolveRewardTargets(MinecraftServer server, List<String> structures, List<String> sets) {
        return resolveRewardTargets(catalog(server), structures, sets);
    }

    static Set<Identifier> resolveRewardTargets(Catalog catalog, List<String> structures, List<String> sets) {
        Set<Identifier> result = new LinkedHashSet<>();
        for (String selector : structures) {
            if (!selector.isBlank()) result.addAll(resolvePattern(selector.trim(), catalog.structures()));
        }
        for (String selector : sets) {
            if (selector.isBlank()) continue;
            for (Identifier id : resolvePattern(selector.trim(), catalog.structureSets()))
                result.addAll(catalog.structureSet(id));
        }
        result.removeIf(id -> !catalog.structures().contains(id) || StructureUtils.structureIdIsExcluded(id));
        return result;
    }

    public static Set<Identifier> setMembers(MinecraftServer server, Identifier id) {
        StructureSet set = server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(id);
        if (set == null) { warn("Unavailable structure set " + id); return Set.of(); }
        return set.structures().stream().map(entry -> entry.structure().getKey().orElseThrow().getValue())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static RequirementNode visitNode(Set<Identifier> required, boolean all, Set<Identifier> known, Progress progress) {
        List<RequirementNode> children = required.stream().map(id -> RequirementNode.structure(
                Text.literal(readable(id)).formatted(Formatting.AQUA),
                known.contains(id) && progress.visited(id), id)).toList();
        boolean checked = !children.isEmpty() && (all ? children.stream().allMatch(RequirementNode::checked)
                : children.stream().anyMatch(RequirementNode::checked));
        String label = required.size() == 1 ? "Visit Structure:"
                : all ? "Visit all Structures:" : "Visit any Structure:";
        return new RequirementNode(Text.literal(label), checked, all, children);
    }

    static RequirementNode advancementNode(Catalog catalog, Set<Identifier> required, boolean all, Progress progress) {
        List<RequirementNode> leaves = required.stream().map(id -> {
            Formatting color = catalog.advancementColor(id);
            Text description = catalog.advancementDescription(id);
            return RequirementNode.advancement(catalog.advancementTitle(id).copy().formatted(color),
                    catalog.advancementExists(id) && progress.completed(id),
                    description == null ? null : description.copy().formatted(color),
                    id, catalog.advancementHasDisplay(id), catalog.advancementTab(id));
        }).toList();
        boolean checked = all ? leaves.stream().allMatch(RequirementNode::checked)
                : leaves.stream().anyMatch(RequirementNode::checked);
        String label = required.size() == 1 ? "Complete Advancement:"
                : all ? "Complete all Advancements:" : "Complete any Advancements:";
        return new RequirementNode(Text.literal(label), checked, all, leaves);
    }

    private static Set<Identifier> resolvePattern(String value, Set<Identifier> available) {
        Set<Identifier> matches = new StructureSelector(value).matchingIds(available);
        if (matches.isEmpty()) {
            if (value.contains("*")) warn("No structure or structure set matches pattern " + value);
            else warn("Unavailable structure or structure set " + new Identifier(value));
        }
        return matches;
    }

    public static Set<Identifier> resolveCostTargets(MinecraftServer server, String selector) {
        Catalog catalog = catalog(server);
        Set<Identifier> structures = catalog.structures();
        Set<Identifier> available = new LinkedHashSet<>(structures);
        available.addAll(catalog.structureSets());
        Set<Identifier> result = new LinkedHashSet<>();
        for (Identifier id : new StructureSelector(selector).matchingIds(available)) {
            if (structures.contains(id)) result.add(id);
            else result.addAll(catalog.structureSet(id));
        }
        return result;
    }

    private static String readable(Identifier id) {
        String path = id.getPath();
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return path.isEmpty() ? id.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
    private static void warn(String message) { LOGGER.warn("Unlocking rules: {}", message); }
}
