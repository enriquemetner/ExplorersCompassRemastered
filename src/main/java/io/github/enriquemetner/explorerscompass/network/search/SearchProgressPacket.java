package io.github.enriquemetner.explorerscompass.network.search;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Sent with radius zero when a search starts, then with updated distances.
 * Search progress must not depend on inventory synchronization, which stops while paused.
 */
public class SearchProgressPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "search_progress");

    public SearchProgressPacket(int requestId, int radius) {
        super(Unpooled.buffer());
        writeInt(requestId);
        writeInt(radius);
    }

    public static void send(ServerPlayerEntity player, int requestId, int radius) {
        ServerPlayNetworking.send(player, ID, new SearchProgressPacket(requestId, radius));
    }
}
