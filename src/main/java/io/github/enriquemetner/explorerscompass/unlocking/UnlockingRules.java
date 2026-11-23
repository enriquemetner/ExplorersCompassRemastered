package io.github.enriquemetner.explorerscompass.unlocking;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures;
import io.github.enriquemetner.explorerscompass.structure.StructureSelector;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Server-owned rule definitions. Reloading replaces definitions without touching player data. */
public final class UnlockingRules {
    private static final JsonElement GENERATED_DEFAULTS =
            new UnlockingRuleDocument(ExplorersCompassConfig.DEFAULT_UNLOCKING_RULES).root;
    private static final Logger LOGGER = LogManager.getLogger("explorerscompass");
    private static UnlockingRules active = new UnlockingRules();
    private static boolean loaded;
    private enum DefaultMode { UNLOCKED, LOCKED_UNTIL_VISITED, LOCKED_UNTIL_VISITED_SET }

    public enum Severity { ERROR, WARNING }
    public record Diagnostic(Severity severity, String message) {}

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int prerequisiteCount;

    static List<Diagnostic> report(Severity severity) {
        return active.diagnostics(severity);
    }

    List<Diagnostic> diagnostics(Severity severity) {
        return diagnostics.stream().filter(entry -> severity == null || severity == entry.severity()).toList();
    }

    private void diagnose(Severity severity, Path file, String message) {
        diagnose(severity, (file == null ? "unlocking_rules.json" : file.getFileName().toString()) + ": " + message);
    }

    private void diagnose(Severity severity, String text) {
        diagnostics.add(new Diagnostic(severity, text));
    }

    private DefaultMode defaultMode = DefaultMode.UNLOCKED;
    private boolean usesGeneratedDefaults;
    private final Map<Identifier, UnlockPrerequisites.Gate> prerequisites = new LinkedHashMap<>();
    private final List<UnlockDefinitions.Rule> prerequisiteDefinitions = new ArrayList<>();
    private final Map<String, Requirement> rules = new LinkedHashMap<>();
    private final Map<Identifier, Requirement> byStructure = new LinkedHashMap<>();
    private final Map<Identifier, List<Set<Identifier>>> visitSetsByStructure = new LinkedHashMap<>();
    private final Map<String, Set<String>> visitSetDefinitions = new LinkedHashMap<>();

    private record Condition(Predicate<Progress> test, Function<Progress, RequirementNode> display,
                             Set<Identifier> visits, Set<Identifier> advancements, boolean all) {
        String definition() {
            if (!visits.isEmpty()) return UnlockDefinitions.condition("visit", all, UnlockDefinitions.ids(visits));
            if (!advancements.isEmpty()) return UnlockDefinitions.condition("advancement", all, UnlockDefinitions.ids(advancements));
            return "always";
        }
    }
    private record Requirement(Set<Identifier> targets, Condition condition) {}

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
        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)) return null;
        var definitions = rules.entrySet().stream().map(entry -> new UnlockDefinitions.Rule(entry.getKey(),
                UnlockDefinitions.ids(entry.getValue().targets), entry.getValue().condition.definition())).toList();
        var rewardDefinitions = rewards.stream().map(reward -> new UnlockDefinitions.Rule(Long.toString(reward.id()),
                UnlockDefinitions.ids(reward.targets()), reward.requirement()))
                .sorted(java.util.Comparator.comparing(UnlockDefinitions.Rule::id)).toList();
        return new UnlockDefinitions(defaultMode.name(), visitSetDefinitions, definitions, prerequisiteDefinitions, rewardDefinitions);
    }

    Map<Identifier, UnlockPrerequisites.Gate> prerequisiteGates() { return prerequisites; }

    public static io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section reload(MinecraftServer server) {
        Path file = ExplorersCompassConfig.serverConfigFile(server, ExplorersCompassConfig.UNLOCKING_RULES);
        int count = reload(file, catalog(server));
        var details = active.diagnostics(null);
        int prerequisites = active.prerequisiteCount;
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
        return result.fallback() ? -1 : active.rules.size();
    }

    private record LoadedRules(UnlockingRules rules, boolean fallback) {}

    private static LoadedRules readFile(Path file, Catalog catalog) {
        try {
            var document = new UnlockingRuleDocument(Files.readString(file));
            return new LoadedRules(parse(document, catalog, file), false);
        } catch (IOException | IllegalArgumentException e) {
            UnlockingRules fallback = new UnlockingRules();
            fallback.diagnose(Severity.ERROR, "Could not load unlocking_rules.json: " + failureReason(e));
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
            UnlockingRules fallback = new UnlockingRules();
            fallback.diagnose(Severity.ERROR, file, "Could not create rules: " + failureReason(e));
            return fallback;
        }
        return readFile(file, catalog).rules();
    }

    static UnlockingRules parse(JsonObject root, Catalog catalog) {
        return parse(root.toString(), catalog);
    }

    static UnlockingRules parse(String source, Catalog catalog) {
        return parse(new UnlockingRuleDocument(source), catalog, null);
    }

    private static UnlockingRules parse(UnlockingRuleDocument document, Catalog catalog, Path file) {
        UnlockingRules result = new UnlockingRules();
        Map<Identifier, List<Integer>> declarationLines = new LinkedHashMap<>();
        Set<Identifier> duplicates = new LinkedHashSet<>();
        if (!(document.root instanceof JsonObject root))
            throw invalid("Expected a JSON object at line " + document.line(document.root));
        if (root.has("default")) {
            result.defaultMode = at(document, root.get("default"), () -> {
                String mode = string(root, "default");
                return switch (mode) {
                    case "unlocked" -> DefaultMode.UNLOCKED;
                    case "lockedUntilVisited" -> DefaultMode.LOCKED_UNTIL_VISITED;
                    case "lockedUntilVisitedSet" -> DefaultMode.LOCKED_UNTIL_VISITED_SET;
                    default -> throw invalid("Unknown \"default\": " + mode);
                };
            });
        }
        if (result.defaultMode == DefaultMode.LOCKED_UNTIL_VISITED_SET) result.indexVisitSets(catalog);
        if (root.has("rules"))
            throw located(document, root.get("rules"), "Replace the old \"rules\" field with \"requirements\"");
        JsonObject entries = new JsonObject();
        if (root.has("requirements")) {
            if (!(root.get("requirements") instanceof JsonObject configured))
                throw located(document, root.get("requirements"), "Expected \"requirements\" to be an object");
            entries = configured;
        }
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            try {
                if (!(entry.getValue() instanceof JsonObject value)) throw invalid("Expected a requirement object");
                for (String key : value.keySet())
                    if (!Set.of("unlockedStructures", "requiredVisits", "requiredAdvancements", "alwaysUnlocked", "match").contains(key)) throw located(document, value.get(key), "Unknown requirement field: " + quoted(key));
                Map<Identifier, List<Integer>> targetLines = new LinkedHashMap<>();
                Set<Identifier> targets = resolveRequirementStructures(catalog, document, value, "unlockedStructures",
                        (id, line) -> targetLines.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line),
                        message -> result.diagnose(Severity.WARNING, file, message));
                Condition condition = condition(catalog, value, document,
                        message -> result.diagnose(Severity.WARNING, file, message));
                Requirement rule = new Requirement(targets, condition);
                result.rules.put(entry.getKey(), rule);
                for (Identifier id : targets) {
                    if (result.byStructure.put(id, rule) != null) duplicates.add(id);
                    declarationLines.computeIfAbsent(id, ignored -> new ArrayList<>())
                            .addAll(targetLines.getOrDefault(id, List.of()));
                }
            } catch (IllegalArgumentException e) {
                String message = e instanceof LocatedError ? e.getMessage()
                        : "line " + document.fieldLine(entries, entry.getKey()) + ": " + e.getMessage();
                result.diagnose(Severity.ERROR, file, "Ignored invalid requirement " + quoted(entry.getKey()) + " at " + message);
            }
        }
        for (Identifier id : duplicates) {
            List<Integer> positions = declarationLines.get(id);
            String listed = positions.isEmpty() ? "unknown" : positions.stream().distinct()
                    .map(String::valueOf).collect(Collectors.joining(", "));
            result.diagnose(Severity.WARNING, file, id + " is listed more than once under \"unlockedStructures\" on lines " + listed
                    + "; the last requirement takes precedence");
        }
        result.loadPrerequisites(root, document, catalog, file);
        result.usesGeneratedDefaults = GENERATED_DEFAULTS.equals(root);
        return result;
    }

    private void loadPrerequisites(JsonObject root, UnlockingRuleDocument document, Catalog catalog, Path file) {
        if (!root.has("prerequisites")) return;
        if (!(root.get("prerequisites") instanceof JsonObject entries)) {
            diagnose(Severity.ERROR, file, "Ignored \"prerequisites\" at line "
                    + document.line(root.get("prerequisites")) + ": Expected an object");
            return;
        }
        Consumer<String> warning = message -> diagnose(Severity.WARNING, file, message);
        for (var entry : entries.entrySet()) {
            try {
                if (!(entry.getValue() instanceof JsonObject value)) throw invalid("Expected a prerequisite object");
                for (String key : value.keySet()) {
                    if (!Set.of("appliesTo", "requiresUnlocked", "match").contains(key))
                        throw located(document, value.get(key), "Unknown prerequisite field: " + quoted(key));
                }
                Map<Identifier, List<Integer>> targets = new LinkedHashMap<>();
                Map<Identifier, List<Integer>> required = new LinkedHashMap<>();
                resolveRequirementStructures(catalog, document, value, "appliesTo",
                        (id, line) -> targets.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line), warning);
                resolveRequirementStructures(catalog, document, value, "requiresUnlocked",
                        (id, line) -> required.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line), warning);
                boolean all = matchAll(document, value);
                targets.forEach((id, lines) -> prerequisites.put(id, new UnlockPrerequisites.Gate(all, required, lines)));
                prerequisiteDefinitions.add(new UnlockDefinitions.Rule(entry.getKey(), UnlockDefinitions.ids(targets.keySet()),
                        UnlockDefinitions.condition("unlocked", all, UnlockDefinitions.ids(required.keySet()))));
                prerequisiteCount++;
            } catch (IllegalArgumentException e) {
                String message = e instanceof LocatedError ? e.getMessage()
                        : "line " + document.fieldLine(entries, entry.getKey()) + ": " + e.getMessage();
                diagnose(Severity.ERROR, file, "Ignored invalid prerequisite " + quoted(entry.getKey()) + " at " + message);
            }
        }
        if (UnlockPrerequisites.reportCycles(prerequisites, message -> diagnose(Severity.ERROR, file, message))) {
            prerequisites.clear();
            prerequisiteCount = 0;
        }
    }

    private void indexVisitSets(Catalog catalog) {
        Set<Identifier> known = catalog.structures();
        for (Identifier setId : catalog.structureSets().stream().sorted().toList()) {
            Set<Identifier> members = new LinkedHashSet<>(catalog.structureSet(setId));
            members.retainAll(known);
            if (!members.isEmpty()) visitSetDefinitions.put(setId.toString(), UnlockDefinitions.ids(members));
            for (Identifier member : members) {
                visitSetsByStructure.computeIfAbsent(member, ignored -> new ArrayList<>()).add(members);
            }
        }
    }

    private Set<Identifier> defaultVisitTypes(Identifier id) {
        var sets = visitSetsByStructure.get(id);
        if (sets == null || sets.size() == 1) return sets == null ? Set.of(id) : sets.get(0);
        Set<Identifier> types = new LinkedHashSet<>();
        sets.forEach(types::addAll);
        return types;
    }

    public boolean hasUnlockingRequirements(ServerPlayerEntity player) {
        return hasUnlockingRequirements(FtbStructureRewards.snapshot(player));
    }

    boolean hasUnlockingRequirements(FtbStructureRewards.Snapshot rewards) {
        return defaultMode != DefaultMode.UNLOCKED || !prerequisites.isEmpty() || byStructure.values().stream().anyMatch(requirement ->
                !requirement.condition.visits.isEmpty() || !requirement.condition.advancements.isEmpty())
                || !rewards.rewardTargets().isEmpty();
    }

    boolean shouldShowUnlockToast(ServerPlayerEntity player) {
        return !usesGeneratedDefaults || shouldShowUnlockToast(FtbStructureRewards.snapshot(player));
    }

    boolean shouldShowUnlockToast(FtbStructureRewards.Snapshot rewards) {
        return !usesGeneratedDefaults || !rewards.rewardTargets().isEmpty();
    }

    public boolean shouldShowRequirements(ServerPlayerEntity player) {
        return !usesGeneratedDefaults || FtbStructureRewards.snapshot(player).rewardTargets().values().stream()
                .anyMatch(targets -> !targets.isEmpty());
    }

    public Set<String> requirementNames() { return Set.copyOf(rules.keySet()); }
    public Set<Identifier> requirementTargets(String id) {
        Requirement rule = rules.get(id);
        return rule == null ? null : rule.targets;
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
        if (!prerequisites.isEmpty()) return evaluateSilently(player, targets);
        return UnlockedStructures.get(player.getServer()).add(player.getUuid(), targets);
    }

    public Set<Identifier> applyPrerequisites(Set<Identifier> candidates, Set<Identifier> saved) {
        if (prerequisites.isEmpty() || candidates.isEmpty()) return candidates;
        Set<Identifier> unlocked = new HashSet<>(saved);
        Set<Identifier> earned = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Identifier id : candidates) {
                if (unlocked.contains(id)) continue;
                var gate = prerequisites.get(id);
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
        Predicate<Identifier> defaultSatisfied = switch (defaultMode) {
            case UNLOCKED -> id -> true;
            case LOCKED_UNTIL_VISITED -> progress::visited;
            case LOCKED_UNTIL_VISITED_SET -> {
                // The same membership set is shared by every member in the index.
                Map<Set<Identifier>, Boolean> visitedSets = new IdentityHashMap<>();
                yield id -> {
                    var sets = visitSetsByStructure.get(id);
                    return sets == null ? progress.visited(id) : sets.stream().anyMatch(set ->
                            visitedSets.computeIfAbsent(set, members -> members.stream().anyMatch(progress::visited)));
                };
            }
        };
        for (Identifier id : structures) {
            if (!rewardCovered.contains(id) && !byStructure.containsKey(id)
                    && defaultSatisfied.test(id)) earned.add(id);
        }
        for (Map.Entry<Identifier, Requirement> entry : byStructure.entrySet())
            if (!rewardCovered.contains(entry.getKey()) && entry.getValue().condition.test.test(progress)) earned.add(entry.getKey());
        return earned;
    }

    /** Evaluate only rules affected by newly recorded progress. */
    public void onProgress(ServerPlayerEntity player, Identifier visit, Identifier advancement) {
        MinecraftServer server = player.getServer();
        if (server == null || server.getOverworld() == null) return;
        if (!prerequisites.isEmpty()) {
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
                if (!rewardCovered.contains(id) && !byStructure.containsKey(id)) earned.add(id);
            }
        }
        for (Map.Entry<Identifier, Requirement> entry : byStructure.entrySet()) {
            Requirement rule = entry.getValue();
            if (!rewardCovered.contains(entry.getKey()) && ((visit != null && rule.condition.visits.contains(visit))
                    || (advancement != null && rule.condition.advancements.contains(advancement)))
                    && rule.condition.test.test(progress)) earned.add(entry.getKey());
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
        var rule = byStructure.get(id);
        RequirementNode requirement = null;
        if (quests != null) {
            requirement = questNode(quests);
        } else if (rule != null || defaultMode != DefaultMode.UNLOCKED) {
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
        var gate = prerequisites.get(id);
        return gate == null || gate.satisfied(unlocked::contains) ? null : describePrerequisite(id, unlocked);
    }

    private RequirementNode describePrerequisite(Identifier id, Set<Identifier> unlocked) {
        var gate = prerequisites.get(id);
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
        Requirement rule = byStructure.get(id);
        if (rule == null) {
            Set<Identifier> required = defaultVisitTypes(id);
            return visitNode(required, false, required, progress);
        }
        return rule.condition.display.apply(progress);
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

    private static Condition condition(Catalog catalog, JsonObject json, UnlockingRuleDocument document, Consumer<String> warning) {
        List<String> kinds = List.of("requiredVisits", "requiredAdvancements", "alwaysUnlocked")
                .stream().filter(json::has).toList();
        if (kinds.size() != 1)
            throw invalid("Specify exactly one of \"requiredVisits\", \"requiredAdvancements\", or \"alwaysUnlocked\"");
        if (json.has("alwaysUnlocked")) {
            JsonElement enabled = json.get("alwaysUnlocked");
            if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean() || !enabled.getAsBoolean())
                throw located(document, enabled, "Expected \"alwaysUnlocked\" to be true");
            if (json.has("match"))
                throw located(document, json.get("match"), "\"match\" cannot be used with \"alwaysUnlocked\"");
            return new Condition(progress -> true,
                    progress -> new RequirementNode(Text.literal("Always Unlocked"), true, List.of()), Set.of(), Set.of(), false);
        }
        boolean all = matchAll(document, json);
        if (json.has("requiredAdvancements")) {
            Set<Identifier> required = new LinkedHashSet<>();
            for (JsonElement element : selectorArray(document, json, "requiredAdvancements")) {
                Identifier id = at(document, element, () -> identifier(selectorString(element)));
                if (!catalog.advancementExists(id)) throw located(document, element, "Unknown advancement " + id);
                required.add(id);
            }
            return new Condition(progress -> all ? required.stream().allMatch(id -> catalog.advancementExists(id) && progress.completed(id))
                    : required.stream().anyMatch(id -> catalog.advancementExists(id) && progress.completed(id)),
                    progress -> {
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
                    }, Set.of(), required, all);
        }
        Set<Identifier> required = resolveRequirementStructures(catalog, document, json, "requiredVisits",
                (id, line) -> {}, warning);
        Set<Identifier> known = catalog.structures();
        return new Condition(player -> {
            Predicate<Identifier> visited = id -> known.contains(id)
                    && player.visited(id);
            return !required.isEmpty() && (all ? required.stream().allMatch(visited) : required.stream().anyMatch(visited));
        }, player -> visitNode(required, all, known, player), required, Set.of(), all);
    }

    private static RequirementNode visitNode(Set<Identifier> required, boolean all, Set<Identifier> known, Progress progress) {
        List<RequirementNode> children = required.stream().map(id -> RequirementNode.structure(
                Text.literal(readable(id)).formatted(Formatting.AQUA),
                known.contains(id) && progress.visited(id), id)).toList();
        boolean checked = !children.isEmpty() && (all ? children.stream().allMatch(RequirementNode::checked)
                : children.stream().anyMatch(RequirementNode::checked));
        String label = required.size() == 1 ? "Visit Structure:"
                : all ? "Visit all Structures:" : "Visit any Structure:";
        return new RequirementNode(Text.literal(label), checked, all, children);
    }

    private static boolean matchAll(UnlockingRuleDocument document, JsonObject json) {
        if (!json.has("match")) return false;
        return atField(document, json, "match", () -> switch (string(json, "match")) {
            case "all" -> true;
            case "any" -> false;
            default -> throw invalid("Expected \"match\" to be any or all");
        });
    }

    private static Set<Identifier> resolveRequirementStructures(Catalog catalog, UnlockingRuleDocument document,
            JsonObject object, String field, BiConsumer<Identifier, Integer> origin,
            Consumer<String> warning) {
        Set<Identifier> result = new LinkedHashSet<>();
        Set<Identifier> structures = catalog.structures();
        Set<Identifier> sets = catalog.structureSets();
        Set<Identifier> available = new LinkedHashSet<>(structures);
        available.addAll(sets);
        for (JsonElement element : selectorArray(document, object, field)) {
            String selector = at(document, element, () -> selectorString(element));
            Set<Identifier> matches = at(document, element, () -> new StructureSelector(selector).matchingIds(available));
            Set<Identifier> resolved = new LinkedHashSet<>();
            for (Identifier id : matches) {
                if (structures.contains(id)) {
                    if (sets.contains(id)) warning.accept("Line " + document.line(element)
                            + ": Both a structure and structure set use " + id + "; treating it as a single structure");
                    resolved.add(id);
                } else {
                    for (Identifier member : catalog.structureSet(id)) {
                        if (!structures.contains(member))
                            throw located(document, element, "Unknown structure " + member + " in structure set " + id);
                        resolved.add(member);
                    }
                }
            }
            if (resolved.isEmpty()) throw located(document, element, "No structures match selector " + selector);
            for (Identifier id : resolved) origin.accept(id, document.line(element));
            result.addAll(resolved);
        }
        return result;
    }

    private static JsonArray selectorArray(UnlockingRuleDocument document, JsonObject object, String field) {
        return atField(document, object, field, () -> {
            if (!(object.get(field) instanceof JsonArray array) || array.isEmpty())
                throw invalid("Expected a nonempty " + quoted(field) + " array");
            return array;
        });
    }

    private static String selectorString(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid("IDs and patterns must be strings");
        return value.getAsString();
    }

    private static final class MissingField extends IllegalArgumentException {
        MissingField(String field) { super("Missing " + quoted(field)); }
    }

    private static final class LocatedError extends IllegalArgumentException {
        LocatedError(String message) { super(message); }
    }

    private static LocatedError located(UnlockingRuleDocument document, JsonElement value, String message) {
        return new LocatedError("line " + document.line(value) + ": " + message);
    }

    private static <T> T atField(UnlockingRuleDocument document, JsonObject object, String field,
                                 Supplier<T> action) {
        if (!object.has(field)) throw new MissingField(field);
        return at(document, object.get(field), action);
    }

    private static <T> T at(UnlockingRuleDocument document, JsonElement value, Supplier<T> action) {
        try {
            return action.get();
        } catch (LocatedError | MissingField e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw located(document, value, e.getMessage());
        }
    }

    private static Set<Identifier> resolvePattern(String value, Set<Identifier> available) {
        Set<Identifier> matches = new StructureSelector(value).matchingIds(available);
        if (matches.isEmpty()) {
            if (value.contains("*")) warn("No structure or structure set matches pattern " + value);
            else warn("Unavailable structure or structure set " + identifier(value));
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

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string for " + quoted(key));
        return value.getAsString();
    }
    private static Identifier identifier(String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw invalid("Invalid identifier " + value);
        return id;
    }
    private static String readable(Identifier id) {
        String path = id.getPath();
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return path.isEmpty() ? id.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
    private static String quoted(String name) { return new JsonPrimitive(name).toString(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static void warn(String message) { LOGGER.warn("Unlocking rules: {}", message); }
}
