package com.chaosthedude.explorerscompass.unlocking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.util.Identifier;

final class UnlockPrerequisites {
    record Gate(boolean all, Map<Identifier, List<Integer>> required, List<Integer> targetLines) {
        boolean satisfied(Predicate<Identifier> unlocked) {
            return all ? required.keySet().stream().allMatch(unlocked) : required.keySet().stream().anyMatch(unlocked);
        }
    }

    private UnlockPrerequisites() {}

    static boolean reportCycles(Map<Identifier, Gate> gates, Consumer<String> error) {
        Map<Identifier, List<Identifier>> incoming = new LinkedHashMap<>();
        gates.forEach((target, gate) -> gate.required.keySet().forEach(dependency ->
                incoming.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(target)));

        boolean cyclic = false;
        Set<Identifier> earlierStarts = new HashSet<>();
        for (Identifier start : gates.keySet().stream().sorted().toList()) {
            Set<Identifier> reachesStart = new HashSet<>();
            var pending = new ArrayDeque<Identifier>();
            pending.add(start);
            while (!pending.isEmpty()) {
                Identifier target = pending.remove();
                if (earlierStarts.contains(target) || !reachesStart.add(target)) continue;
                pending.addAll(incoming.getOrDefault(target, List.of()));
            }
            cyclic |= reportFrom(gates, start, reachesStart, error);
            earlierStarts.add(start);
        }
        return cyclic;
    }

    private static boolean reportFrom(Map<Identifier, Gate> gates, Identifier start,
                                      Set<Identifier> reachesStart, Consumer<String> error) {
        var path = new ArrayList<Identifier>();
        var onPath = new HashSet<Identifier>();
        var edges = new ArrayDeque<java.util.Iterator<Identifier>>();
        path.add(start);
        onPath.add(start);
        edges.push(gates.get(start).required.keySet().iterator());
        boolean cyclic = false;
        while (!edges.isEmpty()) {
            var next = edges.peek();
            if (!next.hasNext()) {
                edges.pop();
                onPath.remove(path.remove(path.size() - 1));
                continue;
            }
            Identifier dependency = next.next();
            if (dependency.equals(start)) {
                reportCycle(gates, path, start, error);
                cyclic = true;
            } else if (reachesStart.contains(dependency) && onPath.add(dependency)) {
                path.add(dependency);
                edges.push(gates.get(dependency).required.keySet().iterator());
            }
        }
        return cyclic;
    }

    private static void reportCycle(Map<Identifier, Gate> gates, List<Identifier> path, Identifier start,
                                    Consumer<String> error) {
        var lines = new java.util.TreeSet<Integer>();
        for (int i = 0; i < path.size(); i++) {
            Gate gate = gates.get(path.get(i));
            Identifier dependency = i + 1 < path.size() ? path.get(i + 1) : start;
            lines.addAll(gate.targetLines);
            lines.addAll(gate.required.get(dependency));
        }
        String chain = path.stream().map(Identifier::toString).collect(Collectors.joining(" → ")) + " → " + start;
        error.accept("Prerequisite loop on lines " + lines.stream().map(String::valueOf).collect(Collectors.joining(", "))
                + ": " + chain + ". Ignoring all prerequisites.");
    }
}
