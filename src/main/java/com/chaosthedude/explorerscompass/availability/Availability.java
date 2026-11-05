package com.chaosthedude.explorerscompass.availability;

import java.util.List;
import net.minecraft.util.Identifier;

public record Availability(Status status, Reason reason, String modName, List<Identifier> replacements) {
    public enum Status { AVAILABLE, WRONG_DIMENSION, DISABLED }
    public enum Reason { GENERIC, STRUCTURIFY, YUNG }
    public Availability { replacements = List.copyOf(replacements); }
    public static Availability available() { return new Availability(Status.AVAILABLE, Reason.GENERIC, "", List.of()); }
    public static Availability wrongDimension() { return new Availability(Status.WRONG_DIMENSION, Reason.GENERIC, "", List.of()); }
    public static Availability disabled(Reason reason, String mod, List<Identifier> replacements) {
        return new Availability(Status.DISABLED, reason, mod, replacements);
    }
    public static Availability generic() { return disabled(Reason.GENERIC, "", List.of()); }
    public static boolean canGenerate(boolean biomeCompatible, List<Boolean> placementPermissions) {
        return biomeCompatible && placementPermissions.stream().anyMatch(Boolean::booleanValue);
    }
    public static Availability decide(Availability yung, Availability structurify, boolean anyWorld, boolean currentWorld) {
        if (yung != null) return yung;
        if (structurify != null) return structurify;
        return !anyWorld ? generic() : currentWorld ? available() : wrongDimension();
    }
}
