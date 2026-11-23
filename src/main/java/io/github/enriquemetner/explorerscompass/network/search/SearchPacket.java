package io.github.enriquemetner.explorerscompass.network.search;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SearchPacket extends PacketByteBuf {

	public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search");

	public SearchPacket(Identifier structureOrSetId, boolean isSet, boolean excludeVisited, boolean creativeUnlocksAll, int requestId) {
		super(Unpooled.buffer());
		writeIdentifier(structureOrSetId);
		writeBoolean(isSet);
		writeBoolean(excludeVisited);
		writeBoolean(creativeUnlocksAll);
		writeInt(requestId);
	}

	public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
		final Identifier structureOrSetId = buf.readIdentifier();
		final boolean isSet = buf.readBoolean();
		final boolean excludeVisited = buf.readBoolean();
		final boolean creativeUnlocksAll = buf.readBoolean();
		final int requestId = buf.readInt();

		SearchDispatch.withHeldCompass(server, player, requestId, stack ->
				ExplorersCompass.EXPLORERS_COMPASS_ITEM.searchForStructure(player.getServerWorld(), player,
						player.getBlockPos(), structureOrSetId, isSet, excludeVisited, creativeUnlocksAll, stack, requestId));
	}

}
