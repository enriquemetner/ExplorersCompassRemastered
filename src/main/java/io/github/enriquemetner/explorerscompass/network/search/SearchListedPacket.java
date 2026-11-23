package io.github.enriquemetner.explorerscompass.network.search;

import java.util.ArrayList;
import java.util.List;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SearchListedPacket extends PacketByteBuf {

    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search_listed");

	public SearchListedPacket(List<Identifier> ids, boolean excludeVisited, boolean creativeUnlocksAll, int requestId) {
        super(Unpooled.buffer());
		writeInt(requestId);
		writeBoolean(excludeVisited);
		writeBoolean(creativeUnlocksAll);
        writeVarInt(ids.size());
        for (Identifier id : ids) writeIdentifier(id);
    }

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
		int requestId = buf.readInt();
		boolean excludeVisited = buf.readBoolean();
		boolean creativeUnlocksAll = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 1 || count > 32767) {
            server.execute(() -> SearchResultPacket.send(player, requestId, SearchResultPacket.REJECTED, 0));
            return;
        }
        List<Identifier> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readIdentifier());
        SearchDispatch.withHeldCompass(server, player, requestId, stack ->
                ExplorersCompass.EXPLORERS_COMPASS_ITEM.searchForListedStructures(player.getServerWorld(), player,
                        player.getBlockPos(), ids, excludeVisited, creativeUnlocksAll, stack, requestId));
    }
}
