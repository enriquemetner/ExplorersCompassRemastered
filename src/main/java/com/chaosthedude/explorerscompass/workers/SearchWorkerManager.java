package com.chaosthedude.explorerscompass.workers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;

import com.chaosthedude.explorerscompass.util.StructureUtils;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;

public class SearchWorkerManager {

	private final String id = RandomStringUtils.random(8, "0123456789abcdef");

	private List<SearchWorker> workers;
	// Keep workers and their cursors across radius stages; index advances within the current stage.
	private int index;

	public SearchWorkerManager() {
		workers = new ArrayList<SearchWorker>();
	}

	public void createWorkers(ServerWorld world, PlayerEntity player, ItemStack stack, List<Structure> structures, List<Structure> savedStructures, Identifier structureOrSetId, boolean excludeVisited, boolean creativeBypass, BlockPos startPos) {
		workers.clear();
		index = 0;

		Map<StructurePlacement, List<Structure>> placementToStructuresMap = new Object2ObjectArrayMap<>();

		for (Structure structure : structures) {
			for (StructurePlacement structureplacement : world.getChunkManager().getStructurePlacementCalculator().getPlacements(StructureUtils.getEntryForStructure(world, structure))) {
				placementToStructuresMap.computeIfAbsent(structureplacement, (holderSet) -> {
					return new ObjectArrayList<Structure>();
				}).add(structure);
			}
		}

        if (!placementToStructuresMap.isEmpty() || !savedStructures.isEmpty()) {
            SearchWorker worker = new SearchWorker(world, player, stack, startPos,
                    placementToStructuresMap, savedStructures, structureOrSetId, excludeVisited, id);
            worker.setCreativeBypass(creativeBypass);
            workers.add(worker);
        }
	}

	public void setRadiusLimit(int radiusLimit) {
		for (SearchWorker worker : workers) worker.setRadiusLimit(radiusLimit);
		index = 0;
	}

	// Returns true if a worker starts, false otherwise
	public boolean start() {
		while (index < workers.size()) {
			SearchWorker worker = workers.get(index);
			if (worker.hasWork()) {
				worker.start();
				return true;
			}
			index++;
		}
		return false;
	}

	public void pop() {
		index++;
	}

    public boolean ownsWorker(SearchWorker worker) {
        return workers.contains(worker);
    }

	public void stop() {
		for (SearchWorker worker : workers) {
			worker.stop();
		}
	}

	public void clear() {
		workers.clear();
		index = 0;
	}

}
