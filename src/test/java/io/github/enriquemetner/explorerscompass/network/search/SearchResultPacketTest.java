package io.github.enriquemetner.explorerscompass.network.search;

import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static io.github.enriquemetner.explorerscompass.network.search.SearchResultPacket.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchResultPacketTest {
    private static final int REQUEST = 0x01020304;
    private static final Identifier STRUCTURE = new Identifier("test:ruin");
    private static final List<Identifier> BIOMES = List.of(
            new Identifier("test:desert"), new Identifier("test:plains"), new Identifier("test:forest"));
    private static final Set<Identifier> VALID_BIOMES = Set.of(BIOMES.get(0), BIOMES.get(2));

    // Captured before the codec refactor. Entries are indexed by their result code.
    private static final List<String> WIRE = List.of(
            "010203040000000000000000000109746573743a7275696efffffff7000000250100"
                    + "030b746573743a6465736572740b746573743a706c61696e730b746573743a666f726573740501",
            "0102030400000001000001400100",
            "0102030400000002000000000000",
            "0102030400000003000000000000",
            "0102030400000004000001f400008702",
            "01020304000000050000000000000e7b2274657874223a22416c6c227d00010000000001"
                    + "107b2274657874223a225669736974227d010000000106746573743a610000",
            "010203040000000600000000000009746573743a7275696e",
            "010203040000000700000000000009746573743a7275696e",
            "0102030400000008000001f400008702");
    private static final int FOUND_HEADER_LENGTH = HexFormat.of().parseHex(
            "010203040000000000000000000109746573743a7275696efffffff7000000250100").length;

    @Test void allResultWritersMatchTheOriginalWire() {
        assertWire(WIRE.get(FOUND), new SearchResultPacket(REQUEST, FOUND, 0, false,
                STRUCTURE, -9, 37, true, false, BIOMES, 5, true));
        assertWire(WIRE.get(NOT_FOUND), emptyResult(NOT_FOUND, 320, true));
        assertWire(WIRE.get(REJECTED), emptyResult(REJECTED, 0, false));
        assertWire(WIRE.get(WRONG_DIMENSION), emptyResult(WRONG_DIMENSION, 0, false));
        assertWire(WIRE.get(INSUFFICIENT_COST), costFailure(REQUEST, 500, 263, false));
        assertWire(WIRE.get(LOCKED), locked(REQUEST, tree()));
        assertWire(WIRE.get(BLACKLISTED), excluded(REQUEST, STRUCTURE, false));
        assertWire(WIRE.get(NOT_WHITELISTED), excluded(REQUEST, STRUCTURE, true));
        assertWire(WIRE.get(MULTI_COST), costFailure(REQUEST, 500, 263, true));
    }

    @Test void readsEveryResultWithTheExistingTail() {
        assertEquals(expectedFound(BIOMES, VALID_BIOMES, true), read(fixture(FOUND)));
        assertEquals(expectedFailure(NOT_FOUND, 320, null, null, 0), read(fixture(NOT_FOUND)));
        assertEquals(expectedFailure(REJECTED, 0, null, null, 0), read(fixture(REJECTED)));
        assertEquals(expectedFailure(WRONG_DIMENSION, 0, null, null, 0), read(fixture(WRONG_DIMENSION)));
        assertEquals(expectedFailure(INSUFFICIENT_COST, 500, null, null, 263), read(fixture(INSUFFICIENT_COST)));
        assertEquals(expectedFailure(LOCKED, 0, null, tree(), 0), read(fixture(LOCKED)));
        assertEquals(expectedFailure(BLACKLISTED, 0, STRUCTURE, null, 0), read(fixture(BLACKLISTED)));
        assertEquals(expectedFailure(NOT_WHITELISTED, 0, STRUCTURE, null, 0), read(fixture(NOT_WHITELISTED)));
        assertEquals(expectedFailure(MULTI_COST, 500, null, null, 263), read(fixture(MULTI_COST)));
    }

    @Test void legacyFoundPacketsCanEndAfterCoordinatesBiomesOrMask() {
        byte[] found = fixture(FOUND);
        assertEquals(expectedFound(List.of(), Set.of(), null), read(Arrays.copyOf(found, FOUND_HEADER_LENGTH)));
        assertEquals(expectedFound(BIOMES, Set.of(), null), read(Arrays.copyOf(found, found.length - 2)));
        assertEquals(expectedFound(BIOMES, VALID_BIOMES, null), read(Arrays.copyOf(found, found.length - 1)));
    }

    @Test void rejectsOutOfRangeFoundBiomeCountsBeforeReadingTheList() {
        for (int count : List.of(-1, 10)) {
            var buf = new PacketByteBuf(Unpooled.buffer());
            try {
                buf.writeBytes(fixture(FOUND), 0, FOUND_HEADER_LENGTH);
                buf.writeVarInt(count);
                assertEquals("Invalid found biome count",
                        assertThrows(IllegalArgumentException.class, () -> SearchResultPacket.read(buf)).getMessage());
            } finally {
                buf.release();
            }
        }
    }

    @Test void startRequestsKeepTheirDistinctWireLayouts() {
        assertWire("09746573743a7275696e00010001020304", new SearchPacket(STRUCTURE, false, true, false, REQUEST));
        assertWire("0102030401000209746573743a7275696e0a746573743a746f776572",
                new SearchListedPacket(List.of(STRUCTURE, new Identifier("test:tower")), true, false, REQUEST));
    }

    private static SearchResultPacket emptyResult(int status, int radius, boolean canContinue) {
        return new SearchResultPacket(REQUEST, status, radius, canContinue,
                null, 0, 0, false, false, List.of(), 0, null);
    }

    private static Result expectedFound(List<Identifier> biomes, Set<Identifier> validBiomes, Boolean visited) {
        return new Result(REQUEST, FOUND, 0, STRUCTURE, -9, 37, true, false, biomes, validBiomes, visited, null, 0);
    }

    private static Result expectedFailure(int status, int radius, Identifier structure, RequirementNode tree, int balance) {
        return new Result(REQUEST, status, radius, structure, 0, 0, false, false, List.of(), Set.of(), null, tree, balance);
    }

    private static RequirementNode tree() {
        return new RequirementNode(Text.literal("All"), false, true,
                List.of(RequirementNode.structure(Text.literal("Visit"), true, new Identifier("test:a"))));
    }

    private static byte[] fixture(int status) {
        return HexFormat.of().parseHex(WIRE.get(status));
    }

    private static void assertWire(String wire, PacketByteBuf buf) {
        try {
            assertEquals(wire, ByteBufUtil.hexDump(buf));
        } finally {
            buf.release();
        }
    }

    private static Result read(byte[] bytes) {
        var buf = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            var result = SearchResultPacket.read(buf);
            assertFalse(buf.isReadable());
            return result;
        } finally {
            buf.release();
        }
    }
}
