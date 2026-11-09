package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.CompassTeleport;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TeleportPacket extends PacketByteBuf {

	public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "teleport");

	public TeleportPacket() {
		super(Unpooled.buffer());
	}

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        server.execute(() -> {
            try {
                CompassTeleport.toHeldTarget(player);
            } catch (CommandSyntaxException e) {
                var message = e.getRawMessage();
                player.sendMessage(message instanceof Text text ? text : Text.literal(message.getString()), false);
            }
        });
    }

}
