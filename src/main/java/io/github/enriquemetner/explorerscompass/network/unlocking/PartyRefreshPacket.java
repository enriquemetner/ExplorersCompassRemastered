package io.github.enriquemetner.explorerscompass.network.unlocking;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PartyRefreshPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "party_refresh");
    private PartyRefreshPacket() {}

    public static void send(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, ID, new PacketByteBuf(Unpooled.buffer()));
    }

}
