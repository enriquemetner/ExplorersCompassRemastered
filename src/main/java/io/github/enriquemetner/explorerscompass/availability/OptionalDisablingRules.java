package io.github.enriquemetner.explorerscompass.availability;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.util.Identifier;

/** Reflect only mod-owned config APIs: no optional mod classes enter our linkage graph. */
public final class OptionalDisablingRules {
    public interface Environment {
        boolean loaded(String id);
        Class<?> load(String name) throws ClassNotFoundException;
        void unknown(String id, Throwable error);
    }
    record YungRule(String mod, String suffix, String type, String flag, String title, String replacement) {}
    static final List<YungRule> YUNG = List.of(
        new YungRule("bettermineshafts","Mineshafts","mineshaft","disableVanillaMineshafts","Mineshafts",null),
        new YungRule("betterdeserttemples","DesertTemples","desert_pyramid","general.disableVanillaPyramids","Desert Temples","desert_temple"),
        new YungRule("betterjungletemples","JungleTemples","jungle_temple","general.disableVanillaJungleTemples","Jungle Temples","jungle_temple"),
        new YungRule("betterwitchhuts","WitchHuts","swamp_hut","general.disableVanillaWitchHuts","Witch Huts","witch_hut"),
        new YungRule("betteroceanmonuments","OceanMonuments","ocean_monument","general.disableVanillaMonuments","Ocean Monuments","ocean_monument"),
        new YungRule("betterfortresses","Fortresses","fortress","general.disableVanillaFortresses","Nether Fortresses","fortress"),
        new YungRule("betterstrongholds","Strongholds","stronghold",null,"Strongholds","stronghold"));
    private final Environment env;
    public OptionalDisablingRules(Environment env) { this.env = env; }

    public Availability yung(String structureType, Set<Identifier> registry) {
        for (YungRule rule : YUNG) {
            if (!structureType.equals("minecraft:"+rule.type) || !env.loaded(rule.mod)) continue;
            try {
                if (rule.flag != null) {
                    Class<?> common = env.load("com.yungnickyoung.minecraft."+rule.mod+".Better"+rule.suffix+"Common");
                    Object config = common.getField("CONFIG").get(null);
                    if (!Boolean.TRUE.equals(path(config,rule.flag))) continue;
                }
                List<Identifier> replacements = registry.stream().filter(id -> id.getNamespace().equals(rule.mod))
                    .filter(id -> rule.replacement != null ? id.getPath().equals(rule.replacement) : id.getPath().startsWith("mineshaft_"))
                    .sorted().toList();
                return Availability.disabled(Availability.Reason.YUNG,"YUNG's Better "+rule.title,replacements);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { env.unknown(rule.mod,e); }
        }
        return null;
    }

    public Availability structurify(Identifier structure, List<Identifier> sets) {
        if (!env.loaded("structurify")) return null;
        try {
            Object config = env.load("com.faboslav.structurify.common.Structurify").getMethod("getConfig").invoke(null);
            return structurifyConfig(config,structure,sets);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { env.unknown("structurify",e); return null; }
    }
    public boolean frequencyPermits(Identifier set, float original) {
        if (!env.loaded("structurify")) return original > 0;
        try {
            Class<?> util = env.load("com.faboslav.structurify.common.util.RandomSpreadUtil");
            return ((Number)util.getMethod("getModifiedFrequency",String.class,float.class)
                    .invoke(null,set.toString(),original)).floatValue() > 0;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { env.unknown("structurify",e); return true; }
    }
    public boolean structurifyLoaded() { return env.loaded("structurify"); }
    public List<?> originalMembers(Object set, List<?> fallback) {
        if (!env.loaded("structurify")) return fallback;
        try { return (List<?>)set.getClass().getMethod("structurify$getOriginalStructures").invoke(set); }
        catch (ReflectiveOperationException | RuntimeException e) { env.unknown("structurify",e); return fallback; }
    }
    static Availability structurifyConfig(Object config, Identifier structure, List<Identifier> sets) throws ReflectiveOperationException {
        if (Boolean.TRUE.equals(path(config, "disableAllStructures"))) return structurifyDisabled();
        String structureId = structure.toString();
        Map<?, ?> structures = (Map<?, ?>) config.getClass().getMethod("getStructureData").invoke(config);
        Object entry = structures.get(structureId);
        if (entry != null && Boolean.TRUE.equals(entry.getClass().getMethod("isDisabled").invoke(entry))) return structurifyDisabled();

        Map<?, ?> setData = (Map<?, ?>) config.getClass().getMethod("getStructureSetData").invoke(config);
        boolean explicitlyBlocked = false;
        for (Identifier set : sets) {
            var decision = setDecision(setData.get(set.toString()), structureId);
            if (decision == SetDecision.PERMITS_GENERATION) return null;
            explicitlyBlocked |= decision == SetDecision.EXPLICITLY_BLOCKED;
        }
        return explicitlyBlocked ? structurifyDisabled() : null;
    }

    private enum SetDecision { PERMITS_GENERATION, EXPLICITLY_BLOCKED, BLOCKED_BY_DEFAULT_FREQUENCY }

    private static SetDecision setDecision(Object data, String structureId) throws ReflectiveOperationException {
        if (data == null) return SetDecision.PERMITS_GENERATION;
        var type = data.getClass();
        if (Boolean.TRUE.equals(type.getMethod("isDisabled").invoke(data))) return SetDecision.EXPLICITLY_BLOCKED;

        Map<?, ?> weights = (Map<?, ?>) type.getMethod("getStructureWeights").invoke(data);
        Object weight = weights.get(structureId);
        if (weight instanceof Number number && number.intValue() == 0) return SetDecision.EXPLICITLY_BLOCKED;

        float frequency = ((Number) type.getMethod("getFrequency").invoke(data)).floatValue();
        if (frequency > 0) return SetDecision.PERMITS_GENERATION;
        return Boolean.TRUE.equals(type.getMethod("isUsingDefaultFrequency").invoke(data))
                ? SetDecision.BLOCKED_BY_DEFAULT_FREQUENCY : SetDecision.EXPLICITLY_BLOCKED;
    }
    private static Availability structurifyDisabled() { return Availability.disabled(Availability.Reason.STRUCTURIFY,"Structurify",List.of()); }
    static Object path(Object object, String path) throws ReflectiveOperationException {
        for (String field : path.split("\\.")) object = object.getClass().getField(field).get(object);
        return object;
    }
}
