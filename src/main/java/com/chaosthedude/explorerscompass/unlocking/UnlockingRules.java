package com.chaosthedude.explorerscompass.unlocking;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.fabricmc.loader.api.FabricLoader;
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
    private static final Logger LOGGER = LogManager.getLogger("explorerscompass");
    private static UnlockingRules active = new UnlockingRules();
    private static String activeDefinition = "";
    private enum DefaultMode { UNLOCKED, LOCKED_UNTIL_VISITED, LOCKED_UNTIL_VISITED_SET }

    private DefaultMode defaultMode = DefaultMode.UNLOCKED;
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final Map<Identifier, Rule> byStructure = new LinkedHashMap<>();
    private final Map<Identifier, List<Set<Identifier>>> visitSetsByStructure = new LinkedHashMap<>();

    private record Condition(Predicate<Progress> test, Function<Progress, RequirementNode> display,
                             Set<Identifier> visits, Set<Identifier> advancements) {}
    private record Rule(Set<Identifier> targets, Condition condition) {}

    public static UnlockingRules get() { return active; }

    interface Catalog {
        Set<Identifier> structures();
        Set<Identifier> structureSets();
        Set<Identifier> structureSet(Identifier id);
        boolean advancementExists(Identifier id);
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

    public static void clear() { active = new UnlockingRules(); activeDefinition = ""; }

    public static String definition() { return activeDefinition; }

    public static int reload(MinecraftServer server) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("explorerscompass/unlocking_rules.json");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, "{\n  \"default\": \"unlocked\",\n  \"rules\": {}\n}\n");
            String source = Files.readString(file);
            UnlockingRules loaded = parse(source, catalog(server));
            active = loaded;
            activeDefinition = JsonParser.parseString(source).toString();
            PartySharing.changed();
            LOGGER.info("Loaded {} structure unlocking rules", active.rules.size());
            return active.rules.size();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not reload {}. Keeping active unlocking rules.", file, e);
            return -1;
        }
    }

    static UnlockingRules loadFile(Path file, Catalog catalog) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, "{\n  \"default\": \"unlocked\",\n  \"rules\": {}\n}\n");
            String source = Files.readString(file);
            return parse(source, catalog);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Cannot load {}. Using default unlocked with no rules.", file, e);
            return new UnlockingRules();
        }
    }

    static UnlockingRules parse(JsonObject root, Catalog catalog) {
        return parse(root, catalog, Map.of());
    }

    static UnlockingRules parse(String source, Catalog catalog) {
        return parse(JsonParser.parseString(source).getAsJsonObject(), catalog, unlockLines(source));
    }

    private static UnlockingRules parse(JsonObject root, Catalog catalog, Map<String, List<Integer>> lines) {
        UnlockingRules result = new UnlockingRules();
        Map<Identifier, List<Integer>> declarationLines = new LinkedHashMap<>();
        Set<Identifier> duplicates = new LinkedHashSet<>();
        String mode = root.has("default") ? string(root, "default") : "unlocked";
        result.defaultMode = switch (mode) {
            case "unlocked" -> DefaultMode.UNLOCKED;
            case "lockedUntilVisited" -> DefaultMode.LOCKED_UNTIL_VISITED;
            case "lockedUntilVisitedSet" -> DefaultMode.LOCKED_UNTIL_VISITED_SET;
            default -> throw invalid("Unknown default: " + mode);
        };
        if (result.defaultMode == DefaultMode.LOCKED_UNTIL_VISITED_SET) result.indexVisitSets(catalog);
        JsonObject entries = root.has("rules") ? root.getAsJsonObject("rules") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            try {
                JsonObject value = entry.getValue().getAsJsonObject();
                for (String key : value.keySet())
                    if (!Set.of("unlocks", "condition").contains(key)) throw invalid("Unknown rule field: " + key);
                Map<Identifier, List<Integer>> targetLines = new LinkedHashMap<>();
                Set<Identifier> targets = targets(catalog, value.get("unlocks"),
                        lines.getOrDefault(entry.getKey(), List.of()), targetLines);
                Condition condition = condition(catalog, value.getAsJsonObject("condition"));
                Rule rule = new Rule(targets, condition);
                result.rules.put(entry.getKey(), rule);
                for (Identifier id : targets) {
                    if (result.byStructure.put(id, rule) != null) duplicates.add(id);
                    declarationLines.computeIfAbsent(id, ignored -> new ArrayList<>())
                            .addAll(targetLines.getOrDefault(id, List.of()));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Skipping invalid rule '{}' in unlocking_rules.json: {}", entry.getKey(), e.getMessage());
            }
        }
        for (Identifier id : duplicates) {
            List<Integer> positions = declarationLines.get(id);
            String listed = positions.isEmpty() ? "unknown" : positions.stream()
                    .map(UnlockingRules::line).collect(Collectors.joining(", "));
            warn("Structure " + id + " is listed under unlocks on lines " + listed
                    + "; the last condition takes precedence");
        }
        return result;
    }

    private void indexVisitSets(Catalog catalog) {
        Set<Identifier> known = catalog.structures();
        for (Identifier setId : catalog.structureSets().stream().sorted().toList()) {
            Set<Identifier> members = new LinkedHashSet<>(catalog.structureSet(setId));
            members.retainAll(known);
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
        return defaultMode != DefaultMode.UNLOCKED || !rules.isEmpty() || !rewards.rewardTargets().isEmpty();
    }

    public Set<String> ruleNames() { return Set.copyOf(rules.keySet()); }
    public Set<Identifier> ruleTargets(String id) {
        Rule rule = rules.get(id);
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
        Set<Identifier> earned = earnedOnOpen(catalog(player.getServer()).structures(), progress(player), rewards);
        earned.addAll(importedUnlocks);
        return UnlockedStructures.get(player.getServer()).add(player.getUuid(), earned);
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress) {
        return earnedOnOpen(structures, progress, Set.of());
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress, FtbStructureRewards.Snapshot rewards) {
        Set<Identifier> earned = earnedOnOpen(structures, progress, rewards.quests().keySet());
        for (long rewardId : rewards.claimedRewardIds()) earned.addAll(rewards.targets(rewardId));
        return earned;
    }

    Set<Identifier> earnedOnOpen(Set<Identifier> structures, Progress progress, Set<Identifier> rewardCovered) {
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
        for (Map.Entry<Identifier, Rule> entry : byStructure.entrySet())
            if (!rewardCovered.contains(entry.getKey()) && entry.getValue().condition.test.test(progress)) earned.add(entry.getKey());
        return earned;
    }

    /** Evaluate only rules affected by newly recorded progress. */
    public void onProgress(ServerPlayerEntity player, Identifier visit, Identifier advancement) {
        MinecraftServer server = player.getServer();
        if (server == null || server.getOverworld() == null) return;
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
        for (Map.Entry<Identifier, Rule> entry : byStructure.entrySet()) {
            Rule rule = entry.getValue();
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
        for (Identifier id : structures) {
            if (saved.contains(player.getUuid(), id)) continue;
            List<FtbStructureRewards.Quest> quests = rewards.quests().get(id);
            result.put(id, quests == null ? requirements(id, progress) : questNode(quests));
        }
        return result;
    }

    RequirementNode requirements(Identifier id, Progress progress) {
        Rule rule = byStructure.get(id);
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
            if (!selector.isBlank()) result.addAll(resolvePattern(selector.trim(), catalog.structures(), false));
        }
        for (String selector : sets) {
            if (selector.isBlank()) continue;
            for (Identifier id : resolvePattern(selector.trim(), catalog.structureSets(), false))
                result.addAll(catalog.structureSet(id));
        }
        result.removeIf(id -> !catalog.structures().contains(id) || StructureUtils.structureIdIsBlacklisted(id));
        return result;
    }

    private static Set<Identifier> targets(Catalog catalog, JsonElement json, List<Integer> lines,
                                           Map<Identifier, List<Integer>> targetLines) {
        Set<Identifier> ids = resolveStructures(catalog, patterns(json), false,
                (id, index) -> targetLines.computeIfAbsent(id, ignored -> new ArrayList<>())
                        .add(index < lines.size() ? lines.get(index) : null));
        Set<Identifier> known = catalog.structures();
        return ids.stream().filter(id -> {
            if (known.contains(id)) return true;
            warn("Unavailable structure target " + id);
            return false;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<Identifier> setMembers(MinecraftServer server, Identifier id) {
        StructureSet set = server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(id);
        if (set == null) { warn("Unavailable structure set " + id); return Set.of(); }
        return set.structures().stream().map(entry -> entry.structure().getKey().orElseThrow().getValue())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Condition condition(Catalog catalog, JsonObject json) {
        if (json == null) throw invalid("Missing condition");
        if (json.has("any") || json.has("all")) throw invalid("Condition groups are no longer supported; use one condition per rule");
        String type = string(json, "type");
        if (type.equals("advancement")) {
            for (String key : json.keySet())
                if (!Set.of("type", "advancements", "match").contains(key)) throw invalid("Unknown advancement condition field: " + key);
            if (!json.has("advancements") || !json.get("advancements").isJsonArray() || json.has("id"))
                throw invalid("Advancement conditions require an advancements array");
            Set<Identifier> required = ids(json.get("advancements"));
            String match = json.has("match") ? string(json, "match") : "any";
            if (!match.equals("any") && !match.equals("all")) throw invalid("Unknown advancement match " + match);
            boolean all = match.equals("all");
            for (Identifier id : required) if (!catalog.advancementExists(id)) warn("Unavailable advancement " + id);
            return new Condition(progress -> all ? required.stream().allMatch(id -> catalog.advancementExists(id) && progress.completed(id))
                    : required.stream().anyMatch(id -> catalog.advancementExists(id) && progress.completed(id)),
                    progress -> {
                        List<RequirementNode> leaves = required.stream().map(id -> {
                            Formatting color = catalog.advancementColor(id);
                            Text description = catalog.advancementDescription(id);
                            return new RequirementNode(catalog.advancementTitle(id).copy().formatted(color),
                                    catalog.advancementExists(id) && progress.completed(id), false, List.of(),
                                    description == null ? null : description.copy().formatted(color));
                        }).toList();
                        boolean checked = all ? leaves.stream().allMatch(RequirementNode::checked)
                                : leaves.stream().anyMatch(RequirementNode::checked);
                        String label = required.size() == 1 ? "Complete Advancement:"
                                : all ? "Complete all Advancements:" : "Complete any Advancements:";
                        return new RequirementNode(Text.literal(label), checked, leaves);
                    }, Set.of(), required);
        }
        if (!type.equals("visit")) throw invalid("Unknown condition type " + type);
        for (String key : json.keySet())
            if (!Set.of("type", "structures", "match").contains(key)) throw invalid("Unknown visit condition field: " + key);
        String match = json.has("match") ? string(json, "match") : "any";
        if (!match.equals("any") && !match.equals("all")) throw invalid("Unknown visit match " + match);
        Set<Identifier> required = resolveStructures(catalog, json, true);
        Set<Identifier> known = catalog.structures();
        for (Identifier id : required) if (!known.contains(id)) warn("Unavailable visit requirement " + id);
        boolean all = match.equals("all");
        return new Condition(player -> {
            Predicate<Identifier> visited = id -> known.contains(id)
                    && player.visited(id);
            return !required.isEmpty() && (all ? required.stream().allMatch(visited) : required.stream().anyMatch(visited));
        }, player -> visitNode(required, all, known, player), required, Set.of());
    }

    private static RequirementNode visitNode(Set<Identifier> required, boolean all, Set<Identifier> known, Progress progress) {
        List<RequirementNode> children = required.stream().map(id -> new RequirementNode(
                Text.literal(readable(id)).formatted(Formatting.AQUA),
                known.contains(id) && progress.visited(id), List.of())).toList();
        boolean checked = !children.isEmpty() && (all ? children.stream().allMatch(RequirementNode::checked)
                : children.stream().anyMatch(RequirementNode::checked));
        String label = required.size() == 1 ? "Visit Structure:"
                : all ? "Visit all Structures:" : "Visit any Structure:";
        return new RequirementNode(Text.literal(label), checked, children);
    }

    private static Set<Identifier> resolveStructures(Catalog catalog, JsonObject json, boolean visit) {
        if (json == null || !json.has("structures")) throw invalid("Specify structures");
        return resolveStructures(catalog, patterns(json.get("structures")), visit, (id, index) -> {});
    }

    private static Set<Identifier> resolveStructures(Catalog catalog, List<String> patterns, boolean visit,
                                                      BiConsumer<Identifier, Integer> origin) {
        Set<Identifier> result = new LinkedHashSet<>();
        Set<Identifier> structures = catalog.structures();
        Set<Identifier> structureSets = catalog.structureSets();
        Set<Identifier> available = new LinkedHashSet<>(structures);
        available.addAll(structureSets);
        for (int index = 0; index < patterns.size(); index++) {
            String pattern = patterns.get(index);
            for (Identifier id : resolvePattern(pattern, available, visit)) {
                if (structures.contains(id)) {
                    if (structureSets.contains(id))
                        warn("Both a structure and structure set use " + id + "; treating it as a single structure");
                    result.add(id);
                    origin.accept(id, index);
                } else if (structureSets.contains(id)) {
                    for (Identifier member : catalog.structureSet(id)) {
                        result.add(member);
                        origin.accept(member, index);
                    }
                } else if (visit) {
                    result.add(id);
                    origin.accept(id, index);
                }
            }
        }
        return result;
    }

    private static String line(Integer value) { return value == null ? "unknown" : value.toString(); }

    /** Read source locations separately because Gson's JSON tree does not retain them. */
    static Map<String, List<Integer>> unlockLines(String source) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setLenient(true);
            reader.beginObject();
            while (reader.hasNext()) {
                if (!reader.nextName().equals("rules") || reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    continue;
                }
                reader.beginObject();
                while (reader.hasNext()) {
                    String ruleName = reader.nextName();
                    List<Integer> positions = new ArrayList<>();
                    result.put(ruleName, positions);
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue; }
                    reader.beginObject();
                    while (reader.hasNext()) {
                        if (!reader.nextName().equals("unlocks") || reader.peek() != JsonToken.BEGIN_ARRAY) {
                            reader.skipValue();
                            continue;
                        }
                        reader.beginArray();
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonToken.STRING) {
                                reader.nextString();
                                var location = Pattern.compile("line (\\d+)").matcher(reader.toString());
                                positions.add(location.find() ? Integer.parseInt(location.group(1)) : null);
                            } else {
                                reader.skipValue();
                                positions.add(null);
                            }
                        }
                        reader.endArray();
                    }
                    reader.endObject();
                }
                reader.endObject();
            }
            reader.endObject();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read unlock selector line numbers", e);
        }
        return result;
    }

    private static List<String> patterns(JsonElement json) {
        if (json == null || !json.isJsonArray() || json.getAsJsonArray().isEmpty())
            throw invalid("Structure selector must be a nonempty array of IDs or patterns");
        List<String> result = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw invalid("Structure IDs and patterns must be strings");
            result.add(value.getAsString());
        }
        return result;
    }

    private static Set<Identifier> resolvePattern(String value, Set<Identifier> available, boolean keepMissing) {
        String normalized = value.contains(":") ? value : "minecraft:" + value;
        Identifier validated = Identifier.tryParse(normalized.replace('*', 'a'));
        if (validated == null || normalized.indexOf(':') != normalized.lastIndexOf(':'))
            throw invalid("Invalid structure ID or pattern " + value);
        if (!normalized.contains("*")) {
            Identifier id = identifier(normalized);
            if (available.contains(id)) return Set.of(id);
            warn("Unavailable structure or structure set " + id);
            return keepMissing ? Set.of(id) : Set.of();
        }
        StringBuilder regex = new StringBuilder("^");
        for (char c : normalized.toCharArray()) {
            if (c == '*') regex.append(".*");
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        Pattern compiled = Pattern.compile(regex.append('$').toString());
        Set<Identifier> matches = available.stream().filter(id -> compiled.matcher(id.toString()).matches())
                .sorted().collect(Collectors.toCollection(LinkedHashSet::new));
        if (matches.isEmpty()) warn("No structure or structure set matches pattern " + value);
        return matches;
    }
    private static Set<Identifier> ids(JsonElement json) {
        Set<Identifier> result = new LinkedHashSet<>();
        for (JsonElement value : json.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("IDs must be strings");
            result.add(identifier(value.getAsString()));
        }
        if (result.isEmpty()) throw invalid("ID list cannot be empty");
        return result;
    }
    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string for " + key);
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
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static void warn(String message) { LOGGER.warn("Unlocking rules: {}", message); }
}
