package io.github.enriquemetner.explorerscompass.network.unlocking;

import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureRequirementsPacketTest {
    @Test void requestIdUsesAVarIntAndAbsentTreeStopsAfterPresenceFlag() {
        assertReply("ac0200", null);
    }

    @Test void nestedReplyUsesTheExistingRequirementTreeFormat() {
        var tree = new RequirementNode(Text.literal("All"), false, true,
                List.of(RequirementNode.structure(Text.literal("Visit"), true, new Identifier("test:a"))));
        assertReply("ac02010e7b2274657874223a22416c6c227d00010000000001"
                + "107b2274657874223a225669736974227d010000000106746573743a610000", tree);
    }

    private static void assertReply(String wire, RequirementNode node) {
        byte[] expected = HexFormat.of().parseHex(wire);
        var encoded = StructureRequirementsPacket.writeResponse(300, node);
        var received = new PacketByteBuf(Unpooled.wrappedBuffer(expected));
        try {
            assertArrayEquals(expected, ByteBufUtil.getBytes(encoded));
            var response = StructureRequirementsPacket.readResponse(received);
            assertEquals(300, response.requestId());
            assertEquals(node, response.requirements());
            assertFalse(received.isReadable());
        } finally {
            received.release();
            encoded.release();
        }
    }
}
