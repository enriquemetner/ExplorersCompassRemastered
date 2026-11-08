package com.chaosthedude.explorerscompass.network;

import java.util.Collections;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules;
import com.chaosthedude.explorerscompass.util.CompassSearchData;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.PlayerUtils;
import com.chaosthedude.explorerscompass.util.StructureDestination;
import com.chaosthedude.explorerscompass.util.StructureLanding;
import com.chaosthedude.explorerscompass.util.StructureUtils;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class TeleportPacket extends PacketByteBuf {

	public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "teleport");

	public TeleportPacket() {
		super(Unpooled.buffer());
	}

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        server.execute(() -> {
            var compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
            ItemStack stack = ItemUtils.getHeldItem(player, compass);
            if (stack.isEmpty()) return;

            compass.validateTarget(player, stack);
            if (!PlayerUtils.canTeleport(player)) {
                ExplorersCompass.LOGGER.warn("Player " + player.getDisplayName().getString() + " tried to teleport but does not have permission.");
                return;
            }
            var world = player.getServerWorld();
            if (compass.getState(stack) != CompassState.FOUND || !compass.isInFoundDimension(stack, world)) return;

            var nbt = stack.getOrCreateNbt();
            Identifier id = compass.getStructureID(stack);
            if (!UnlockingRules.isUnlocked(server, player.getUuid(), id)
                    && !(player.getAbilities().creativeMode && nbt.getBoolean("CreativeBypass"))) return;

            var target = new BlockPos(compass.getFoundStructureX(stack), 0, compass.getFoundStructureZ(stack));
            StructureStart start = null;
            if (nbt.contains(CompassSearchData.FOUND_START_CHUNK, NbtElement.LONG_TYPE)) {
                var chunk = new ChunkPos(nbt.getLong(CompassSearchData.FOUND_START_CHUNK));
                // Some pieces adjust their bounds to the terrain when their blocks are placed.
                world.getChunk(target);
                start = StructureDestination.readStart(world, chunk, StructureUtils.getStructureForID(world, id));
            }

            var landing = StructureLanding.find(player, target, StructureDestination.pieces(start));
            if (landing == null) {
                player.sendMessage(Text.translatable("message.explorerscompass.no_teleport_destination"), false);
                return;
            }

            player.stopRiding();
            var pos = landing.position();
            handler.requestTeleport(pos.x, pos.y, pos.z, player.getYaw(), player.getPitch(), Collections.emptySet());
            if (landing.pose() != null) player.setPose(landing.pose());

            if (!player.isFallFlying()) {
                player.setVelocity(player.getVelocity().getX(), 0, player.getVelocity().getZ());
                player.setOnGround(true);
            }
        });
    }

}
