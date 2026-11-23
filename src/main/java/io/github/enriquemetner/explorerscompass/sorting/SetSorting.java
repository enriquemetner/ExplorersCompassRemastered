package io.github.enriquemetner.explorerscompass.sorting;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class SetSorting implements ISorting {
	
	@Override
	public int compare(Identifier id1, Identifier id2) {
		return ExplorersCompass.structureIDsToSetIDs.get(id1).compareTo(ExplorersCompass.structureIDsToSetIDs.get(id2));
	}

	@Override
	public String getValue(Identifier id) {
		return StructureUtils.formatIdPath(ExplorersCompass.structureIDsToSetIDs.get(id));
	}

	@Override
	public ISorting next() {
		return new NameSorting();
	}

	@Override
	public String getLocalizedName() {
		return "Set";
	}

}
