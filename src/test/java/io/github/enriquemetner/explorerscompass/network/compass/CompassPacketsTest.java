package io.github.enriquemetner.explorerscompass.network.compass;

import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompassPacketsTest {
    private static final Identifier PEARL = new Identifier("minecraft:ender_pearl");
    private static final Identifier DIAMOND = new Identifier("minecraft:diamond");
    private static final Identifier RUIN = new Identifier("test:ruin");
    // Captured before the refactor so the expected bytes stay independent of the writer.
    private static final String SETTINGS_WIRE = "02" // Cost type: ITEMS
            + "ac02" // Initial energy: 300
            + "02" // Two fuels
            + "156d696e6563726166743a656e6465725f706561726c07" // minecraft:ender_pearl: 7 energy
            + "116d696e6563726166743a6469616d6f6e648101" // minecraft:diamond: 129 energy
            + "01" // Show Nether-equivalent coordinates
            + "8202" // Default search cost: 258
            + "01" // One cost override
            + "09746573743a7275696e11" // test:ruin: 17 energy
            + "01"; // Classic compass enabled

    @Test void chargeRequestKeepsTwoVarIntsAndTheWholeStackFlag() {
        for (boolean wholeStack : List.of(false, true)) {
            var packet = ConsumeCompassItemsPacket.request(300, 257, wholeStack);
            try {
                assertEquals(wholeStack ? "ac02810201" : "ac02810200", ByteBufUtil.hexDump(packet));
                assertEquals(300, packet.readVarInt());
                assertEquals(257, packet.readVarInt());
                assertEquals(wholeStack, packet.readBoolean());
                assertFalse(packet.isReadable());
            } finally {
                packet.release();
            }
        }
    }

    @Test void settingsWriterMatchesTheOriginalFuelOrderAndLayout() {
        var fuels = new LinkedHashMap<Identifier, Integer>();
        fuels.put(PEARL, 7);
        fuels.put(DIAMOND, 129);
        var costs = new SearchCosts(SearchCosts.Type.ITEMS, 258, Map.of(RUIN, 17), 300, fuels, false);
        var packet = CompassSettingsPacket.write(costs, true, true);
        try {
            assertEquals(SETTINGS_WIRE, ByteBufUtil.hexDump(packet));
        } finally {
            packet.release();
        }
    }

    @Test void settingsReaderAcceptsBothCurrentAndLegacyTails() {
        for (boolean includesClassicField : List.of(false, true)) {
            String wire = includesClassicField ? SETTINGS_WIRE : SETTINGS_WIRE.substring(0, SETTINGS_WIRE.length() - 2);
            var packet = new PacketByteBuf(Unpooled.wrappedBuffer(HexFormat.of().parseHex(wire)));
            try {
                var settings = CompassSettingsPacket.read(packet);
                assertEquals(SearchCosts.Type.ITEMS, settings.type());
                assertEquals(300, settings.initialEnergy());
                assertEquals(List.of(PEARL, DIAMOND), List.copyOf(settings.items().keySet()));
                assertEquals(Map.of(PEARL, 7, DIAMOND, 129), settings.items());
                assertTrue(settings.showNetherEquivalent());
                assertEquals(258, settings.defaultCost());
                assertEquals(Map.of(RUIN, 17), settings.overrides());
                assertEquals(includesClassicField, settings.useClassicCompass());
                assertFalse(packet.isReadable());
            } finally {
                packet.release();
            }
        }
    }
}
