package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.unlocking.UnlockedStructures;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;

public final class AcknowledgeNoticePacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "acknowledge_notice");
    public AcknowledgeNoticePacket(long revision) { super(Unpooled.buffer()); writeLong(revision); }
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, sender) -> {
            long revision = buf.readLong();
            server.execute(() -> UnlockedStructures.get(server).acknowledgeNotice(player.getUuid(), revision));
        });
    }
}
