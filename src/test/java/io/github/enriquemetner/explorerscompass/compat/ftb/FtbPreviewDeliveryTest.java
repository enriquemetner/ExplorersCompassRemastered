package io.github.enriquemetner.explorerscompass.compat.ftb;

import io.github.enriquemetner.explorerscompass.compat.ftb.FtbPreviewDelivery.Kind;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FtbPreviewDeliveryTest {
    private static final long ID = 12;
    private static final FtbRewardPreview FORTRESS = FtbRewardPreview.of(Set.of(new Identifier("fortress")));

    private final FtbPreviewDelivery<String> delivery = new FtbPreviewDelivery<>();
    private final String file = new String("quest file");

    @Test void packetKeepsTheLongIdBeforeBothPreviewCounts() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            var preview = new FtbPreviewPacket(0x123456789ABCDEFL, FORTRESS);
            preview.write(buf);
            assertEquals(preview.objectId(), buf.readLong());
            assertEquals(1, buf.readVarInt());
            assertEquals(1, buf.readVarInt());
            assertEquals(new Identifier("fortress"), buf.readIdentifier());
            assertFalse(buf.isReadable());
            buf.readerIndex(0);
            assertEquals(preview, FtbPreviewPacket.read(buf));
        } finally {
            buf.release();
        }
    }

    @Test void appliesAnExistingObjectEditImmediately() {
        var packet = new FtbPreviewPacket(ID, FORTRESS);
        var received = new ArrayList<FtbPreviewPacket>();
        delivery.receive(file, Kind.TASK, packet, received::add);
        assertEquals(List.of(packet), received);
        assertNull(delivery.take(file, Kind.TASK, ID));
    }

    @Test void keepsTheLatestPreviewUntilCreateAndSeparatesEqualIdsByKind() {
        queue(file, Kind.TASK, FORTRESS);
        queue(file, Kind.REWARD, FORTRESS);
        queue(file, Kind.TASK, FtbRewardPreview.EMPTY);

        assertEquals(FtbRewardPreview.EMPTY, delivery.take(file, Kind.TASK, ID));
        assertNull(delivery.take(file, Kind.TASK, ID));
        assertEquals(FORTRESS, delivery.take(file, Kind.REWARD, ID));
    }

    @Test void replacingTheFileClearsBothKindsEvenWhenFilesCompareEqual() {
        queue(file, Kind.TASK, FORTRESS);
        queue(file, Kind.REWARD, FORTRESS);
        String replacement = new String(file);
        assertNull(delivery.take(replacement, Kind.TASK, ID));
        assertNull(delivery.take(replacement, Kind.REWARD, ID));
        assertNull(delivery.take(file, Kind.TASK, ID));
    }

    @Test void disconnectClearsBothKindsAndUpdatesWithoutAFileAreDropped() {
        queue(file, Kind.TASK, FORTRESS);
        queue(file, Kind.REWARD, FORTRESS);
        delivery.clear();
        delivery.receive(null, Kind.TASK, new FtbPreviewPacket(ID, FORTRESS),
                update -> fail("No quest file available"));
        assertNull(delivery.take(file, Kind.TASK, ID));
        assertNull(delivery.take(file, Kind.REWARD, ID));
    }

    private void queue(String questFile, Kind kind, FtbRewardPreview preview) {
        delivery.receive(questFile, kind, new FtbPreviewPacket(ID, preview), update -> false);
    }
}
