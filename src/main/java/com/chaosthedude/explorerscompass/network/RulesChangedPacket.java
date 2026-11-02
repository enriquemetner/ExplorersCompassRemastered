package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class RulesChangedPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "rules_changed");
    private RulesChangedPacket() {}

    public static void send(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, ID, new PacketByteBuf(Unpooled.buffer()));
    }

}
