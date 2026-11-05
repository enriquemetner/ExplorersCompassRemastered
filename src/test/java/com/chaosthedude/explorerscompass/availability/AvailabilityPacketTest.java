package com.chaosthedude.explorerscompass.availability;

import com.chaosthedude.explorerscompass.network.SearchAvailabilityPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AvailabilityPacketTest {
    @Test void roundTripsMixedSelectionWithStyledMessageMetadataAndReplacementIds() {
        var original=new SearchAvailabilityPacket.Response(37,List.of(new Identifier("test:working")),2,List.of(
                new AvailabilitySelection.Disabled(new Identifier("minecraft:desert_pyramid"),Availability.disabled(
                        Availability.Reason.YUNG,"YUNG's Better Desert Temples",List.of(new Identifier("betterdeserttemples:desert_temple"))))));
        var buffer=new PacketByteBuf(Unpooled.buffer());
        try { SearchAvailabilityPacket.write(buffer,original);assertEquals(original,SearchAvailabilityPacket.read(buffer)); }
        finally {buffer.release();}
    }
    @Test void ignoresDismissedAndSupersededRequests() {
        var response=new SearchAvailabilityPacket.Response(37,List.of(),0,List.of());
        assertTrue(response.appliesTo(37,true));
        assertFalse(response.appliesTo(38,true));
        assertFalse(response.appliesTo(37,false));
    }
    @Test void invalidCountsAreRejectedBeforeAllocating() {
        var buffer=new PacketByteBuf(Unpooled.buffer());
        try { buffer.writeInt(1);buffer.writeVarInt(-1);assertThrows(IllegalArgumentException.class,()->SearchAvailabilityPacket.read(buffer)); }
        finally {buffer.release();}
    }
}
