package io.github.enriquemetner.explorerscompass.sorting;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class DimensionSorting implements ISorting {
	
	@Override
	public int compare(Identifier id1, Identifier id2) {
		return StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id1)).compareTo(StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id2)));
	}

	@Override
	public String getValue(Identifier id) {
		return StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id));
	}

	@Override
	public ISorting next() {
		return new SetSorting();
	}

	@Override
	public String getLocalizedName() {
		return "Dimension";
	}

}
