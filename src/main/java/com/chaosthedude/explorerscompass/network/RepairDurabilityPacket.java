package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Synchronizes compass settings needed as soon as a player joins. */
public final class RepairDurabilityPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "repair_durability");

    private RepairDurabilityPacket(int durability, boolean showNetherEquivalent) {
        super(Unpooled.buffer());
        writeVarInt(durability);
        writeBoolean(showNetherEquivalent);
    }

    public static void send(ServerPlayerEntity player, int durability, boolean showNetherEquivalent) {
        ServerPlayNetworking.send(player, ID, new RepairDurabilityPacket(durability, showNetherEquivalent));
    }

}
