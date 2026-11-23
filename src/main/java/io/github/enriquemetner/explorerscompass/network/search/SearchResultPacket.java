package io.github.enriquemetner.explorerscompass.network.search;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.structure.StructureDestination;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SearchResultPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search_result");
    public static final int FOUND = 0, NOT_FOUND = 1, REJECTED = 2, WRONG_DIMENSION = 3, INSUFFICIENT_COST = 4, LOCKED = 5, BLACKLISTED = 6, NOT_WHITELISTED = 7, MULTI_COST = 8;

    public record Result(int requestId, int status, int radius, Identifier structureId, int foundX, int foundZ,
                         boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes,
                         Set<Identifier> validBiomes, Boolean visited, RequirementNode requirements, int balance) {}

    public SearchResultPacket(int requestId, int result, int radius, boolean canContinue, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, int validBiomeMask, Boolean visited) {
        super(Unpooled.buffer());
        writeInt(requestId);
        writeInt(result);
        writeInt(radius);
        writeBoolean(canContinue);
        writeBoolean(foundId != null);
        if (foundId != null) {
            writeIdentifier(foundId);
            writeInt(foundX);
            writeInt(foundZ);
            writeBoolean(displayCoordinates);
            writeBoolean(displayDistance);
            writeCollection(biomes, PacketByteBuf::writeIdentifier);
            writeVarInt(validBiomeMask);
            if (visited != null) writeBoolean(visited);
        }
    }

    public static void sendExcluded(ServerPlayerEntity player, int requestId, Identifier structure, boolean whitelistActive) {
        ServerPlayNetworking.send(player, ID, excluded(requestId, structure, whitelistActive));
    }

    static SearchResultPacket excluded(int requestId, Identifier structure, boolean whitelistActive) {
        var packet = withoutFoundTarget(requestId, whitelistActive ? NOT_WHITELISTED : BLACKLISTED, 0, false);
        packet.writeIdentifier(structure);
        return packet;
    }

    public static void sendCostFailure(ServerPlayerEntity player, int requestId, int cost, int balance, boolean multiple) {
        ServerPlayNetworking.send(player, ID, costFailure(requestId, cost, balance, multiple));
    }

    static SearchResultPacket costFailure(int requestId, int cost, int balance, boolean multiple) {
        var packet = withoutFoundTarget(requestId, multiple ? MULTI_COST : INSUFFICIENT_COST, cost, false);
        packet.writeVarInt(balance);
        return packet;
    }

    public static void sendLocked(ServerPlayerEntity player, int requestId, RequirementNode requirements) {
        ServerPlayNetworking.send(player, ID, locked(requestId, requirements));
    }

    static SearchResultPacket locked(int requestId, RequirementNode requirements) {
        var packet = withoutFoundTarget(requestId, LOCKED, 0, false);
        requirements.write(packet);
        return packet;
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius) {
        send(player, requestId, result, radius, false);
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius, boolean canContinue) {
        ServerPlayNetworking.send(player, ID, withoutFoundTarget(requestId, result, radius, canContinue));
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, int validBiomeMask, Boolean visited) {
        ServerPlayNetworking.send(player, ID, new SearchResultPacket(requestId, result, radius, false,
                foundId, foundX, foundZ, displayCoordinates, displayDistance, biomes, validBiomeMask, visited));
    }

    private static SearchResultPacket withoutFoundTarget(int requestId, int result, int radius, boolean canContinue) {
        return new SearchResultPacket(requestId, result, radius, canContinue, null, 0, 0, false, false, List.of(), 0, null);
    }

    public static Result read(PacketByteBuf buf) {
        int requestId = buf.readInt();
        int status = buf.readInt();
        int radius = buf.readInt();
        buf.readBoolean(); // Legacy search continuation flag.
        Identifier structureId = null;
        int foundX = 0;
        int foundZ = 0;
        boolean displayCoordinates = false;
        boolean displayDistance = false;
        if (buf.readBoolean()) {
            structureId = buf.readIdentifier();
            foundX = buf.readInt();
            foundZ = buf.readInt();
            displayCoordinates = buf.readBoolean();
            displayDistance = buf.readBoolean();
        }

        List<Identifier> biomes = List.of();
        Set<Identifier> validBiomes = Set.of();
        if (status == FOUND && buf.isReadable()) {
            int count = buf.readVarInt();
            if (count < 0 || count > StructureDestination.MAX_BIOME_SAMPLES) {
                throw new IllegalArgumentException("Invalid found biome count");
            }
            var samples = new ArrayList<Identifier>(count);
            for (int i = 0; i < count; i++) samples.add(buf.readIdentifier());
            int validBiomeMask = buf.isReadable() ? buf.readVarInt() : 0;
            var validSamples = new HashSet<Identifier>();
            for (int i = 0; i < count; i++) {
                if ((validBiomeMask & (1 << i)) != 0) validSamples.add(samples.get(i));
            }
            biomes = List.copyOf(samples);
            validBiomes = Set.copyOf(validSamples);
        }

        Boolean visited = status == FOUND && buf.isReadable() ? buf.readBoolean() : null;
        if (status == BLACKLISTED || status == NOT_WHITELISTED) structureId = buf.readIdentifier();
        RequirementNode requirements = status == LOCKED ? RequirementNode.read(buf) : null;
        int balance = status == INSUFFICIENT_COST || status == MULTI_COST ? buf.readVarInt() : 0;
        return new Result(requestId, status, radius, structureId, foundX, foundZ, displayCoordinates, displayDistance,
                biomes, validBiomes, visited, requirements, balance);
    }

}
