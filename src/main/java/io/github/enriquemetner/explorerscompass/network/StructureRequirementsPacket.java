package io.github.enriquemetner.explorerscompass.network;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Requests the full requirements for one structure from the server. */
public final class StructureRequirementsPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "structure_requirements");

    private StructureRequirementsPacket() {}

    public static PacketByteBuf request(int requestId, Identifier structureId) {
        var request = PacketByteBufs.create();
        request.writeVarInt(requestId);
        request.writeIdentifier(structureId);
        return request;
    }

    public static void apply(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                             PacketByteBuf buf, PacketSender sender) {
        int requestId = buf.readVarInt();
        Identifier id = buf.readIdentifier();
        server.execute(() -> {
            if (!ServerPlayNetworking.canSend(player, ID)) return;
            var world = player.getServerWorld();
            var structure = StructureUtils.getStructureForID(world, id);
            RequirementNode requirements = structure != null && !StructureUtils.structureIdIsExcluded(id)
                    && !StructureUtils.structureIsHidden(world, structure)
                    ? UnlockingRules.get().inspectRequirements(player, id) : null;
            var response = PacketByteBufs.create();
            response.writeVarInt(requestId);
            response.writeBoolean(requirements != null);
            if (requirements != null) requirements.write(response);
            ServerPlayNetworking.send(player, ID, response);
        });
    }
}
