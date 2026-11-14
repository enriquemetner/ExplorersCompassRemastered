package com.chaosthedude.explorerscompass.util;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.mixins.StructureChunkStorageAccessor;
import com.chaosthedude.explorerscompass.storage.EmptyChunkCache;
import com.chaosthedude.explorerscompass.storage.EmptyChunkStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;

public final class StructureDestination {
    public static final int MAX_BIOME_SAMPLES = 9;
    public record Target(BlockPos pos, BlockBox piece) {}

    private StructureDestination() {}

    /** Read an existing start without requesting a chunk status or creating a chunk. */
    public static StructureStart readStart(ServerWorld world, ChunkPos pos, Structure structure) {
        if (world == null || structure == null) return null;
        return readStart(world, pos, structure, null);
    }

    private static StructureStart readStart(ServerWorld world, ChunkPos pos, Structure structure, EmptyChunkCache emptyChunks) {
        try {
            var chunk = existingChunk(world, pos, ChunkStatus.STRUCTURE_STARTS);
            if (chunk != null) {
                return validStart(chunk.getStructureStart(structure), pos, structure);
            }

            var id = StructureUtils.getIDForStructure(world, structure);
            if (id == null) return null;
            var starts = readSavedStructures(world, pos, emptyChunks).getCompound("starts");
            if (!starts.contains(id.toString())) return null;
            var start = StructureStart.fromNbt(StructureContext.from(world), starts.getCompound(id.toString()), world.getSeed());
            return validStart(start, pos, structure);
        } catch (RuntimeException e) {
            ExplorersCompass.LOGGER.warn("Could not read structure pieces in {} at {}", world.getRegistryKey().getValue(), pos, e);
            return null;
        }
    }

    static List<StructureStart> readNearbyStarts(ServerWorld world, BlockPos target, Structure structure) {
        var id = StructureUtils.getIDForStructure(world, structure);
        if (id == null) return List.of();
        int radius = StructureLanding.RADIUS;
        Set<ChunkPos> candidates = new LinkedHashSet<>();
        EmptyChunkCache emptyChunks = new EmptyChunkCache();
        var min = new ChunkPos(target.add(-radius, 0, -radius));
        var max = new ChunkPos(target.add(radius, 0, radius));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                var pos = new ChunkPos(x, z);
                candidates.add(pos);
                try {
                    var chunk = existingChunk(world, pos, ChunkStatus.STRUCTURE_REFERENCES);
                    if (chunk != null) {
                        for (long reference : chunk.getStructureReferences(structure)) candidates.add(new ChunkPos(reference));
                    } else {
                        var references = readSavedStructures(world, pos, emptyChunks).getCompound("References");
                        for (long reference : references.getLongArray(id.toString())) candidates.add(new ChunkPos(reference));
                    }
                } catch (RuntimeException e) {
                    ExplorersCompass.LOGGER.warn("Could not read structure references in {} at {}", world.getRegistryKey().getValue(), pos, e);
                }
            }
        }
        return candidates.stream().sorted(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z))
                .map(pos -> readStart(world, pos, structure, emptyChunks)).filter(Objects::nonNull).toList();
    }

    public static Chunk existingChunk(ServerWorld world, ChunkPos pos, ChunkStatus status) {
        var storage = (StructureChunkStorageAccessor) world.getChunkManager().threadedAnvilChunkStorage;
        var holder = storage.explorerscompass$getCurrentChunkHolder(pos.toLong());
        if (holder == null) holder = storage.explorerscompass$chunksToUnload().get(pos.toLong());
        var chunk = holder == null ? null : holder.getCurrentChunk();
        return chunk != null && chunk.getStatus().isAtLeast(status) ? chunk : null;
    }

    private static NbtCompound readSavedStructures(ServerWorld world, ChunkPos pos, EmptyChunkCache emptyChunks) {
        var storage = world.getChunkManager().threadedAnvilChunkStorage;
        if (emptyChunks != null && storage.getWorker() instanceof EmptyChunkStorage knownStorage
                && knownStorage.explorerscompass$isKnownEmpty(emptyChunks, pos)) return new NbtCompound();
        var saved = storage.getNbt(pos).join().orElse(null);
        return saved == null ? new NbtCompound() : ((StructureChunkStorageAccessor) storage)
                .explorerscompass$updateChunkNbt(saved.copy()).getCompound("structures");
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

    public static List<Identifier> sampleBiomes(ServerWorld world, BlockBox bounds) {
        return sampleBiomes(bounds, pos -> world.getBiome(pos).getKey().map(key -> key.getValue()).orElse(null));
    }

    static List<Identifier> sampleBiomes(BlockBox bounds, Function<BlockPos, Identifier> lookup) {
        if (bounds == null) return List.of();
        Set<BlockPos> samples = new LinkedHashSet<>();
        samples.add(bounds.getCenter());
        for (int x : new int[] {bounds.getMinX(), bounds.getMaxX()}) {
            for (int y : new int[] {bounds.getMinY(), bounds.getMaxY()}) {
                for (int z : new int[] {bounds.getMinZ(), bounds.getMaxZ()}) {
                    samples.add(new BlockPos(x, y, z));
                }
            }
        }
        Set<Identifier> biomes = new LinkedHashSet<>();
        for (BlockPos sample : samples) {
            Identifier biome = lookup.apply(sample);
            if (biome != null) biomes.add(biome);
        }
        return List.copyOf(biomes);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }
}
