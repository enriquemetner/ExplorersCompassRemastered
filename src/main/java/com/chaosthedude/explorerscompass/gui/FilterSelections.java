package com.chaosthedude.explorerscompass.gui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.chaosthedude.explorerscompass.ExplorersCompass;

import net.minecraft.util.Identifier;

/** Filters are client-session preferences. An empty set leaves that category unrestricted. */
public final class FilterSelections {
	public static final Set<String> sources = new HashSet<>();
	public static final Set<Identifier> dimensions = new HashSet<>();
	public static final Set<Identifier> groups = new HashSet<>();
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
		groups.clear();
		currentSelected = false;
		excludeVisited = false;
		excludeLocked = false;
        newlyUnlocked = false;
	}

	public static boolean isEmpty() {
		return sources.isEmpty() && dimensions.isEmpty() && !currentSelected && groups.isEmpty() && !excludeVisited && !excludeLocked && !newlyUnlocked;
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

	public static boolean matches(Identifier structureId) {
        if (newlyUnlocked && !com.chaosthedude.explorerscompass.network.ClientPacketHandlers.latestUnlockedStructures.contains(structureId)) return false;
		if (!sources.isEmpty() && !sources.contains(structureId.getNamespace())) {
			return false;
		}
		Identifier group = ExplorersCompass.structureIDsToGroupIDs.get(structureId);
		if (!groups.isEmpty() && !groups.contains(group)) {
			return false;
		}
		if (dimensions.isEmpty() && !currentSelected) {
			return true;
		}
		List<Identifier> structureDimensions = ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureId);
		for (Identifier dimension : structureDimensions) {
			if (dimensions.contains(dimension) || (currentSelected && dimension.equals(currentDimension))) {
				return true;
			}
		}
		return false;
	}
}
