package io.github.enriquemetner.explorerscompass.gui;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import com.google.common.collect.ListMultimap;

import net.minecraft.util.Identifier;

/** Filters are client-session preferences. An empty set leaves that category unrestricted. */
public final class FilterSelections {
	public static final Set<String> sources = new HashSet<>();
	public static final Set<Identifier> dimensions = new HashSet<>();
	public static final Set<Identifier> sets = new HashSet<>();
	private static boolean currentSelected;
	private static boolean excludeVisited;
	private static boolean excludeLocked;
    private static boolean newlyUnlocked;
    public static void clearNewlyUnlocked() { newlyUnlocked = false; }
    public static boolean newlyUnlocked() { return newlyUnlocked; }
    public static void toggleNewlyUnlocked() { newlyUnlocked = !newlyUnlocked; }
	private static Identifier currentDimension;

	private FilterSelections() {
	}

	public static void clear() {
		sources.clear();
		dimensions.clear();
		sets.clear();
		currentSelected = false;
		excludeVisited = false;
		excludeLocked = false;
        newlyUnlocked = false;
	}

	public static boolean isEmpty() {
		return sources.isEmpty() && dimensions.isEmpty() && !currentSelected && sets.isEmpty() && !excludeVisited && !excludeLocked && !newlyUnlocked;
	}

	public static boolean excludeVisited() {
		return excludeVisited;
	}

	public static void toggleExcludeVisited() {
		excludeVisited = !excludeVisited;
	}

	public static boolean excludeLocked() {
		return excludeLocked;
	}

	public static void toggleExcludeLocked() {
		excludeLocked = !excludeLocked;
	}

	public static boolean isCurrentSelected() {
		return currentSelected;
	}

	public static void openedInDimension(Identifier dimension) {
		currentDimension = dimension;
	}

	public static void toggleCurrent() {
		currentSelected = !currentSelected;
	}

	public static void toggleDimension(Identifier dimension) {
		if (!dimensions.add(dimension)) {
			dimensions.remove(dimension);
		}
	}

	record AvailableOptions(Set<String> sources, Set<Identifier> dimensions, Set<Identifier> sets) {
	}

	static AvailableOptions availableOptions(List<Identifier> structures, Map<Identifier, Identifier> setsByStructure,
			ListMultimap<Identifier, Identifier> dimensionsByStructure, Predicate<Identifier> isEligible) {
		AvailableOptions available = new AvailableOptions(new HashSet<>(), new HashSet<>(), new HashSet<>());
		for (Identifier id : structures) {
			if (!isEligible.test(id) || !matchesNewlyUnlocked(id)) {
				continue;
			}
			String source = id.getNamespace();
			Identifier set = setsByStructure.get(id);
			List<Identifier> structureDimensions = dimensionsByStructure.get(id);
			boolean sourceMatches = matchesSource(source);
			boolean setMatches = matchesSet(set);
			boolean dimensionMatches = matchesDimensions(structureDimensions);
			// Each option replaces the restriction from its own column for this check.
			if (setMatches && dimensionMatches) {
				available.sources().add(source);
			}
			if (sourceMatches && setMatches) {
				available.dimensions().addAll(structureDimensions);
			}
			if (sourceMatches && dimensionMatches && set != null) {
				available.sets().add(set);
			}
		}
		return available;
	}

	public static boolean matches(Identifier structureId) {
		return matchesNewlyUnlocked(structureId)
				&& matchesSource(structureId.getNamespace())
				&& matchesSet(ExplorersCompass.structureIDsToSetIDs.get(structureId))
				&& matchesDimensions(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureId));
	}

	private static boolean matchesNewlyUnlocked(Identifier id) {
		return !newlyUnlocked || ClientPacketHandlers.latestUnlockedStructures.contains(id);
	}

	private static boolean matchesSource(String source) {
		return sources.isEmpty() || sources.contains(source);
	}

	private static boolean matchesSet(Identifier set) {
		return sets.isEmpty() || sets.contains(set);
	}

	private static boolean matchesDimensions(List<Identifier> structureDimensions) {
		if (dimensions.isEmpty() && !currentSelected) {
			return true;
		}
		for (Identifier dimension : structureDimensions) {
			if (dimensions.contains(dimension) || (currentSelected && dimension.equals(currentDimension))) {
				return true;
			}
		}
		return false;
	}
}
