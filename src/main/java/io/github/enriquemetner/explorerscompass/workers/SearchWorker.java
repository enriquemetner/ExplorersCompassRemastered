package io.github.enriquemetner.explorerscompass.workers;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.mixins.SearchChunkManagerAccessor;
import io.github.enriquemetner.explorerscompass.mixins.StructureChunkStorageAccessor;
import io.github.enriquemetner.explorerscompass.storage.EmptyChunkCache;
import io.github.enriquemetner.explorerscompass.storage.EmptyChunkStorage;
import io.github.enriquemetner.explorerscompass.storage.SavedStructureIndex;
import io.github.enriquemetner.explorerscompass.storage.SavedStructureReader;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.util.StructureDestination;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import io.github.enriquemetner.explorerscompass.util.VisitedStructures;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.placement.*;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;

public final class SearchWorker implements WorldWorkerManager.IWorker {

    private record Match(Structure structure, ChunkPos startChunk, StructureStart start) {}

	private static final int PROGRESS_INTERVAL_BLOCKS = 100;

	private String managerId;
    private final EmptyChunkCache storageCache = new EmptyChunkCache();
	private ServerWorld level;
	private PlayerEntity player;
	private ItemStack stack;
	private BlockPos startPos;
	private BlockPos currentPos;
	private StructurePlacement placement;
	private List<Structure> structureSet;
	private Identifier structureOrSetId;
	private boolean excludeVisited;
	private boolean creativeBypass;
	private int samples;
	private boolean finished;
	private int lastRadiusThreshold;
	private int radiusLimit = ExplorersCompassConfig.maxSearchRadius;


	public void start() {
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": SearchWorker starting with " + radiusLimit + " radius limit, no candidate limit");
			WorldWorkerManager.addWorker(level.getServer(), this);
		}
	}

	public void setRadiusLimit(int radiusLimit) {
		this.radiusLimit = radiusLimit;
	}

	public void setCreativeBypass(boolean creativeBypass) {
		this.creativeBypass = creativeBypass;
	}

	private void reportProgress() {
		int radius = getRadius();
		int threshold = radius / PROGRESS_INTERVAL_BLOCKS;
		if (threshold > lastRadiusThreshold) {
			if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
				((ExplorersCompassItem) stack.getItem()).reportSearchProgress(player, stack, roundRadius(radius, PROGRESS_INTERVAL_BLOCKS));
			}
			lastRadiusThreshold = threshold;
		}
	}

	private Match getStructureGeneratingAt(ChunkPos chunkPos, Chunk loaded, boolean requested) {
		for (Structure structure : structureSet) {
            Identifier id = StructureUtils.getIDForStructure(level, structure);
            if (id == null || (!(creativeBypass && player.getAbilities().creativeMode)
					&& !io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.isUnlocked(level.getServer(), player.getUuid(), id))) continue;
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


	private boolean wasVisited(Structure structure, ChunkPos startChunk) {
		if (!excludeVisited) return false;
		Identifier structureId = StructureUtils.getIDForStructure(level, structure);
		return structureId != null && VisitedStructures.hasEffectiveVisit(level.getServer(), player.getUuid(),
			level.getRegistryKey().getValue(), structureId, startChunk);
	}

	private void succeed(Match match) {
        storageCache.clear();
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": SearchWorker succeeded with " + getRadius() + " radius, " + samples + " samples");
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
            StructureStart start = match.start() != null ? match.start()
                    : StructureDestination.readStart(level, match.startChunk(), match.structure());
            var pieces = StructureDestination.pieces(start);
            BlockPos pos = StructureDestination.refine(pieces, placement == null ? match.startChunk().getStartPos() : placement.getLocatePos(match.startChunk())).pos();
			((ExplorersCompassItem) stack.getItem()).succeed(player, stack, StructureUtils.getIDForStructure(level, match.structure()),
                    match.startChunk(), pos.getX(), pos.getZ(), BlockBox.encompass(pieces).orElse(null), samples, ExplorersCompassConfig.displayCoordinates, ExplorersCompassConfig.displayDistance);
		} else {
			ExplorersCompass.LOGGER.error("SearchWorkerManager " + managerId + ": SearchWorker found invalid compass after successful search");
		}
		finished = true;
	}

	private void fail() {
        storageCache.clear();
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": SearchWorker failed with " + getRadius() + " radius, " + samples + " samples");
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			((ExplorersCompassItem) stack.getItem()).fail(player, stack, structureOrSetId, getRadius(), samples);
		} else {
			ExplorersCompass.LOGGER.error("SearchWorkerManager " + managerId + ": SearchWorker found invalid compass after failed search");
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
        pendingTarget = null;
        pendingChunk = null;
        storageCache.clear();
		ExplorersCompass.LOGGER.info("SearchWorkerManager " + managerId + ": SearchWorker stopped with " + getRadius() + " radius, " + samples + " samples");
		finished = true;
	}

	private int getRadius() {
		return StructureUtils.getHorizontalDistanceToLocation(startPos, currentPos.getX(), currentPos.getZ());
	}

	private int roundRadius(int radius, int roundTo) {
		return ((int) radius / roundTo) * roundTo;
	}

    private record Target(StructurePlacement placement, List<Structure> structures, ChunkPos chunk, boolean saved) {}
    private final Map<Identifier, Structure> savedTargets = new HashMap<>();
    private final Map<Structure, List<StructurePlacement>> locateRules = new HashMap<>();
    private final SavedStructureIndex savedIndex;
    private int minX, maxX, minZ, maxZ;
    private boolean warnedReadFailure;
    private final NearestFirstQueue<Target> queue;

    SearchWorker(ServerWorld world, PlayerEntity player, ItemStack stack, BlockPos origin,
            Map<StructurePlacement, List<Structure>> placements, List<Structure> searchStructures, Identifier searchId,
            boolean excludeVisited, String managerId) {
        this.level = world;
        this.player = player;
        this.stack = stack;
        this.startPos = origin;
        this.structureSet = List.of();
        this.structureOrSetId = searchId;
        this.excludeVisited = excludeVisited && ExplorersCompassConfig.allowExcludeVisited;
        this.managerId = managerId;
        currentPos = origin;
        samples = 0;
        finished = !level.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
        queue = new NearestFirstQueue<>(origin.getX(), origin.getZ());
        boolean generate = !finished;
        if (!searchStructures.isEmpty()) finished = false;
        savedIndex = searchStructures.isEmpty() ? null : SavedStructureIndex.get(world);
        for (Structure structure : searchStructures) {
            Identifier id = StructureUtils.getIDForStructure(world, structure);
            if (id == null) continue;
            savedTargets.put(id, structure);
            var rules = List.copyOf(world.getChunkManager().getStructurePlacementCalculator()
                    .getPlacements(StructureUtils.getEntryForStructure(world, structure)));
            locateRules.put(structure, rules);
            for (var rule : rules) {
                BlockPos offset = rule.getLocatePos(new ChunkPos(0, 0));
                minX = Math.min(minX, offset.getX());
                maxX = Math.max(maxX, offset.getX());
                minZ = Math.min(minZ, offset.getZ());
                maxZ = Math.max(maxZ, offset.getZ());
            }
        }
        if (savedIndex != null) queue.addOverlappingGrid(512, 496 + maxX - minX, 496 + maxZ - minZ, minX, minZ, (x, z) -> {
            addSavedRegion(x, z);
        });
        if (generate) placements.forEach((rule, structures) -> {
            if (rule instanceof ConcentricRingsStructurePlacement rings) {
                List<ChunkPos> positions = world.getChunkManager().getStructurePlacementCalculator().getPlacementPositions(rings);
                if (positions != null) for (ChunkPos chunk : positions) queue.addCandidate(candidate(rule, structures, chunk));
            } else {
                BlockPos offset = rule.getLocatePos(new ChunkPos(0, 0));
                if (rule instanceof RandomSpreadStructurePlacement random) {
                    int spacing = random.getSpacing();
                    queue.addGrid(spacing * 16, (spacing - random.getSeparation() - 1) * 16,
                            offset.getX(), offset.getZ(), (x, z) -> candidate(rule, structures,
                                    random.getStartChunk(world.getSeed(), x * spacing, z * spacing)));
                } else {
                    // Unknown placement: conservatively check every chunk, still in global distance order.
                    queue.addGrid(16, 0, offset.getX(), offset.getZ(),
                            (x, z) -> candidate(rule, structures, new ChunkPos(x, z)));
                }
            }
        });
    }

    private NearestFirstQueue.Candidate<Target> candidate(StructurePlacement rule, List<Structure> structures, ChunkPos chunk) {
        BlockPos pos = rule.getLocatePos(chunk);
        return new NearestFirstQueue.Candidate<>(pos.getX(), pos.getZ(), new Target(rule, structures, chunk, false));
    }

    private void addSavedRegion(int x, int z) {
        ChunkPos base = new ChunkPos(x * 32, z * 32);
        var storage = level.getChunkManager().threadedAnvilChunkStorage;
        EmptyChunkStorage.RegionSnapshot snapshot = null;
        try {
            if (storage.getWorker() instanceof EmptyChunkStorage backend) snapshot = backend.explorerscompass$savedRegion(base);
        } catch (RuntimeException e) {
            warnReadFailure(base, e);
        }
        BitSet occupied = new BitSet(1024);
        if (snapshot == null) occupied.set(0, 1024);
        else occupied = BitSet.valueOf(snapshot.occupied());
        var access = (StructureChunkStorageAccessor) storage;
        for (var holders : List.of(access.explorerscompass$currentChunkHolders(), access.explorerscompass$chunksToUnload())) {
            for (var holder : holders.values()) {
                ChunkPos chunk = holder.getPos();
                if (chunk.getRegionX() == x && chunk.getRegionZ() == z) {
                    occupied.set(chunk.getRegionRelativeX() + (chunk.getRegionRelativeZ() << 5));
                }
            }
        }
        var region = snapshot;
        for (int slot = occupied.nextSetBit(0); slot >= 0; slot = occupied.nextSetBit(slot + 1)) {
            ChunkPos chunk = new ChunkPos(base.x + (slot & 31), base.z + (slot >>> 5));
            queue.addArea(chunk.getStartX() + (long) minX, chunk.getStartZ() + (long) minZ,
                    maxX - minX, maxZ - minZ, () -> addSavedStarts(chunk, region));
        }
    }

    private void addSavedStarts(ChunkPos chunk, EmptyChunkStorage.RegionSnapshot snapshot) {
        List<Identifier> ids;
        try {
            ids = SavedStructureReader.starts(level, chunk, snapshot, savedIndex);
        } catch (RuntimeException e) {
            warnReadFailure(chunk, e);
            return;
        }
        if (ids == null) return;
        for (Identifier id : ids) {
            Structure structure = savedTargets.get(id);
            if (structure == null || !canUseSaved(id, structure, chunk)) continue;
            var rules = locateRules.get(structure);
            if (rules.isEmpty()) {
                queue.addCandidate(new NearestFirstQueue.Candidate<>(chunk.getStartX(), chunk.getStartZ(),
                        new Target(null, List.of(structure), chunk, true)));
            } else {
                for (var rule : rules) {
                    BlockPos pos = rule.getLocatePos(chunk);
                    queue.addCandidate(new NearestFirstQueue.Candidate<>(pos.getX(), pos.getZ(),
                            new Target(rule, List.of(structure), chunk, true)));
                }
            }
        }
    }

    private boolean canUseSaved(Identifier id, Structure structure, ChunkPos chunk) {
        return ((creativeBypass && player.getAbilities().creativeMode)
                || UnlockingRules.isUnlocked(level.getServer(), player.getUuid(), id)) && !wasVisited(structure, chunk);
    }

    private void warnReadFailure(ChunkPos chunk, RuntimeException error) {
        if (warnedReadFailure) return;
        warnedReadFailure = true;
        ExplorersCompass.LOGGER.warn("Could not inspect saved structures near {} in {}", chunk,
                level.getRegistryKey().getValue(), error);
    }

    private Target pendingTarget;
    private CompletableFuture<Chunk> pendingChunk;

    private void requestCandidateChunk(ChunkPos pos) {
        pendingChunk = ((SearchChunkManagerAccessor) level.getChunkManager())
                .explorerscompass$requestChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true)
                .handle((result, error) -> {
                    if (error != null) {
                        ExplorersCompass.LOGGER.warn("Could not load structure starts at {}", pos, error);
                        return null;
                    }
                    return result.left().orElse(null);
                });
    }

    @Override public CompletableFuture<?> pendingWork() { return pendingChunk; }

    @Override public boolean hasWork() { return !finished; }
    private boolean canWorkAt(int radius) {
        return !finished && queue.nextDistanceSquared() < (long) radius * radius;
    }
    @Override public boolean doWork() {
        if (!hasWork()) return false;
        if (pendingChunk != null) {
            if (!pendingChunk.isDone()) return false;
            Chunk loaded = pendingChunk.getNow(null);
            Target target = pendingTarget;
            pendingChunk = null;
            pendingTarget = null;
            Match match = getStructureGeneratingAt(target.chunk, loaded, true);
            if (match != null) succeed(match);
            return hasWork();
        }
        if (!canWorkAt(radiusLimit)) {
            finished = true;
            fail();
            return false;
        }
        // This lower bound is the radius proven clear so far, not the farthest prepared candidate.
        int radius = (int) Math.sqrt(queue.nextDistanceSquared());
        currentPos = new BlockPos(startPos.getX() + radius, 0, startPos.getZ());
        reportProgress();
        var candidate = queue.advance();
        if (candidate != null) {
            Target target = candidate.value();
            placement = target.placement;
            structureSet = target.structures;
            samples++;
            Match result;
            if (target.saved) {
                Structure structure = structureSet.get(0);
                var id = StructureUtils.getIDForStructure(level, structure);
                var start = id != null && canUseSaved(id, structure, target.chunk)
                        ? StructureDestination.readStart(level, target.chunk, structure) : null;
                result = start == null ? null : new Match(structure, target.chunk, start);
            } else {
                result = getStructureGeneratingAt(target.chunk, null, false);
                if (pendingChunk != null) {
                    pendingTarget = target;
                    return false;
                }
            }
            if (result != null) succeed(result);
        }
        return hasWork();
    }
}
