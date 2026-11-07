package com.chaosthedude.explorerscompass.availability;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.util.Identifier;

public record AvailabilitySelection(List<Identifier> eligible, List<Disabled> disabled, int wrongDimension, int locked) {
    public record Disabled(Identifier structure, Availability availability) {}
    public AvailabilitySelection { eligible = List.copyOf(eligible); disabled = List.copyOf(disabled); }
    public static AvailabilitySelection classify(List<Identifier> ids, Function<Identifier, Availability> check,
                                                 Predicate<Identifier> unlocked) {
        List<Identifier> eligible = new ArrayList<>();
        List<Disabled> disabled = new ArrayList<>();
        int wrong = 0;
        int locked = 0;
        for (Identifier id : new LinkedHashSet<>(ids)) {
            Availability result = check.apply(id);
            if (result.status() == Availability.Status.DISABLED) {
                disabled.add(new Disabled(id, result));
            } else if (!unlocked.test(id)) {
                locked++;
            } else if (result.status() == Availability.Status.WRONG_DIMENSION) {
                wrong++;
            } else {
                eligible.add(id);
            }
        }
        return new AvailabilitySelection(eligible, disabled, wrong, locked);
    }
}
