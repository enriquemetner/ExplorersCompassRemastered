package io.github.enriquemetner.explorerscompass.network.compass;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class CompassSettingsPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "compass_settings");

    private CompassSettingsPacket() {}

    public static void send(ServerPlayerEntity player) {
        var costs = SearchCosts.active();
        var packet = new PacketByteBuf(Unpooled.buffer());
        packet.writeEnumConstant(costs.type());
        packet.writeVarInt(costs.initialEnergy());
        packet.writeMap(costs.items(), PacketByteBuf::writeIdentifier, PacketByteBuf::writeVarInt);
        packet.writeBoolean(ExplorersCompassConfig.showNetherEquivalent);
        packet.writeVarInt(costs.defaultCost());
        packet.writeMap(costs.overrides(), PacketByteBuf::writeIdentifier, PacketByteBuf::writeVarInt);
        packet.writeBoolean(ExplorersCompassConfig.useClassicCompass);
        ServerPlayNetworking.send(player, ID, packet);
    }
}
