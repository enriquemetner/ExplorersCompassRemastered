package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.availability.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class SearchAvailabilityPacket {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID,"search_availability");
    public record Response(int requestId, List<Identifier> eligibleIds, int wrongDimension, List<AvailabilitySelection.Disabled> disabled) {
        public Response { eligibleIds = List.copyOf(eligibleIds); disabled = List.copyOf(disabled); }
        public int eligible() { return eligibleIds.size(); }
        public boolean appliesTo(int activeRequest, boolean searching) { return searching && requestId == activeRequest; }
    }
    private SearchAvailabilityPacket() {}
    public static void send(ServerPlayerEntity player,int request,AvailabilitySelection selection) {
        if (!ServerPlayNetworking.canSend(player,ID)) return;
        var response = new Response(request,selection.eligible(),selection.wrongDimension(),selection.disabled());
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer()); write(buf,response);
        ServerPlayNetworking.send(player,ID,buf);
    }
    public static void write(PacketByteBuf buf,Response response) {
        buf.writeInt(response.requestId());buf.writeVarInt(response.eligible());
        for (Identifier id : response.eligibleIds()) buf.writeIdentifier(id);
        buf.writeVarInt(response.wrongDimension());
        buf.writeVarInt(response.disabled().size());
        for (var entry : response.disabled()) {
            buf.writeIdentifier(entry.structure());buf.writeEnumConstant(entry.availability().reason());
            buf.writeString(entry.availability().modName(),256);buf.writeVarInt(entry.availability().replacements().size());
            for (Identifier id : entry.availability().replacements()) buf.writeIdentifier(id);
        }
    }
    private static int count(PacketByteBuf buf,int max) {
        int count=buf.readVarInt();if(count<0 || count>max)throw new IllegalArgumentException("Invalid availability count");return count;
    }
    public static Response read(PacketByteBuf buf) {
        int request=buf.readInt(),eligible=count(buf,32767);
        List<Identifier> ids=new ArrayList<>();
        for(int i=0;i<eligible;i++)ids.add(buf.readIdentifier());
        int wrong=count(buf,32767),size=count(buf,32767);
        List<AvailabilitySelection.Disabled> disabled=new ArrayList<>();
        for(int i=0;i<size;i++) {
            Identifier id=buf.readIdentifier();Availability.Reason reason=buf.readEnumConstant(Availability.Reason.class);
            String mod=buf.readString(256);int n=count(buf,256);List<Identifier> replacements=new ArrayList<>();
            for(int j=0;j<n;j++)replacements.add(buf.readIdentifier());
            disabled.add(new AvailabilitySelection.Disabled(id,Availability.disabled(reason,mod,replacements)));
        }
        return new Response(request,ids,wrong,disabled);
    }
}
