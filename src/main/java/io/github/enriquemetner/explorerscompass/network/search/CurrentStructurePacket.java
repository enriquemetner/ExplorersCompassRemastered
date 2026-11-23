package io.github.enriquemetner.explorerscompass.network.search;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

public final class CurrentStructurePacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "current_structure");

    private CurrentStructurePacket() {}

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                             PacketByteBuf buffer, PacketSender sender) {
        var request = buffer.readUuid();
        server.execute(() -> {
            if (!player.isHolding(ExplorersCompass.EXPLORERS_COMPASS_ITEM)) return;
            var world = player.getServerWorld();
            var pos = player.getBlockPos();
            var accessor = world.getStructureAccessor();
            Set<Identifier> matches = new LinkedHashSet<>();
            for (var start : accessor.getStructureStarts(new ChunkPos(pos), structure ->
                    !StructureUtils.structureIsExcluded(world, structure) && !StructureUtils.structureIsHidden(world, structure))) {
                if (start.hasChildren() && accessor.structureContains(pos, start)) {
                    var id = StructureUtils.getIDForStructure(world, start.getStructure());
                    if (id != null) matches.add(id);
                }
            }
            var reply = PacketByteBufs.create();
            reply.writeUuid(request);
            reply.writeIdentifier(world.getRegistryKey().getValue());
            reply.writeCollection(matches, PacketByteBuf::writeIdentifier);
            ServerPlayNetworking.send(player, ID, reply);
        });
    }
}
