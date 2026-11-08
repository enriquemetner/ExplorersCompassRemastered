package com.chaosthedude.explorerscompass.util;

import java.util.List;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.mixins.StructureChunkStorageAccessor;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;

public final class StructureDestination {
    public record Target(BlockPos pos, BlockBox piece) {}

    private StructureDestination() {}

    /** Read an existing start without requesting a chunk status or creating a chunk. */
    public static StructureStart readStart(ServerWorld world, ChunkPos pos, Structure structure) {
        if (world == null || structure == null) return null;
        var storage = world.getChunkManager().threadedAnvilChunkStorage;
        var accessor = (StructureChunkStorageAccessor) storage;
        try {
            var holder = accessor.explorerscompass$getCurrentChunkHolder(pos.toLong());
            var chunk = holder == null ? null : holder.getCurrentChunk();
            if (chunk != null && chunk.getStatus().isAtLeast(ChunkStatus.STRUCTURE_STARTS)) {
                return validStart(chunk.getStructureStart(structure), pos, structure);
            }

            var id = StructureUtils.getIDForStructure(world, structure);
            if (id == null) return null;
            var saved = storage.getNbt(pos).join().orElse(null);
            if (saved == null) return null;
            var starts = accessor.explorerscompass$updateChunkNbt(saved.copy())
                    .getCompound("structures").getCompound("starts");
            if (!starts.contains(id.toString())) return null;
            var start = StructureStart.fromNbt(StructureContext.from(world), starts.getCompound(id.toString()), world.getSeed());
            return validStart(start, pos, structure);
        } catch (RuntimeException e) {
            ExplorersCompass.LOGGER.warn("Could not read structure pieces in {} at {}", world.getRegistryKey().getValue(), pos, e);
            return null;
        }
    }

    private static StructureStart validStart(StructureStart start, ChunkPos pos, Structure structure) {
        return start != null && start.hasChildren() && start.getStructure() == structure && start.getPos().equals(pos)
                ? start : null;
    }

    public static List<BlockBox> pieces(StructureStart start) {
        return start == null ? List.of() : start.getChildren().stream().map(StructurePiece::getBoundingBox).toList();
    }

    public static Target refine(List<BlockBox> pieces, BlockPos fallback) {
        var bounds = BlockBox.encompass(pieces).orElse(null);
        if (bounds == null) return new Target(fallback, null);

        // Work in half-block units so an odd span doesn't round the center before comparing distances.
        long centerX = (long) bounds.getMinX() + bounds.getMaxX();
        long centerZ = (long) bounds.getMinZ() + bounds.getMaxZ();
        BlockBox nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (BlockBox piece : pieces) {
            long dx = clamp(centerX, 2L * piece.getMinX(), 2L * piece.getMaxX()) - centerX;
            long dz = clamp(centerZ, 2L * piece.getMinZ(), 2L * piece.getMaxZ()) - centerZ;
            long distance = dx * dx + dz * dz;
            if (nearest == null || distance < nearestDistance) {
                nearest = piece;
                nearestDistance = distance;
            }
        }

        int insetX = nearestDistance != 0 && nearest.getMaxX() - nearest.getMinX() >= 2 ? 1 : 0;
        int insetZ = nearestDistance != 0 && nearest.getMaxZ() - nearest.getMinZ() >= 2 ? 1 : 0;
        int x = (int) clamp(Math.floorDiv(centerX, 2), nearest.getMinX() + insetX, nearest.getMaxX() - insetX);
        int z = (int) clamp(Math.floorDiv(centerZ, 2), nearest.getMinZ() + insetZ, nearest.getMaxZ() - insetZ);
        return new Target(new BlockPos(x, nearest.getCenter().getY(), z), nearest);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }
}
