package com.chaosthedude.explorerscompass.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public final class StructureLanding {
    public record Landing(Vec3d position, EntityPose pose) {}

    static final int RADIUS = 2;
    private static final List<BlockPos> OFFSETS = columnOffsets();
    private static final List<EntityPose> POSES = List.of(EntityPose.STANDING, EntityPose.CROUCHING, EntityPose.SWIMMING);

    private StructureLanding() {}

    public static Landing find(ServerPlayerEntity player, ServerWorld world, BlockPos target, List<BlockBox> pieces) {
        var preferred = StructureDestination.refine(pieces, target);
        for (BlockPos offset : OFFSETS) {
            int x = target.getX() + offset.getX();
            int z = target.getZ() + offset.getZ();
            var column = pieces.stream().filter(piece -> piece.intersectsXZ(x, z, x, z)).toList();
            if (column.isEmpty()) continue;
            Landing landing = findInColumn(player, world, column, x, z, preferred.pos().getY());
            if (landing != null) return landing;
        }

        // The world-level height query reports the dimension bottom for an unloaded chunk.
        var chunk = world.getChunk(target);
        int top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, target.getX() & 15, target.getZ() & 15) + 1;
        return top > world.getBottomY()
                ? new Landing(new Vec3d(target.getX() + 0.5, top, target.getZ() + 0.5), null) : null;
    }

    private static Landing findInColumn(ServerPlayerEntity player, ServerWorld world, List<BlockBox> pieces,
            int x, int z, int preferredY) {
        int minY = world.getTopY();
        int maxY = world.getBottomY() - 1;
        for (BlockBox piece : pieces) {
            minY = Math.min(minY, piece.getMinY());
            maxY = Math.max(maxY, piece.getMaxY());
        }
        minY = Math.max(minY, world.getBottomY());
        maxY = Math.min(maxY, world.getTopY() - 1);
        if (minY > maxY) return null;

        int centerY = MathHelper.clamp(preferredY, minY, maxY);
        int maxDistance = Math.max(centerY - minY, maxY - centerY);
        for (int distance = 0; distance <= maxDistance; distance++) {
            if (centerY - distance >= minY) {
                Landing landing = check(player, world, pieces, new BlockPos(x, centerY - distance, z));
                if (landing != null) return landing;
            }
            if (distance > 0 && centerY + distance <= maxY) {
                Landing landing = check(player, world, pieces, new BlockPos(x, centerY + distance, z));
                if (landing != null) return landing;
            }
        }
        return null;
    }

    private static Landing check(ServerPlayerEntity player, ServerWorld world, List<BlockBox> pieces, BlockPos feet) {
        if (pieces.stream().noneMatch(piece -> piece.contains(feet))) return null;
        double floor = world.getDismountHeight(feet);
        if (!Double.isFinite(floor) || floor < 0 || floor >= 1) return null;

        double x = feet.getX() + 0.5;
        double y = feet.getY() + floor;
        double z = feet.getZ() + 0.5;
        // The height query can pick up a shape off-center. Check for support directly under the landing.
        Box support = new Box(x - 0.01, y - 0.001, z - 0.01, x + 0.01, y, z + 0.01);
        if (!world.getBlockCollisions(player, support).iterator().hasNext()) return null;

        for (EntityPose pose : POSES) {
            Box body = player.getDimensions(pose).getBoxAt(x, y, z);
            if (body.maxY > world.getTopY() || !world.getWorldBorder().contains(body)
                    || pieces.stream().noneMatch(piece -> contains(piece, body))
                    || !world.isSpaceEmpty(player, body) || hasHazard(world, body)) continue;
            return new Landing(new Vec3d(x, y, z), pose);
        }
        return null;
    }

    private static boolean contains(BlockBox piece, Box body) {
        return body.minX >= piece.getMinX() && body.maxX <= piece.getMaxX() + 1.0
                && body.minY >= piece.getMinY() && body.maxY <= piece.getMaxY() + 1.0
                && body.minZ >= piece.getMinZ() && body.maxZ <= piece.getMaxZ() + 1.0;
    }

    private static boolean hasHazard(ServerWorld world, Box body) {
        var min = BlockPos.ofFloored(body.minX, body.minY - 0.001, body.minZ);
        var max = BlockPos.ofFloored(body.maxX - 0.001, body.maxY - 0.001, body.maxZ - 0.001);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            var state = world.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || EntityType.PLAYER.isInvalidSpawn(state)
                    || state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL)
                    || state.isOf(Blocks.END_GATEWAY) || state.isOf(Blocks.POINTED_DRIPSTONE)) return true;
        }
        return false;
    }

    private static List<BlockPos> columnOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) offsets.add(new BlockPos(x, 0, z));
        }
        offsets.sort(Comparator.comparingInt(pos -> pos.getX() * pos.getX() + pos.getZ() * pos.getZ()));
        return List.copyOf(offsets);
    }
}
