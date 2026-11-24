package io.github.enriquemetner.explorerscompass.unlocking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.structure.StructureSelector;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Catalog;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Progress;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Severity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Compiles one rule document against the server catalogue. */
final class UnlockingRuleParser {
    private static final JsonElement GENERATED_DEFAULTS =
            new UnlockingRuleDocument(ExplorersCompassConfig.DEFAULT_UNLOCKING_RULES).root;
    private static final Set<String> REQUIREMENT_FIELDS =
            Set.of("unlockedStructures", "requiredVisits", "requiredAdvancements", "alwaysUnlocked", "match");
    private static final Set<String> PREREQUISITE_FIELDS = Set.of("appliesTo", "requiresUnlocked", "match");

    enum DefaultMode { UNLOCKED, LOCKED_UNTIL_VISITED, LOCKED_UNTIL_VISITED_SET }

    record Result(DefaultMode defaultMode, boolean usesGeneratedDefaults, Map<String, Requirement> rules,
                  Map<Identifier, Requirement> byStructure, Map<Identifier, List<Set<Identifier>>> visitSetsByStructure,
                  Map<String, Set<String>> visitSetDefinitions, Map<Identifier, UnlockPrerequisites.Gate> prerequisites,
                  List<UnlockDefinitions.Rule> prerequisiteDefinitions, int prerequisiteCount, List<Diagnostic> diagnostics) {
        static Result empty(Diagnostic... diagnostics) {
            return new Result(DefaultMode.UNLOCKED, false, Map.of(), Map.of(), Map.of(), Map.of(),
                    new LinkedHashMap<>(), List.of(), 0, List.of(diagnostics));
        }

        List<UnlockDefinitions.Rule> requirementDefinitions() {
            return rules.entrySet().stream().map(entry -> new UnlockDefinitions.Rule(entry.getKey(),
                    UnlockDefinitions.ids(entry.getValue().targets()), entry.getValue().condition().definition())).toList();
        }
    }

    record Condition(Predicate<Progress> satisfied, Function<Progress, RequirementNode> description,
                             Set<Identifier> visits, Set<Identifier> advancements, boolean all) {
        String definition() {
            if (!visits.isEmpty()) return UnlockDefinitions.condition("visit", all, UnlockDefinitions.ids(visits));
            if (!advancements.isEmpty()) return UnlockDefinitions.condition("advancement", all, UnlockDefinitions.ids(advancements));
            return "always";
        }
    }
    record Requirement(Set<Identifier> targets, Condition condition) {}

    private final UnlockingRuleDocument document;
    private final Catalog catalog;
    private final String fileName;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<Identifier, UnlockPrerequisites.Gate> prerequisites = new LinkedHashMap<>();
    private final List<UnlockDefinitions.Rule> prerequisiteDefinitions = new ArrayList<>();
    private final Map<String, Requirement> rules = new LinkedHashMap<>();
    private final Map<Identifier, Requirement> byStructure = new LinkedHashMap<>();
    private final Map<Identifier, List<Set<Identifier>>> visitSetsByStructure = new LinkedHashMap<>();
    private final Map<String, Set<String>> visitSetDefinitions = new LinkedHashMap<>();
    private int prerequisiteCount;

    private UnlockingRuleParser(String source, Catalog catalog, Path file) {
        this.document = new UnlockingRuleDocument(source);
        this.catalog = catalog;
        this.fileName = file == null ? "unlocking_rules.json" : file.getFileName().toString();
    }

    static Result parse(String source, Catalog catalog, Path file) {
        return new UnlockingRuleParser(source, catalog, file).parse();
    }

    private void diagnose(Severity severity, String message) {
        diagnostics.add(new Diagnostic(severity, fileName + ": " + message));
    }

    private Result parse() {
        DefaultMode defaultMode = DefaultMode.UNLOCKED;
        Map<Identifier, List<Integer>> declarationLines = new LinkedHashMap<>();
        Set<Identifier> duplicates = new LinkedHashSet<>();
        if (!(document.root instanceof JsonObject root))
            throw invalid("Expected a JSON object at line " + document.line(document.root));
        if (root.has("default")) {
            defaultMode = at(root.get("default"), () -> {
                String mode = string(root, "default");
                return switch (mode) {
                    case "unlocked" -> DefaultMode.UNLOCKED;
                    case "lockedUntilVisited" -> DefaultMode.LOCKED_UNTIL_VISITED;
                    case "lockedUntilVisitedSet" -> DefaultMode.LOCKED_UNTIL_VISITED_SET;
                    default -> throw invalid("Unknown \"default\": " + mode);
                };
            });
        }
        if (defaultMode == DefaultMode.LOCKED_UNTIL_VISITED_SET) indexVisitSets();
        if (root.has("rules"))
            throw located(root.get("rules"), "Replace the old \"rules\" field with \"requirements\"");
        JsonObject entries = new JsonObject();
        if (root.has("requirements")) {
            if (!(root.get("requirements") instanceof JsonObject configured))
                throw located(root.get("requirements"), "Expected \"requirements\" to be an object");
            entries = configured;
        }
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            try {
                if (!(entry.getValue() instanceof JsonObject value)) throw invalid("Expected a requirement object");
                for (String key : value.keySet()) {
                    if (!REQUIREMENT_FIELDS.contains(key))
                        throw located(value.get(key), "Unknown requirement field: " + quoted(key));
                }
                Map<Identifier, List<Integer>> targetLines = new LinkedHashMap<>();
                Set<Identifier> targets = resolveRequirementStructures(value, "unlockedStructures",
                        (id, line) -> targetLines.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line));
                Condition condition = condition(value);
                Requirement rule = new Requirement(targets, condition);
                rules.put(entry.getKey(), rule);
                for (Identifier id : targets) {
                    if (byStructure.put(id, rule) != null) duplicates.add(id);
                    declarationLines.computeIfAbsent(id, ignored -> new ArrayList<>())
                            .addAll(targetLines.getOrDefault(id, List.of()));
                }
            } catch (IllegalArgumentException e) {
                String message = e instanceof LocatedError ? e.getMessage()
                        : "line " + document.fieldLine(entries, entry.getKey()) + ": " + e.getMessage();
                diagnose(Severity.ERROR, "Ignored invalid requirement " + quoted(entry.getKey()) + " at " + message);
            }
        }
        for (Identifier id : duplicates) {
            List<Integer> positions = declarationLines.get(id);
            String listed = positions.isEmpty() ? "unknown" : positions.stream().distinct()
                    .map(String::valueOf).collect(Collectors.joining(", "));
            diagnose(Severity.WARNING, id + " is listed more than once under \"unlockedStructures\" on lines " + listed
                    + "; the last requirement takes precedence");
        }
        loadPrerequisites(root);
        return new Result(defaultMode, GENERATED_DEFAULTS.equals(root), rules, byStructure, visitSetsByStructure,
                visitSetDefinitions, prerequisites, prerequisiteDefinitions, prerequisiteCount, List.copyOf(diagnostics));
    }

    private void loadPrerequisites(JsonObject root) {
        if (!root.has("prerequisites")) return;
        if (!(root.get("prerequisites") instanceof JsonObject entries)) {
            diagnose(Severity.ERROR, "Ignored \"prerequisites\" at line "
                    + document.line(root.get("prerequisites")) + ": Expected an object");
            return;
        }
        for (var entry : entries.entrySet()) {
            try {
                if (!(entry.getValue() instanceof JsonObject value)) throw invalid("Expected a prerequisite object");
                for (String key : value.keySet()) {
                    if (!PREREQUISITE_FIELDS.contains(key))
                        throw located(value.get(key), "Unknown prerequisite field: " + quoted(key));
                }
                Map<Identifier, List<Integer>> targets = new LinkedHashMap<>();
                Map<Identifier, List<Integer>> required = new LinkedHashMap<>();
                resolveRequirementStructures(value, "appliesTo",
                        (id, line) -> targets.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line));
                resolveRequirementStructures(value, "requiresUnlocked",
                        (id, line) -> required.computeIfAbsent(id, ignored -> new ArrayList<>()).add(line));
                boolean all = matchAll(value);
                targets.forEach((id, lines) -> prerequisites.put(id, new UnlockPrerequisites.Gate(all, required, lines)));
                prerequisiteDefinitions.add(new UnlockDefinitions.Rule(entry.getKey(), UnlockDefinitions.ids(targets.keySet()),
                        UnlockDefinitions.condition("unlocked", all, UnlockDefinitions.ids(required.keySet()))));
                prerequisiteCount++;
            } catch (IllegalArgumentException e) {
                String message = e instanceof LocatedError ? e.getMessage()
                        : "line " + document.fieldLine(entries, entry.getKey()) + ": " + e.getMessage();
                diagnose(Severity.ERROR, "Ignored invalid prerequisite " + quoted(entry.getKey()) + " at " + message);
            }
        }
        if (UnlockPrerequisites.reportCycles(prerequisites, message -> diagnose(Severity.ERROR, message))) {
            prerequisites.clear();
            prerequisiteCount = 0;
        }
    }

    private void indexVisitSets() {
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

    private Condition condition(JsonObject json) {
        List<String> kinds = List.of("requiredVisits", "requiredAdvancements", "alwaysUnlocked")
                .stream().filter(json::has).toList();
        if (kinds.size() != 1)
            throw invalid("Specify exactly one of \"requiredVisits\", \"requiredAdvancements\", or \"alwaysUnlocked\"");
        if (json.has("alwaysUnlocked")) {
            JsonElement enabled = json.get("alwaysUnlocked");
            if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean() || !enabled.getAsBoolean())
                throw located(enabled, "Expected \"alwaysUnlocked\" to be true");
            if (json.has("match"))
                throw located(json.get("match"), "\"match\" cannot be used with \"alwaysUnlocked\"");
            return new Condition(progress -> true,
                    progress -> new RequirementNode(Text.literal("Always Unlocked"), true, List.of()), Set.of(), Set.of(), false);
        }
        boolean all = matchAll(json);
        if (json.has("requiredAdvancements")) {
            Catalog advancements = catalog;
            Set<Identifier> required = new LinkedHashSet<>();
            for (JsonElement element : selectorArray(json, "requiredAdvancements")) {
                Identifier id = at(element, () -> identifier(selectorString(element)));
                if (!advancements.advancementExists(id)) throw located(element, "Unknown advancement " + id);
                required.add(id);
            }
            return new Condition(progress -> {
                Predicate<Identifier> completed = id -> advancements.advancementExists(id) && progress.completed(id);
                return all ? required.stream().allMatch(completed) : required.stream().anyMatch(completed);
            }, progress -> UnlockingRules.advancementNode(advancements, required, all, progress), Set.of(), required, all);
        }
        Set<Identifier> required = resolveRequirementStructures(json, "requiredVisits",
                (id, line) -> {});
        Set<Identifier> known = catalog.structures();
        return new Condition(player -> {
            Predicate<Identifier> visited = id -> known.contains(id)
                    && player.visited(id);
            return !required.isEmpty() && (all ? required.stream().allMatch(visited) : required.stream().anyMatch(visited));
        }, player -> UnlockingRules.visitNode(required, all, known, player), required, Set.of(), all);
    }

    private boolean matchAll(JsonObject json) {
        if (!json.has("match")) return false;
        return atField(json, "match", () -> switch (string(json, "match")) {
            case "all" -> true;
            case "any" -> false;
            default -> throw invalid("Expected \"match\" to be any or all");
        });
    }

    private Set<Identifier> resolveRequirementStructures(JsonObject object, String field,
            BiConsumer<Identifier, Integer> origin) {
        Set<Identifier> result = new LinkedHashSet<>();
        Set<Identifier> structures = catalog.structures();
        Set<Identifier> sets = catalog.structureSets();
        Set<Identifier> available = new LinkedHashSet<>(structures);
        available.addAll(sets);
        for (JsonElement element : selectorArray(object, field)) {
            String selector = at(element, () -> selectorString(element));
            Set<Identifier> matches = at(element, () -> new StructureSelector(selector).matchingIds(available));
            Set<Identifier> resolved = new LinkedHashSet<>();
            for (Identifier id : matches) {
                if (structures.contains(id)) {
                    if (sets.contains(id)) diagnose(Severity.WARNING, "Line " + document.line(element)
                            + ": Both a structure and structure set use " + id + "; treating it as a single structure");
                    resolved.add(id);
                } else {
                    for (Identifier member : catalog.structureSet(id)) {
                        if (!structures.contains(member))
                            throw located(element, "Unknown structure " + member + " in structure set " + id);
                        resolved.add(member);
                    }
                }
            }
            if (resolved.isEmpty()) throw located(element, "No structures match selector " + selector);
            for (Identifier id : resolved) origin.accept(id, document.line(element));
            result.addAll(resolved);
        }
        return result;
    }

    private JsonArray selectorArray(JsonObject object, String field) {
        return atField(object, field, () -> {
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

    private LocatedError located(JsonElement value, String message) {
        return new LocatedError("line " + document.line(value) + ": " + message);
    }

    private <T> T atField(JsonObject object, String field, Supplier<T> action) {
        if (!object.has(field)) throw new MissingField(field);
        return at(object.get(field), action);
    }

    private <T> T at(JsonElement value, Supplier<T> action) {
        try {
            return action.get();
        } catch (LocatedError | MissingField e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw located(value, e.getMessage());
        }
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

    private static String quoted(String name) { return new JsonPrimitive(name).toString(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
