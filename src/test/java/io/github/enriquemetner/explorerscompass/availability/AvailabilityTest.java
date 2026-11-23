package io.github.enriquemetner.explorerscompass.availability;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvailabilityTest {
    private final Identifier target=new Identifier("minecraft:desert_pyramid");
    @Test void optionalOverridesTakePrecedenceOverDimensionResults() {
        var yung=Availability.disabled(Availability.Reason.YUNG,"YUNG",List.of());
        var structurify=Availability.disabled(Availability.Reason.STRUCTURIFY,"Structurify",List.of());
        assertSame(yung,Availability.decide(yung,structurify,false,false));
        assertSame(structurify,Availability.decide(null,structurify,false,false));
        assertEquals(Availability.forDimensions(true, false), Availability.decide(null, null, true, false));
    }

    @Test void distinguishesNoViableWorldFromAnotherViableDimension() {
        assertEquals(Availability.generic(), Availability.forDimensions(false, false));
        assertEquals(Availability.wrongDimension(), Availability.forDimensions(true, false));
        assertEquals(Availability.available(), Availability.forDimensions(true, true));
    }
    @Test void genericRulesRequireBiomesAndAtLeastOnePermittedPlacement() {
        assertFalse(Availability.canGenerate(true,List.of()));
        assertFalse(Availability.canGenerate(false,List.of(true)));
        assertFalse(Availability.canGenerate(true,List.of(false,false)));
        assertTrue(Availability.canGenerate(true,List.of(false,true)),"Another enabled or unknown custom placement must survive");
    }
    @Test void listedSkipsAndDeduplicatesWithoutLosingEligibleEntries() {
        Identifier wrong = new Identifier("test:wrong");
        Identifier disabled = new Identifier("test:disabled");
        Identifier lockedElsewhere = new Identifier("test:locked_elsewhere");
        var result = AvailabilitySelection.classify(List.of(target, wrong, disabled, lockedElsewhere, target),
                id -> id.equals(disabled) ? Availability.generic()
                        : id.equals(target) ? Availability.available() : Availability.wrongDimension(),
                Set.of(target, wrong)::contains);

        assertEquals(List.of(target), result.eligible());
        assertEquals(List.of(disabled), result.disabled().stream().map(AvailabilitySelection.Disabled::structure).toList());
        assertEquals(1, result.locked());
        assertEquals(1, result.wrongDimension());
    }
    public static class General {
        public boolean disableVanillaPyramids,disableVanillaJungleTemples,disableVanillaWitchHuts,disableVanillaMonuments,disableVanillaFortresses;
    }
    public static class Config {
        public General general=new General();public boolean disableVanillaMineshafts;
    }
    public static class Common { public static Config CONFIG=new Config(); }
    private OptionalDisablingRules rules(Set<String> loaded,AtomicInteger unknown) {
        return new OptionalDisablingRules(new OptionalDisablingRules.Environment() {
            public boolean loaded(String id){return loaded.contains(id);}
            public Class<?> load(String name){return Common.class;}
            public void unknown(String id,Throwable error){unknown.incrementAndGet();}
        });
    }
    @Test void sevenYungMappingsHonorFlagsAndRegisteredReplacementsAndReevaluation() throws Exception {
        Common.CONFIG=new Config();
        for(var rule:OptionalDisablingRules.YUNG) {
            var adapter=rules(Set.of(rule.mod()),new AtomicInteger());
            Identifier replacement=new Identifier(rule.mod(),rule.replacement()==null?"mineshaft_mesa":rule.replacement());
            if(rule.flag()!=null) {
                assertNull(adapter.yung("minecraft:"+rule.type(),Set.of(replacement)));
                Object owner=rule.flag().startsWith("general.")?Common.CONFIG.general:Common.CONFIG;
                String field=rule.flag().substring(rule.flag().lastIndexOf('.')+1);
                owner.getClass().getField(field).set(owner,true);
            }
            var result=adapter.yung("minecraft:"+rule.type(),Set.of(replacement,new Identifier("other:unrelated")));
            assertNotNull(result,rule.mod());assertEquals(Availability.Reason.YUNG,result.reason());
            assertEquals(List.of(replacement),result.replacements());
            if(rule.flag()!=null) {
                Object owner=rule.flag().startsWith("general.")?Common.CONFIG.general:Common.CONFIG;
                owner.getClass().getField(rule.flag().substring(rule.flag().lastIndexOf('.')+1)).set(owner,false);
                assertNull(adapter.yung("minecraft:"+rule.type(),Set.of(replacement)));
            }
        }
    }
    @Test void absentAndUnknownOptionalModsDoNotDisable() {
        var adapter=rules(Set.of(),new AtomicInteger());assertNull(adapter.yung("minecraft:stronghold",Set.of()));
        assertNull(adapter.structurify(target,List.of()));
        AtomicInteger warnings=new AtomicInteger();
        var unknown=rules(Set.of("structurify"),warnings);
        assertNull(unknown.structurify(target,List.of()));assertEquals(1,warnings.get());
        assertTrue(unknown.frequencyPermits(new Identifier("test:set"),0),"Unknown overrides must not turn a stale base zero into a rejection");
    }
    public static class Entry {
        boolean disabled;float frequency=1;boolean defaultFrequency=true;Map<String,Integer> weights=new HashMap<>();
        public boolean isDisabled(){return disabled;}public float getFrequency(){return frequency;}
        public boolean isUsingDefaultFrequency(){return defaultFrequency;}public Map<String,Integer> getStructureWeights(){return weights;}
    }
    public static class StructConfig {
        public boolean disableAllStructures;Map<String,Entry> structures=new HashMap<>(),sets=new HashMap<>();
        public Map<String,Entry> getStructureData(){return structures;}public Map<String,Entry> getStructureSetData(){return sets;}
    }
    @Test void structurifyGlobalStructureSetAndWeightSettingsAndMultiplePlacements() throws Exception {
        StructConfig config=new StructConfig();Identifier a=new Identifier("test:a"),b=new Identifier("test:b");
        assertNull(OptionalDisablingRules.structurifyConfig(config,target,List.of(a)));
        config.disableAllStructures=true;assertNotNull(OptionalDisablingRules.structurifyConfig(config,target,List.of()));
        config.disableAllStructures=false;Entry entry=new Entry();entry.disabled=true;config.structures.put(target.toString(),entry);
        assertNotNull(OptionalDisablingRules.structurifyConfig(config,target,List.of()));config.structures.clear();
        config.sets.put(a.toString(),entry);assertNotNull(OptionalDisablingRules.structurifyConfig(config,target,List.of(a)));
        config.sets.put(b.toString(),new Entry());assertNull(OptionalDisablingRules.structurifyConfig(config,target,List.of(a,b)));
        entry.disabled=false;entry.frequency=0;entry.defaultFrequency=false;
        assertNotNull(OptionalDisablingRules.structurifyConfig(config,target,List.of(a)));
        entry.defaultFrequency=true;assertNull(OptionalDisablingRules.structurifyConfig(config,target,List.of(a)),"An unchanged default zero is generic, not caused by Structurify");
        entry.frequency=1;entry.weights.put(target.toString(),0);
        assertNotNull(OptionalDisablingRules.structurifyConfig(config,target,List.of(a)));
    }

    @Test void attributesMixedBlockedSetsToStructurifyOnlyWhenEverySetBlocks() throws Exception {
        var config = new StructConfig();
        var overridden = new Identifier("test:overridden");
        var defaultZero = new Identifier("test:default_zero");
        var other = new Identifier("test:other");
        var override = new Entry();
        override.weights.put(target.toString(), 0);
        var defaultBlocked = new Entry();
        defaultBlocked.frequency = 0;
        config.sets.put(overridden.toString(), override);
        config.sets.put(defaultZero.toString(), defaultBlocked);

        assertEquals(Availability.Reason.STRUCTURIFY,
                OptionalDisablingRules.structurifyConfig(config, target, List.of(defaultZero, overridden)).reason());
        assertEquals(Availability.Reason.STRUCTURIFY,
                OptionalDisablingRules.structurifyConfig(config, target, List.of(overridden, defaultZero)).reason());
        assertNull(OptionalDisablingRules.structurifyConfig(config, target, List.of(overridden, defaultZero, other)));
        config.sets.put(other.toString(), new Entry());
        assertNull(OptionalDisablingRules.structurifyConfig(config, target, List.of(overridden, defaultZero, other)));

        override.weights.clear();
        override.frequency = 0;
        assertNull(OptionalDisablingRules.structurifyConfig(config, target, List.of(overridden, defaultZero)));
    }
}
