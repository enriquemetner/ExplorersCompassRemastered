package com.chaosthedude.explorerscompass.workers;

import java.util.List;
import com.chaosthedude.explorerscompass.storage.EmptyChunkCache;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.StructureDestination;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.util.VisitedStructures;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.StructurePresence;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;

public abstract class StructureSearchWorker<T extends StructurePlacement> implements WorldWorkerManager.IWorker {

    protected record Match(Structure structure, ChunkPos startChunk, StructureStart start) {}

	private static final int PROGRESS_INTERVAL_BLOCKS = 100;

	protected String managerId;
    private final EmptyChunkCache storageCache = new EmptyChunkCache();
	protected ServerWorld level;
	protected PlayerEntity player;
	protected ItemStack stack;
	protected BlockPos startPos;
	protected BlockPos currentPos;
	protected T placement;
	protected List<Structure> structureSet;
	protected Identifier structureOrSetId;
	protected boolean excludeVisited;
	protected boolean creativeBypass;
	protected int samples;
	protected boolean finished;
	protected int lastRadiusThreshold;
	protected int radiusLimit = ExplorersCompassConfig.maxSearchRadius;

	public StructureSearchWorker(ServerWorld level, PlayerEntity player, ItemStack stack, BlockPos startPos, T placement, List<Structure> structureSet, Identifier structureOrSetId, boolean excludeVisited, String managerId) {
		this.level = level;
		this.player = player;
		this.stack = stack;
		this.startPos = startPos;
		this.structureSet = structureSet;
		this.structureOrSetId = structureOrSetId;
		this.excludeVisited = excludeVisited && ExplorersCompassConfig.allowExcludeVisited;
		this.placement = placement;
		this.managerId = managerId;

		currentPos = startPos;
		samples = 0;

		finished = !level.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
	}

	public void start() {
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " starting with " + (shouldLogRadius() ? radiusLimit + " radius limit, " : "") + "no candidate limit");
			WorldWorkerManager.addWorker(level.getServer(), this);
		}
	}

	public void setRadiusLimit(int radiusLimit) {
		this.radiusLimit = radiusLimit;
	}

	public void setCreativeBypass(boolean creativeBypass) {
		this.creativeBypass = creativeBypass;
	}

	public boolean canWorkAt(int radiusLimit) {
		return !finished && getRadius() < radiusLimit;
	}

	@Override
	public boolean hasWork() {
		return canWorkAt(radiusLimit);
	}

	@Override
	public boolean doWork() {
		int radius = getRadius();
		int threshold = radius / PROGRESS_INTERVAL_BLOCKS;
		if (threshold > lastRadiusThreshold) {
			if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
				((ExplorersCompassItem) stack.getItem()).reportSearchProgress(player, stack, roundRadius(radius, PROGRESS_INTERVAL_BLOCKS));
			}
			lastRadiusThreshold = threshold;
		}
		return false;
	}

	protected Match getStructureGeneratingAt(ChunkPos chunkPos, Chunk loaded, boolean requested) {
		for (Structure structure : structureSet) {
            Identifier id = StructureUtils.getIDForStructure(level, structure);
            if (id == null || (!(creativeBypass && player.getAbilities().creativeMode)
					&& !com.chaosthedude.explorerscompass.unlocking.UnlockingRules.isUnlocked(level.getServer(), player.getUuid(), id))) continue;
			StructurePresence result = SearchPlacementFilter.lookup(chunkPos.toLong(), structure,
                    () -> placement.shouldGenerate(level.getChunkManager().getStructurePlacementCalculator(), chunkPos.x, chunkPos.z),
                    storageCache, () -> level.getStructureAccessor().getStructurePresence(chunkPos, structure, false));
			if (result != StructurePresence.START_NOT_PRESENT) {
				if (result == StructurePresence.START_PRESENT) {
					if (wasVisited(structure, chunkPos)) continue;
					StructureStart start = loaded == null ? null
                            : level.getStructureAccessor().getStructureStart(ChunkSectionPos.from(loaded), structure, loaded);
                    return new Match(structure, chunkPos, start != null && start.hasChildren() ? start : null);
				}

				if (!requested) {
                    requestCandidateChunk(chunkPos);
                    return null;
                }
                if (loaded == null) continue;
                Chunk chunk = loaded;
				StructureStart structureStart = level.getStructureAccessor().getStructureStart(ChunkSectionPos.from(chunk), structure, chunk);
				if (structureStart != null && structureStart.hasChildren() && !wasVisited(structure, structureStart.getPos())) {
					return new Match(structure, structureStart.getPos(), structureStart);
				}
			}
		}

		return null;
	}

    protected abstract void requestCandidateChunk(ChunkPos pos);

	protected boolean wasVisited(Structure structure, ChunkPos startChunk) {
		if (!excludeVisited) return false;
		Identifier structureId = StructureUtils.getIDForStructure(level, structure);
		return structureId != null && VisitedStructures.hasEffectiveVisit(level.getServer(), player.getUuid(),
			level.getRegistryKey().getValue(), structureId, startChunk);
	}

	protected void succeed(Match match) {
        storageCache.clear();
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " succeeded with " + (shouldLogRadius() ? getRadius() + " radius, " : "") + samples + " samples");
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
            StructureStart start = match.start() != null ? match.start()
                    : StructureDestination.readStart(level, match.startChunk(), match.structure());
            var pieces = StructureDestination.pieces(start);
            BlockPos pos = StructureDestination.refine(pieces, placement == null ? match.startChunk().getStartPos() : placement.getLocatePos(match.startChunk())).pos();
			((ExplorersCompassItem) stack.getItem()).succeed(player, stack, StructureUtils.getIDForStructure(level, match.structure()),
                    match.startChunk(), pos.getX(), pos.getZ(), BlockBox.encompass(pieces).orElse(null), samples, ExplorersCompassConfig.displayCoordinates, ExplorersCompassConfig.displayDistance);
		} else {
			ExplorersCompass.LOGGER.error("SearchWorkerManager " + managerId + ": " + getName() + " found invalid compass after successful search");
		}
		finished = true;
	}

	protected void fail() {
        storageCache.clear();
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " failed with " + (shouldLogRadius() ? getRadius() + " radius, " : "") + samples + " samples");
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			((ExplorersCompassItem) stack.getItem()).fail(player, stack, structureOrSetId, getRadius(), samples);
		} else {
			ExplorersCompass.LOGGER.error("SearchWorkerManager " + managerId + ": " + getName() + " found invalid compass after failed search");
			finished = true;
		}
	}

    @Override
    public void onSearchError(RuntimeException exception) {
        ExplorersCompass.LOGGER.error("Compass search {} for {} in {} failed at radius {}",
                managerId, structureOrSetId, level.getRegistryKey().getValue(), getRadius(), exception);
        ExplorersCompass.EXPLORERS_COMPASS_ITEM.abortFailedSearch(player, this);
    }

	public void stop() {
        storageCache.clear();
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": " + getName() + " stopped with " + (shouldLogRadius() ? getRadius() + " radius, " : "") + samples + " samples");
		finished = true;
	}

	protected int getRadius() {
		return StructureUtils.getHorizontalDistanceToLocation(startPos, currentPos.getX(), currentPos.getZ());
	}

	protected int roundRadius(int radius, int roundTo) {
		return ((int) radius / roundTo) * roundTo;
	}

	protected abstract String getName();

	protected abstract boolean shouldLogRadius();

}
