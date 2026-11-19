package io.github.enriquemetner.explorerscompass.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.unlocking.PartySharing;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

public final class CompassTeleport {
    record Match(Identifier dimension, ChunkPos startChunk, int distance) {}
    private record Destination(ServerWorld world, ChunkPos startChunk) {}

    private CompassTeleport() {}

    public static void toHeldTarget(ServerPlayerEntity player) throws CommandSyntaxException {
        if (!PlayerUtils.canTeleport(player)) throw error("You don't have permission to teleport with the compass");
        var compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        var stack = ItemUtils.getHeldItem(player, compass);
        if (stack.isEmpty()) throw error("Hold an Explorer's Compass to teleport to its target");
        compass.ensureHolder(player, stack);
        PartySharing.synchronize(player.getServer());
        compass.validateTarget(player, stack);
        if (compass.getState(stack) != CompassState.FOUND) throw error("The compass has no located structure");

        var nbt = stack.getOrCreateNbt();
        var id = compass.getStructureID(stack);
        if (!UnlockingRules.isUnlocked(player.getServer(), player.getUuid(), id)
                && !(player.getAbilities().creativeMode && nbt.getBoolean("CreativeBypass"))) {
            throw error("The compass's target structure is locked");
        }

        var target = new BlockPos(compass.getFoundStructureX(stack), 0, compass.getFoundStructureZ(stack));
        var structure = StructureUtils.getStructureForID(player.getServerWorld(), id);
        var start = nbt.contains(CompassSearchData.FOUND_START_CHUNK, NbtElement.LONG_TYPE)
                ? new ChunkPos(nbt.getLong(CompassSearchData.FOUND_START_CHUNK)) : null;
        Destination destination;
        if (nbt.contains("FoundDimension")) {
            var dimension = Identifier.tryParse(nbt.getString("FoundDimension"));
            var world = dimension == null ? null : player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension));
            if (world == null) throw error("The compass's saved destination dimension is unavailable");
            destination = new Destination(world, start);
        } else {
            var inferred = resolve(player.getServerWorld(), id, structure, target);
            destination = start == null ? inferred : new Destination(inferred.world(), start);
        }
        teleport(player, destination, structure, target);
    }

    public static void toCoordinates(ServerPlayerEntity player, Identifier id, BlockPos target) throws CommandSyntaxException {
        var world = player.getServerWorld();
        var structure = StructureUtils.getStructureForID(world, id);
        if (structure == null || StructureUtils.structureIdIsExcluded(id)) throw error("Unknown structure: " + id);
        teleport(player, resolve(world, id, structure, target), structure, target);
    }

    private static Destination resolve(ServerWorld current, Identifier id, Structure structure, BlockPos target)
            throws CommandSyntaxException {
        var server = current.getServer();
        List<Match> matches = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            Match match = matchInWorld(world, structure, target);
            if (match != null) matches.add(match);
        }
        Match match = selectMatch(matches, current.getRegistryKey().getValue());
        if (match != null) {
            return new Destination(server.getWorld(RegistryKey.of(RegistryKeys.WORLD, match.dimension())), match.startChunk());
        }

        var entry = StructureUtils.getEntryForStructure(current, structure);
        var dimensions = StructureUtils.getGeneratingDimensionIDs(current, structure).stream()
                .map(dimension -> server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension)))
                .filter(world -> world != null && !world.getChunkManager().getStructurePlacementCalculator().getPlacements(entry).isEmpty())
                .toList();
        if (dimensions.size() != 1) throw error("Could not determine the destination dimension for " + id);
        return new Destination(dimensions.get(0), null);
    }

    private static Match matchInWorld(ServerWorld world, Structure structure, BlockPos target) {
        Match best = null;
        for (var start : StructureDestination.readNearbyStarts(world, target, structure)) {
            int distance = matchDistance(StructureDestination.pieces(start), target);
            if (distance != Integer.MAX_VALUE && (best == null || distance < best.distance())) {
                best = new Match(world.getRegistryKey().getValue(), start.getPos(), distance);
            }
        }
        return best;
    }

    static int matchDistance(List<BlockBox> pieces, BlockPos target) {
        int x = target.getX(), z = target.getZ(), radius = StructureLanding.RADIUS;
        int nearest = Integer.MAX_VALUE;
        for (BlockBox piece : pieces) {
            if (!piece.intersectsXZ(x - radius, z - radius, x + radius, z + radius)) continue;
            int dx = x - Math.max(piece.getMinX(), Math.min(x, piece.getMaxX()));
            int dz = z - Math.max(piece.getMinZ(), Math.min(z, piece.getMaxZ()));
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        if (nearest == 0) {
            BlockPos refined = StructureDestination.refine(pieces, target).pos();
            if (refined.getX() == x && refined.getZ() == z) return -1;
        }
        return nearest;
    }

    static Match selectMatch(List<Match> matches, Identifier current) throws CommandSyntaxException {
        if (matches.isEmpty()) return null;
        int closest = matches.stream().mapToInt(Match::distance).min().orElseThrow();
        List<Match> best = matches.stream().filter(match -> match.distance() == closest).toList();
        if (best.size() == 1) return best.get(0);
        for (Match match : best) if (match.dimension().equals(current)) return match;
        throw error("The teleport destination matches structures in multiple dimensions");
    }

    private static void teleport(ServerPlayerEntity player, Destination destination, Structure structure, BlockPos target)
            throws CommandSyntaxException {
        var world = destination.world();
        // Some structure pieces adjust their bounds as their blocks are placed.
        world.getChunk(target);
        var startChunk = destination.startChunk();
        if (startChunk == null) {
            Match match = matchInWorld(world, structure, target);
            if (match != null) startChunk = match.startChunk();
        }
        var start = startChunk == null ? null : StructureDestination.readStart(world, startChunk, structure);
        var landing = StructureLanding.find(player, world, target, StructureDestination.pieces(start));
        if (landing == null) {
            throw new SimpleCommandExceptionType(Text.translatable("message.explorerscompass.no_teleport_destination")).create();
        }

        var pos = landing.position();
        player.teleport(world, pos.x, pos.y, pos.z, Set.of(), player.getYaw(), player.getPitch());
        if (landing.pose() != null) player.setPose(landing.pose());
        if (!player.isFallFlying()) {
            player.setVelocity(player.getVelocity().getX(), 0, player.getVelocity().getZ());
            player.setOnGround(true);
        }
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Text.literal(message)).create();
    }
}
