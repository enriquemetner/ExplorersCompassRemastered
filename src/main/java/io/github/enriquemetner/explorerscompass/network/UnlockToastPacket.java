package io.github.enriquemetner.explorerscompass.network;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class UnlockToastPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "unlock_toast");

    private UnlockToastPacket(int count, boolean party) {
        super(Unpooled.buffer());
        writeVarInt(count);
        writeBoolean(party);
    }

    public static void send(ServerPlayerEntity player, int count) {
        send(player, count, false);
    }
    public static void send(ServerPlayerEntity player, int count, boolean party) {
        if (count > 0) ServerPlayNetworking.send(player, ID, new UnlockToastPacket(count, party));
    }
}
