package com.chaosthedude.explorerscompass.availability;

import java.util.*;
import java.util.function.Function;
import net.minecraft.util.Identifier;

public record AvailabilitySelection(List<Identifier> eligible, List<Disabled> disabled, int wrongDimension) {
    public record Disabled(Identifier structure, Availability availability) {}
    public AvailabilitySelection { eligible = List.copyOf(eligible); disabled = List.copyOf(disabled); }
    public static AvailabilitySelection classify(List<Identifier> ids, Function<Identifier,Availability> check) {
        List<Identifier> eligible = new ArrayList<>();
        List<Disabled> disabled = new ArrayList<>();
        int wrong = 0;
        for (Identifier id : new LinkedHashSet<>(ids)) {
            Availability result = check.apply(id);
            switch (result.status()) {
                case AVAILABLE -> eligible.add(id);
                case WRONG_DIMENSION -> wrong++;
                case DISABLED -> disabled.add(new Disabled(id,result));
            }
        }
        return new AvailabilitySelection(eligible,disabled,wrong);
    }
}
