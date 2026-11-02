package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class UnlockToastPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "unlock_toast");

    private UnlockToastPacket(int count) {
        super(Unpooled.buffer());
        writeVarInt(count);
    }

    public static void send(ServerPlayerEntity player, int count) {
        if (count > 0) ServerPlayNetworking.send(player, ID, new UnlockToastPacket(count));
    }

}
