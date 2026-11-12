package com.chaosthedude.explorerscompass.network;

import java.util.List;
import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.unlocking.RequirementNode;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SearchResultPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search_result");
    public static final int FOUND = 0, NOT_FOUND = 1, REJECTED = 2, WRONG_DIMENSION = 3, INSUFFICIENT_XP = 4, LOCKED = 5;

    public SearchResultPacket(int requestId, int result, int radius, boolean canContinue, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, int validBiomeMask) {
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
        }
    }

    public static void sendLocked(ServerPlayerEntity player, int requestId, RequirementNode requirements) {
        SearchResultPacket packet = new SearchResultPacket(requestId, LOCKED, 0, false, null, 0, 0, false, false, List.of(), 0);
        requirements.write(packet);
        ServerPlayNetworking.send(player, ID, packet);
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius) {
        send(player, requestId, result, radius, false, null, 0, 0, false, false, List.of(), 0);
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius, boolean canContinue) {
        send(player, requestId, result, radius, canContinue, null, 0, 0, false, false, List.of(), 0);
    }

    public static void send(ServerPlayerEntity player, int requestId, int result, int radius, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, int validBiomeMask) {
        send(player, requestId, result, radius, false, foundId, foundX, foundZ, displayCoordinates, displayDistance, biomes, validBiomeMask);
    }

    private static void send(ServerPlayerEntity player, int requestId, int result, int radius, boolean canContinue, Identifier foundId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, int validBiomeMask) {
        ServerPlayNetworking.send(player, ID, new SearchResultPacket(requestId, result, radius, canContinue, foundId, foundX, foundZ, displayCoordinates, displayDistance, biomes, validBiomeMask));
    }

}
