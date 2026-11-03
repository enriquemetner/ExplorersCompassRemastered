package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Requests current server-owned structure state when returning to an open compass screen. */
public final class RefreshCompassPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "refresh_compass");

    public RefreshCompassPacket() { this(true); }

    public RefreshCompassPacket(boolean reevaluate) {
        super(Unpooled.buffer());
        writeBoolean(reevaluate);
    }

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                             PacketByteBuf buf, PacketSender sender) {
        boolean reevaluate = buf.readBoolean();
        server.execute(() -> ExplorersCompass.EXPLORERS_COMPASS_ITEM.syncScreen(player, reevaluate));
    }
}
