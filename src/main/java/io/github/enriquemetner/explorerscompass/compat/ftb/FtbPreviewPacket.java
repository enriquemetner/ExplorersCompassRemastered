package io.github.enriquemetner.explorerscompass.compat.ftb;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Derived editor data sent separately from FTB's saved quest configuration. */
record FtbPreviewPacket(long objectId, FtbRewardPreview preview) {
    static FtbPreviewPacket read(PacketByteBuf buf) {
        return new FtbPreviewPacket(buf.readLong(), FtbRewardPreview.read(buf));
    }

    void write(PacketByteBuf buf) {
        buf.writeLong(objectId);
        preview.write(buf);
    }

    void broadcast(MinecraftServer server, Identifier channel) {
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, channel)) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            write(buf);
            ServerPlayNetworking.send(player, channel, buf);
        }
    }
}
