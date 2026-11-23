package io.github.enriquemetner.explorerscompass.network.compass;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ConsumeCompassItemsPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "consume_compass_items");

    private ConsumeCompassItemsPacket() {}

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler connection,
                             PacketByteBuf buffer, PacketSender sender) {
        int syncId = buffer.readVarInt();
        int slotId = buffer.readVarInt();
        boolean wholeStack = buffer.readBoolean();
        server.execute(() -> {
            var handler = player.currentScreenHandler;
            if (handler.syncId != syncId || !handler.canUse(player)
                    || slotId < 0 || slotId >= handler.slots.size()) return;
            var slot = handler.getSlot(slotId);
            if (slot.isEnabled() && slot.getStack().getItem() instanceof ExplorersCompassItem compass) {
                compass.consumeItems(player, slot, wholeStack);
            }
        });
    }
}
