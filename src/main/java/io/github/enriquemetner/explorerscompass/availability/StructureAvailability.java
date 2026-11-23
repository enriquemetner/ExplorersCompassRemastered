package io.github.enriquemetner.explorerscompass.availability;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.mixins.search.StructurePlacementAccessor;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;

public final class StructureAvailability {
    private static final Set<String> WARNED = new HashSet<>();
    private static final OptionalDisablingRules MODS = new OptionalDisablingRules(new OptionalDisablingRules.Environment() {
        public boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
        public Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name); }
        public void unknown(String id, Throwable error) {
            if (WARNED.add(id)) ExplorersCompass.LOGGER.warn("Unable to inspect disabling settings for {}. Keeping normal search behaviour",id,error);
        }
    });
    private StructureAvailability() {}

    public static Availability check(ServerWorld current, Identifier id) {
        var registries = current.getRegistryManager();
        var registry = registries.get(RegistryKeys.STRUCTURE);
        var structure = registry.get(id);
        if (structure == null) return Availability.generic();

        Availability yung = yungOverride(current, structure, registry);
        if (yung != null) return yung;

        var sets = registries.get(RegistryKeys.STRUCTURE_SET);
        Availability structurify = MODS.structurify(id, containingSets(sets, structure));
        if (structurify != null) return structurify;
        if (!current.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures()) return Availability.generic();

        return checkDimensions(current, structure, sets);
    }

    private static Availability yungOverride(ServerWorld world, Structure structure, Registry<Structure> registry) {
        String type = String.valueOf(Registries.STRUCTURE_TYPE.getId(structure.getType()));
        Availability yung = MODS.yung(type, registry.getIds());
        if (yung == null) return null;

        Set<Identifier> permitted = new HashSet<>(StructureUtils.getAllowedStructureIDs(world));
        var replacements = yung.replacements().stream().filter(permitted::contains).toList();
        return Availability.disabled(yung.reason(), yung.modName(), replacements);
    }

    private static List<Identifier> containingSets(Registry<StructureSet> sets, Structure structure) {
        return sets.stream()
                .filter(set -> MODS.originalMembers(set, set.structures()).stream()
                        .anyMatch(entry -> ((StructureSet.WeightedEntry) entry).structure().value() == structure))
                .map(sets::getId).toList();
    }

    private static Availability checkDimensions(ServerWorld current, Structure structure, Registry<StructureSet> sets) {
        boolean anyWorld = false;
        boolean currentWorld = false;
        for (ServerWorld world : current.getServer().getWorlds()) {
            var biomes = world.getChunkManager().getChunkGenerator().getBiomeSource().getBiomes();
            boolean validBiome = structure.getValidBiomes().stream().anyMatch(biomes::contains);
            var placements = world.getChunkManager().getStructurePlacementCalculator()
                    .getPlacements(StructureUtils.getEntryForStructure(world, structure));
            var placementPermissions = placements.stream().map(placement -> canAttempt(placement, sets)).toList();
            boolean valid = Availability.canGenerate(validBiome, placementPermissions);
            anyWorld |= valid;
            if (world == current) currentWorld = valid;
        }
        return Availability.forDimensions(anyWorld, currentWorld);
    }

    private static boolean canAttempt(StructurePlacement placement, Registry<StructureSet> sets) {
        // Arbitrary overrides can ignore the base frequency; do not guess about them.
        if (placement.getClass() != RandomSpreadStructurePlacement.class
                && placement.getClass() != ConcentricRingsStructurePlacement.class) return true;
        float frequency = ((StructurePlacementAccessor) placement).explorerscompass$getFrequency();
        if (!MODS.structurifyLoaded()) return frequency > 0;
        var ids = sets.stream().filter(set -> set.placement() == placement).map(sets::getId).toList();
        return ids.isEmpty() || ids.stream().anyMatch(id -> MODS.frequencyPermits(id, frequency));
    }
}
