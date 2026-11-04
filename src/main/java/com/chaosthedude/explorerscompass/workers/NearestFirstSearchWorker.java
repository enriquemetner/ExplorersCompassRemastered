package com.chaosthedude.explorerscompass.workers;

import java.util.List;
import java.util.Map;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.placement.*;
import net.minecraft.world.gen.structure.Structure;

/** One coordinator orders all placement grids, including Search Listed, by horizontal locate distance. */
final class NearestFirstSearchWorker extends StructureSearchWorker<StructurePlacement> {
    private record Target(StructurePlacement placement, List<Structure> structures, ChunkPos chunk) {}
    private final NearestFirstQueue<Target> queue;
    private boolean stageComplete;

    NearestFirstSearchWorker(ServerWorld world, PlayerEntity player, ItemStack stack, BlockPos origin,
            Map<StructurePlacement, List<Structure>> placements, Identifier searchId,
            boolean excludeVisited, String managerId) {
        super(world, player, stack, origin, null, List.of(), searchId, excludeVisited, managerId);
        queue = new NearestFirstQueue<>(origin.getX(), origin.getZ());
        placements.forEach((rule, structures) -> {
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
        return new NearestFirstQueue.Candidate<>(pos.getX(), pos.getZ(), new Target(rule, structures, chunk));
    }

    @Override public void setRadiusLimit(int radius) { super.setRadiusLimit(radius); stageComplete = false; }
    @Override public boolean hasWork() { return !finished && !stageComplete; }
    @Override public boolean canWorkAt(int radius) {
        return !finished && samples < ExplorersCompassConfig.maxSamples
                && queue.nextDistanceSquared() < (long) radius * radius;
    }
    @Override public boolean doWork() {
        if (!hasWork()) return false;
        if (!canWorkAt(radiusLimit)) {
            stageComplete = true;
            fail();
            return false;
        }
        // This lower bound is the radius proven clear so far, not the farthest prepared candidate.
        int radius = (int) Math.sqrt(queue.nextDistanceSquared());
        currentPos = new BlockPos(startPos.getX() + radius, 0, startPos.getZ());
        super.doWork();
        var candidate = queue.advance();
        if (candidate != null) {
            Target target = candidate.value();
            placement = target.placement;
            structureSet = target.structures;
            samples++;
            var result = getStructureGeneratingAt(target.chunk);
            if (result != null) succeed(result.getFirst(), result.getSecond());
        }
        return hasWork();
    }
    @Override protected String getName() { return "NearestFirstSearchWorker"; }
    @Override protected boolean shouldLogRadius() { return true; }
}
