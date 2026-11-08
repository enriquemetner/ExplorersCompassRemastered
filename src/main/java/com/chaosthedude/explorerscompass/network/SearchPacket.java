package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.ItemUtils;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

		server.execute(() -> {
			final ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
			if (!stack.isEmpty()) {
				final ExplorersCompassItem explorersCompass = (ExplorersCompassItem) stack.getItem();
				final ServerWorld world = (ServerWorld) player.getEntityWorld();
				explorersCompass.searchForStructure(world, player, player.getBlockPos(), structureOrSetId, isSet, excludeVisited, creativeUnlocksAll, stack, requestId);
			} else {
				SearchResultPacket.send(player, requestId, SearchResultPacket.REJECTED, 0);
			}
		});
	}

}
