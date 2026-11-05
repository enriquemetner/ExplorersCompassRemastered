package com.chaosthedude.explorerscompass.availability;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.mixins.StructurePlacementAccessor;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.placement.*;

public final class StructureAvailability {
    private static final Set<String> WARNED = new HashSet<>();
    private static final OptionalDisablingRules MODS = new OptionalDisablingRules(new OptionalDisablingRules.Environment() {
        public boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
        public Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name); }
        public void unknown(String id, Throwable error) {
            if (WARNED.add(id)) ExplorersCompass.LOGGER.warn("Unable to inspect disabling settings for {}. Keeping normal search behaviour.",id,error);
        }
    });
    private StructureAvailability() {}
    public static Availability check(ServerWorld current, Identifier id) {
        var registries = current.getRegistryManager();
        var registry = registries.get(RegistryKeys.STRUCTURE);
        var structure = registry.get(id);
        if (structure == null) return Availability.generic();
        String type = String.valueOf(Registries.STRUCTURE_TYPE.getId(structure.getType()));
        Availability yung = MODS.yung(type,registry.getIds());
        if (yung != null) {
            Set<Identifier> permitted = new HashSet<>(StructureUtils.getAllowedStructureIDs(current));
            return Availability.disabled(yung.reason(),yung.modName(),yung.replacements().stream().filter(permitted::contains).toList());
        }
        var sets = registries.get(RegistryKeys.STRUCTURE_SET);
        List<Identifier> containing = sets.stream().filter(set -> MODS.originalMembers(set,set.structures()).stream()
                .anyMatch(entry -> ((net.minecraft.structure.StructureSet.WeightedEntry)entry).structure().value() == structure))
                .map(sets::getId).toList();
        Availability structurify = MODS.structurify(id,containing);
        if (structurify != null) return structurify;
        if (!current.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures()) return Availability.generic();
        boolean any = false, here = false;
        for (ServerWorld world : current.getServer().getWorlds()) {
            var biomes = world.getChunkManager().getChunkGenerator().getBiomeSource().getBiomes();
            boolean validBiome = structure.getValidBiomes().stream().anyMatch(biomes::contains);
            var placements = world.getChunkManager().getStructurePlacementCalculator()
                    .getPlacements(StructureUtils.getEntryForStructure(world,structure));
            boolean valid = Availability.canGenerate(validBiome,placements.stream().map(p -> canAttempt(p,sets)).toList());
            any |= valid;
            if (world == current) here = valid;
        }
        return Availability.decide(null,null,any,here);
    }
    private static boolean canAttempt(StructurePlacement placement, Registry<net.minecraft.structure.StructureSet> sets) {
        // Arbitrary overrides can ignore the base frequency; do not guess about them.
        if (placement.getClass() != RandomSpreadStructurePlacement.class && placement.getClass() != ConcentricRingsStructurePlacement.class) return true;
        float frequency = ((StructurePlacementAccessor) placement).explorerscompass$getFrequency();
        if (!MODS.structurifyLoaded()) return frequency > 0;
        var ids = sets.stream().filter(set -> set.placement() == placement).map(sets::getId).toList();
        return ids.isEmpty() || ids.stream().anyMatch(id -> MODS.frequencyPermits(id,frequency));
    }
}
