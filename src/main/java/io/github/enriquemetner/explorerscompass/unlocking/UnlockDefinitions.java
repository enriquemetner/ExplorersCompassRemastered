package io.github.enriquemetner.explorerscompass.unlocking;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.util.Identifier;

/** Saved rule records. Effective requirements are built only for candidate structures. */
record UnlockDefinitions(String defaultMode, Map<String, Set<String>> visitSets,
                         List<Rule> rules, List<Rule> prerequisites, List<Rule> rewards) {
    static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    UnlockDefinitions {
        Objects.requireNonNull(defaultMode);
        visitSets = Map.copyOf(visitSets);
        rules = List.copyOf(rules);
        prerequisites = List.copyOf(prerequisites);
        rewards = List.copyOf(rewards);
    }

    record Rule(String id, Set<String> targets, String requirement) {
        Rule {
            Objects.requireNonNull(id);
            targets = Set.copyOf(targets);
            Objects.requireNonNull(requirement);
        }
    }

    static Set<String> ids(Collection<Identifier> ids) {
        return ids.stream().map(Identifier::toString).collect(Collectors.toUnmodifiableSet());
    }

    static String condition(String kind, boolean all, Collection<String> ids) {
        return kind + (all && ids.size() > 1 ? ":all:" : ":any:") + String.join(",", ids.stream().sorted().toList());
    }

    String serialize() { return GSON.toJson(this); }

    static UnlockDefinitions deserialize(String value) {
        try {
            return GSON.fromJson(value, UnlockDefinitions.class);
        } catch (JsonParseException | IllegalArgumentException error) {
            return null;
        }
    }

    Set<Identifier> changedStructures(UnlockDefinitions previous, Set<String> savedIds) {
        Set<String> candidates = new HashSet<>();
        if (!defaultMode.equals(previous.defaultMode)) candidates.addAll(savedIds);
        for (String set : union(visitSets.keySet(), previous.visitSets.keySet())) {
            Set<String> before = previous.visitSets.getOrDefault(set, Set.of());
            Set<String> after = visitSets.getOrDefault(set, Set.of());
            if (!before.equals(after)) {
                candidates.addAll(before);
                candidates.addAll(after);
            }
        }
        nominate(previous.rules, rules, true, candidates);
        nominate(previous.prerequisites, prerequisites, true, candidates);
        nominate(previous.rewards, rewards, false, candidates);
        if (candidates.isEmpty()) return Set.of();

        var before = previous.effective(candidates);
        var after = effective(candidates);
        return candidates.stream().filter(id -> !before.get(id).equals(after.get(id)))
                .map(Identifier::tryParse).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static void nominate(List<Rule> before, List<Rule> after, boolean ordered, Set<String> candidates) {
        if (before.equals(after)) return;
        Map<String, Rule> oldRules = before.stream().collect(Collectors.toMap(Rule::id, rule -> rule));
        Map<String, Rule> newRules = after.stream().collect(Collectors.toMap(Rule::id, rule -> rule));
        List<String> oldOrder = before.stream().map(Rule::id).filter(newRules::containsKey).toList();
        List<String> newOrder = after.stream().map(Rule::id).filter(oldRules::containsKey).toList();
        Set<String> reordered = new HashSet<>();
        if (ordered && !oldOrder.equals(newOrder)) {
            for (int i = 0; i < oldOrder.size(); i++) {
                if (!oldOrder.get(i).equals(newOrder.get(i))) reordered.add(oldOrder.get(i));
            }
        }

        for (String id : union(oldRules.keySet(), newRules.keySet())) {
            Rule oldRule = oldRules.get(id);
            Rule newRule = newRules.get(id);
            if (oldRule == null) candidates.addAll(newRule.targets);
            else if (newRule == null) candidates.addAll(oldRule.targets);
            else if (reordered.contains(id) || !oldRule.requirement.equals(newRule.requirement)) {
                candidates.addAll(oldRule.targets);
                candidates.addAll(newRule.targets);
            } else if (!oldRule.targets.equals(newRule.targets)) {
                for (String target : oldRule.targets) if (!newRule.targets.contains(target)) candidates.add(target);
                for (String target : newRule.targets) if (!oldRule.targets.contains(target)) candidates.add(target);
            }
        }
    }

    private record Effective(String requirement, Set<String> rewards, String prerequisite) {}

    private Map<String, Effective> effective(Set<String> candidates) {
        Map<String, String> winningRules = winningRules(rules, candidates);
        Map<String, String> gates = winningRules(prerequisites, candidates);
        Map<String, Set<String>> rewardClaims = new HashMap<>();
        for (Rule reward : rewards) {
            for (String id : reward.targets) {
                if (candidates.contains(id)) {
                    rewardClaims.computeIfAbsent(id, ignored -> new HashSet<>()).add(reward.id + ":" + reward.requirement);
                }
            }
        }

        Map<String, List<String>> memberships = new HashMap<>();
        visitSets.forEach((set, members) -> {
            for (String id : members) {
                if (candidates.contains(id) && !winningRules.containsKey(id) && !rewardClaims.containsKey(id)) {
                    memberships.computeIfAbsent(id, ignored -> new ArrayList<>()).add(set);
                }
            }
        });
        // Many members of the same sets share one default requirement.
        Map<List<String>, String> visitRequirements = new HashMap<>();

        Map<String, Effective> result = new HashMap<>();
        for (String id : candidates) {
            Set<String> claims = rewardClaims.getOrDefault(id, Set.of());
            String requirement = claims.isEmpty() ? winningRules.get(id) : null;
            if (claims.isEmpty() && requirement == null) {
                List<String> sets = memberships.get(id);
                if (defaultMode.equals("UNLOCKED")) requirement = "always";
                else if (sets == null) requirement = condition("visit", false, Set.of(id));
                else requirement = visitRequirements.computeIfAbsent(sets, sharedSets -> {
                    Set<String> members = new HashSet<>();
                    sharedSets.forEach(set -> members.addAll(visitSets.get(set)));
                    return condition("visit", false, members);
                });
            }
            result.put(id, new Effective(requirement, claims, gates.get(id)));
        }
        return result;
    }

    private static Map<String, String> winningRules(List<Rule> rules, Set<String> candidates) {
        Map<String, String> result = new HashMap<>();
        for (Rule rule : rules) {
            for (String id : rule.targets) if (candidates.contains(id)) result.put(id, rule.requirement);
        }
        return result;
    }

    private static <T> Set<T> union(Set<T> first, Set<T> second) {
        Set<T> result = new HashSet<>(first);
        result.addAll(second);
        return result;
    }
}
