package io.github.enriquemetner.explorerscompass.compat.ftb;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.Identifier;

/** Evaluates a visit requirement against one player's recorded structure types. */
public final class VisitTaskMatch {
    private VisitTaskMatch() {}

    public static boolean satisfied(Set<Identifier> targets, boolean all, Predicate<Identifier> visited) {
        return !targets.isEmpty() && (all ? targets.stream().allMatch(visited) : targets.stream().anyMatch(visited));
    }
}
