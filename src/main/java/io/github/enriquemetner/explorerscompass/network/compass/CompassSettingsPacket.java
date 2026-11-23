package io.github.enriquemetner.explorerscompass.network.compass;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class CompassSettingsPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "compass_settings");

    public record Settings(SearchCosts.Type type, int initialEnergy, Map<Identifier, Integer> items,
                           boolean showNetherEquivalent, int defaultCost, Map<Identifier, Integer> overrides,
                           boolean useClassicCompass) {}

    private CompassSettingsPacket() {}

    public static void send(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, ID, write(SearchCosts.active(),
                ExplorersCompassConfig.showNetherEquivalent, ExplorersCompassConfig.useClassicCompass));
    }

    static PacketByteBuf write(SearchCosts costs, boolean showNetherEquivalent, boolean useClassicCompass) {
        var packet = new PacketByteBuf(Unpooled.buffer());
        packet.writeEnumConstant(costs.type());
        packet.writeVarInt(costs.initialEnergy());
        packet.writeMap(costs.items(), PacketByteBuf::writeIdentifier, PacketByteBuf::writeVarInt);
        packet.writeBoolean(showNetherEquivalent);
        packet.writeVarInt(costs.defaultCost());
        packet.writeMap(costs.overrides(), PacketByteBuf::writeIdentifier, PacketByteBuf::writeVarInt);
        packet.writeBoolean(useClassicCompass);
        return packet;
    }

    public static Settings read(PacketByteBuf buf) {
        var type = buf.readEnumConstant(SearchCosts.Type.class);
        int energy = buf.readVarInt();
        var items = buf.readMap(LinkedHashMap::new, PacketByteBuf::readIdentifier, PacketByteBuf::readVarInt);
        boolean equivalent = buf.readBoolean();
        int defaultCost = buf.readVarInt();
        var overrides = buf.readMap(PacketByteBuf::readIdentifier, PacketByteBuf::readVarInt);
        boolean classic = buf.isReadable() && buf.readBoolean();
        return new Settings(type, energy, items, equivalent, defaultCost, overrides, classic);
    }
}
