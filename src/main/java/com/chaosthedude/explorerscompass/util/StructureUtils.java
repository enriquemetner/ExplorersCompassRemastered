package com.chaosthedude.explorerscompass.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.WordUtils;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureSet.WeightedEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

public class StructureUtils {

	public static Map<Identifier, Identifier> getStructureIDsToSetIDs(ServerWorld world) {
		Map<Identifier, Identifier> structureIDsToSetIDs = new HashMap<Identifier, Identifier>();
		for (Structure structure : getStructureRegistry(world)) {
			structureIDsToSetIDs.put(getIDForStructure(world, structure), getSetForStructure(world, structure));
		}
		return structureIDsToSetIDs;
	}

	public static Identifier getSetForStructure(ServerWorld world, Structure structure) {
		Registry<StructureSet> registry = getStructureSetRegistry(world);
		for (StructureSet set : registry) {
			for (WeightedEntry entry : set.structures()) {
				if (entry.structure().value().equals(structure)) {
					return registry.getId(set);
				}
			}
		}
		return new Identifier(ExplorersCompass.MODID, "none");
	}

	public static List<Identifier> getStructuresForSet(ServerWorld world, Identifier setId) {
		List<Identifier> structureIds = new ArrayList<Identifier>();
		Registry<StructureSet> registry = getStructureSetRegistry(world);
		StructureSet set = registry.get(setId);
		if (set != null) {
			for (WeightedEntry entry : set.structures()) {
				Identifier structureId = getIDForStructure(world, entry.structure().value());
				if (structureId != null) {
					structureIds.add(structureId);
				}
			}
		}
		return structureIds;
	}

	public static Identifier getIDForStructure(ServerWorld world, Structure structure) {
		return getStructureRegistry(world).getId(structure);
	}

	public static Structure getStructureForID(ServerWorld world, Identifier id) {
		return getStructureRegistry(world).get(id);
	}

	public static RegistryEntry<Structure> getEntryForStructure(ServerWorld world, Structure structure) {
		Optional<RegistryKey<Structure>> optional = getStructureRegistry(world).getKey(structure);
		if (optional.isPresent()) {
			return getStructureRegistry(world).getEntry(optional.get()).get();
		}
		return null;
	}

	public static List<Identifier> getAllowedStructureIDs(ServerWorld world) {
		return getEligibleStructureIDs(world).stream().filter(id -> !structureIdIsExcluded(id))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	public static java.util.Set<Identifier> getEligibleStructureIDs(ServerWorld world) {
		var registry = getStructureRegistry(world);
		var eligible = new java.util.LinkedHashSet<Identifier>();
		for (Structure structure : registry) {
			Identifier id = registry.getId(structure);
			if (id != null && !id.getNamespace().isEmpty() && !id.getPath().isEmpty()
					&& !structureIsHidden(world, structure)) eligible.add(id);
		}
		return eligible;
	}

	public static boolean structureIsExcluded(ServerWorld world, Structure structure) {
		return structureIdIsExcluded(getIDForStructure(world, structure));
	}

	public static boolean structureIdIsExcluded(Identifier id) {
		if (id == null) return false;
		boolean whitelist = !ExplorersCompassConfig.structureWhitelist.isEmpty();
		List<String> patterns = whitelist ? ExplorersCompassConfig.structureWhitelist : ExplorersCompassConfig.structureBlacklist;
		for (String structureKey : patterns) {
			if (structureIdPattern(structureKey).matcher(id.toString()).matches()) {
				return !whitelist;
			}
		}
		return whitelist;
	}

	public static boolean structureIsHidden(ServerWorld world, Structure structure) {
		RegistryEntry<Structure> entry = getEntryForStructure(world, structure);
		if (entry != null) {
			return entry.streamTags().anyMatch(tag -> tag.id().toString().equals("c:hidden_from_locator_selection"));
		}
		return false;
	}

	public static List<Identifier> getGeneratingDimensionIDs(ServerWorld serverWorld, Structure structure) {
		final List<Identifier> dimensions = new ArrayList<Identifier>();
		for (ServerWorld world : serverWorld.getServer().getWorlds()) {
			ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
			Set<RegistryEntry<Biome>> biomeSet = chunkGenerator.getBiomeSource().getBiomes();
			if (!structure.getValidBiomes().stream().noneMatch(biomeSet::contains)) {
				dimensions.add(world.getRegistryKey().getValue());
			}
		}
		return dimensions;
	}

	public static ListMultimap<Identifier, Identifier> getGeneratingDimensionIDsForAllowedStructures(ServerWorld serverWorld, List<Identifier> allowedStructures) {
		ListMultimap<Identifier, Identifier> dimensionsForAllowedStructures = ArrayListMultimap.create();
		for (Identifier id : allowedStructures) {
			Structure structure = getStructureForID(serverWorld, id);
			dimensionsForAllowedStructures.putAll(id, getGeneratingDimensionIDs(serverWorld, structure));
		}
		return dimensionsForAllowedStructures;
	}

	public static int getHorizontalDistanceToLocation(PlayerEntity player, int x, int z) {
		return getHorizontalDistanceToLocation(player.getBlockPos(), x, z);
	}

	public static int getHorizontalDistanceToLocation(BlockPos startPos, int x, int z) {
		return (int) MathHelper.sqrt((float) startPos.getSquaredDistance(new BlockPos(x, startPos.getY(), z)));
	}

	public static String getStructureName(Identifier id) {
		if (id == null) return "";
		String key = "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		Language language = Language.getInstance();
		return language.hasTranslation(key) ? language.get(key) : formatIdPath(id);
	}

	public static String formatIdPath(Identifier id) {
		if (id == null) {
			return "";
		}
		return WordUtils.capitalize(id.getPath().replace('_', ' '));
	}

	@Environment(EnvType.CLIENT)
	public static String getStructureSource(Identifier id) {
		if (id == null) {
			return "";
		}
		String registryEntry = id.toString();
		String modid = registryEntry.substring(0, registryEntry.indexOf(":"));
		if (modid.equals("minecraft")) {
			return "Minecraft";
		}
		Optional<? extends ModContainer> sourceContainer = FabricLoader.getInstance().getModContainer(modid);
		if (sourceContainer.isPresent()) {
			return sourceContainer.get().getMetadata().getName();
		}
		return modid;
	}

	@Environment(EnvType.CLIENT)
	public static String structureDimensionsToString(List<Identifier> dimensions) {
		String str = "";
		if (dimensions != null && dimensions.size() > 0) {
			str = getDimensionName(dimensions.get(0));
			for (int i = 1; i < dimensions.size(); i++) {
				str += ", " + getDimensionName(dimensions.get(i));
			}
		}
		return str;
	}

	@Environment(EnvType.CLIENT)
	public static String getDimensionName(Identifier dimensionKey) {
		if (dimensionKey == null) {
			return "";
		}
		return WordUtils.capitalize(dimensionKey.getPath().replace('_', ' '));
	}

	private static Registry<Structure> getStructureRegistry(ServerWorld world) {
		return world.getRegistryManager().get(RegistryKeys.STRUCTURE);
	}

	private static Registry<StructureSet> getStructureSetRegistry(ServerWorld world) {
		return world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET);
	}

	public static Pattern structureIdPattern(String selector) {
		String glob = selector.contains(":") ? selector : "minecraft:" + selector;
		StringBuilder regex = new StringBuilder("^");
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*') regex.append(".*");
			else regex.append(Pattern.quote(String.valueOf(c)));
		}
		return Pattern.compile(regex.append('$').toString());
	}
}
