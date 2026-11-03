package com.chaosthedude.explorerscompass.util;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Server-only ownership decisions; no unlock data is trusted from the item. */
public final class CompassTransfer {
    public static final String HOLDER = "LastCompassHolder";
    public static final String INHERITED = "InheritedCompassTarget";
    public record Result(boolean resetTarget, Set<Identifier> unlocks) {}
    private CompassTransfer() {}

    public static Result acquire(NbtCompound data, UUID player, boolean sharing, boolean found,
            Function<UUID, Set<Identifier>> savedUnlocks, Predicate<Identifier> allowed) {
        UUID previous = data.containsUuid(HOLDER) ? data.getUuid(HOLDER) : null;
        if (player.equals(previous)) return new Result(false, Set.of());
        data.putUuid(HOLDER, player);
        if (previous == null) return new Result(false, Set.of());
        if (!sharing) return new Result(true, Set.of());
        if (found) data.putBoolean(INHERITED, true);
        return new Result(false, savedUnlocks.apply(previous).stream().filter(allowed).collect(Collectors.toSet()));
    }
}
