package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.recipe.CompassRepairRecipe;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Synced on join so the crafting preview uses the server's durability setting. */
public final class RepairDurabilityPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "repair_durability");

    private RepairDurabilityPacket(int durability) {
        super(Unpooled.buffer());
        writeVarInt(durability);
    }

    public static void send(ServerPlayerEntity player, int durability) {
        ServerPlayNetworking.send(player, ID, new RepairDurabilityPacket(durability));
    }

}
