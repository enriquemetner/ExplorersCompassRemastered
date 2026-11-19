package io.github.enriquemetner.explorerscompass.compat.ftb;

import io.netty.buffer.Unpooled;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FtbRewardPreviewTest {
    private static FtbRewardPreview transfer(Set<Identifier> targets) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            FtbRewardPreview.of(targets).write(buf);
            FtbRewardPreview result = FtbRewardPreview.read(buf);
            assertEquals(0, buf.readableBytes());
            return result;
        } finally {
            buf.release();
        }
    }

    @Test void updatesReplaceBothNamesAndCountWhenTargetsGrowShrinkOrBecomeEmpty() {
        Set<Identifier> targets = new LinkedHashSet<>();
        for (int i = 11; i >= 0; i--) targets.add(new Identifier("test:structure_" + String.format("%02d", i)));
        FtbRewardPreview client = transfer(targets);
        assertEquals(12, client.totalCount());
        assertEquals(12, client.structures().size());
        assertEquals(new Identifier("test:structure_00"), client.structures().get(0));
        assertEquals(new Identifier("test:structure_11"), client.structures().get(11));

        Identifier replacement = new Identifier("minecraft:ancient_city");
        client = transfer(Set.of(replacement));
        assertEquals(1, client.totalCount());
        assertEquals(List.of(replacement), client.structures());

        client = transfer(Set.of());
        assertEquals(FtbRewardPreview.EMPTY, client);
    }

    @Test void namesAreSortedByDisplayNameWithAnIdTiebreaker() {
        Identifier first = new Identifier("z:ancient_city");
        Identifier second = new Identifier("a:fortress");
        Identifier third = new Identifier("z:fortress");
        assertEquals(List.of(first, second, third), transfer(Set.of(third, second, first)).structures());
    }

    @Test void rejectsIncompleteOrOversizedPreviews() {
        for (int count : List.of(-1, 9, 11)) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(12);
                buf.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> FtbRewardPreview.read(buf));
            } finally {
                buf.release();
            }
        }
    }

    @Test void longTooltipsCycleThroughEveryNameAndWrapAround() {
        List<Identifier> names = transfer(Set.of(
                new Identifier("test:one"), new Identifier("test:two"),
                new Identifier("test:three"), new Identifier("test:four"))).structures();
        assertEquals(names, FtbStructureTooltip.visibleNames(names, 4, 17));
        assertEquals(List.of(names.get(2), names.get(3)), FtbStructureTooltip.visibleNames(names, 2, 2));
        assertEquals(List.of(names.get(3), names.get(0)), FtbStructureTooltip.visibleNames(names, 2, 3));
        assertEquals(List.of(names.get(0), names.get(1)), FtbStructureTooltip.visibleNames(names, 2, 4));
    }
}
