package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import java.text.Collator;
import java.util.function.Function;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public enum SortCriterion {
	NAME("Name", StructureUtils::getStructureName),
	SOURCE("Source", StructureUtils::getStructureSource),
	DIMENSION("Dimension", id -> StructureUtils.structureDimensionsToString(
			ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id))),
	SET("Set", id -> StructureUtils.formatIdPath(ExplorersCompass.structureIDsToSetIDs.get(id)));

	private final String label;
	private final Function<Identifier, String> displayValue;

	SortCriterion(String label, Function<Identifier, String> displayValue) {
		this.label = label;
		this.displayValue = displayValue;
	}

	public int compare(Identifier first, Identifier second, Collator collator) {
		return collator.compare(displayValue.apply(first), displayValue.apply(second));
	}

	public Text getLabel() {
		return Text.literal(label);
	}
}
