package io.github.enriquemetner.explorerscompass.network;

import com.google.common.collect.ArrayListMultimap;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncPacketTest {
    // Captured from the pre-refactor writer with the ordered inputs in writesTheOriginalWireLayout.
    private static final byte[] WIRE = HexFormat.of().parseHex("""
            0100010000000406746573743a62000000020b746573743a6e65746865720e746573743a6f766572776f726c64087465
            73743a73657406746573743a61000000010e746573743a6f766572776f726c6408746573743a73657406746573743a63
            0000000009746573743a6e6f6e6506746573743a640000000108746573743a656e6408746573743a7365740102067465
            73743a6206746573743a640106746573743a63000000030e7b2274657874223a22416c6c227d00010000000001107b22
            74657874223a225669736974227d010000000106746573743a610000107b2274657874223a224669727374227d000000
            00000000107b2274657874223a225061727479227d010000000000000000000206746573743a62000000000674657374
            3a61000000010000000206746573743a640000000206746573743a63000000000206746573743a7a06746573743a7901
            00
            """.replaceAll("\\s", ""));

    private static final int LATEST_COUNT = 140;
    private static final int LOCKED_COUNT = 268;
    private static final int PARTY_START = 294;
    private static final int EXCLUDED_START = 320;
    private static final int WHITELIST_START = 335;
    private static final int SHOW_REQUIREMENTS_START = 336;

    @Test void writesTheOriginalWireLayout() {
        var a = id("a");
        var b = id("b");
        var c = id("c");
        var d = id("d");
        var set = id("set");
        var dimensions = ArrayListMultimap.<Identifier, Identifier>create();
        dimensions.putAll(b, List.of(id("nether"), id("overworld")));
        dimensions.put(a, id("overworld"));
        dimensions.put(d, id("end"));
        Map<Identifier, RequirementNode> locked = new LinkedHashMap<>();
        locked.put(b, sharedTree());
        locked.put(a, new RequirementNode(Text.literal("First"), false, List.of()));
        Map<Identifier, RequirementNode> party = new LinkedHashMap<>();
        party.put(d, new RequirementNode(Text.literal("Party"), true, List.of()));
        party.put(c, sharedTree());
        var latest = new LinkedHashSet<>(List.of(d, b));
        var packet = new SyncPacket(true, false, true, List.of(b, a, c, d), dimensions,
                Map.of(a, set, b, set, c, id("none"), d, set), locked, party, c, latest, true,
                List.of(id("z"), id("y")), true, false);
        try {
            assertArrayEquals(WIRE, ByteBufUtil.getBytes(packet));
        } finally {
            packet.release();
        }
    }

    @Test void readsTheOriginalCatalogueAndSharedRequirementTable() {
        var packet = readThrough(WIRE.length);
        assertTrue(packet.canTeleport());
        assertFalse(packet.canExcludeVisited());
        assertTrue(packet.infiniteXp());
        assertEquals(List.of(id("b"), id("a"), id("c"), id("d")), packet.allowedStructureIDs());
        assertEquals(List.of(id("nether"), id("overworld")), packet.dimensionIDs().get(id("b")));
        assertTrue(packet.dimensionIDs().get(id("c")).isEmpty());
        assertEquals(id("none"), packet.setIDs().get(id("c")));
        assertEquals(id("c"), packet.trackedStructureId());
        assertEquals(Set.of(id("b"), id("d")), packet.latestUnlocked());
        assertTrue(packet.showNewlyUnlockedFilter());
        assertFalse(packet.showStructureRequirements());
        assertTrue(packet.whitelistActive());
        assertEquals(Set.of(id("y"), id("z")), packet.excludedStructures());
        assertEquals(sharedTree(), packet.lockedRequirements().get(id("b")));
        assertSame(packet.lockedRequirements().get(id("b")), packet.partyRequirements().get(id("c")));
        assertEquals("First", packet.lockedRequirements().get(id("a")).label().getString());
        assertEquals("Party", packet.partyRequirements().get(id("d")).label().getString());
    }

    @Test void acceptsEveryLegacyOptionalTailBoundary() {
        for (int end : List.of(PARTY_START, EXCLUDED_START, WHITELIST_START, SHOW_REQUIREMENTS_START)) {
            var packet = readThrough(end);
            assertEquals(sharedTree(), packet.lockedRequirements().get(id("b")));
            assertEquals(end == PARTY_START ? Set.of() : Set.of(id("c"), id("d")), packet.partyRequirements().keySet());
            assertEquals(end <= EXCLUDED_START ? Set.of() : Set.of(id("y"), id("z")), packet.excludedStructures());
            assertEquals(end == SHOW_REQUIREMENTS_START, packet.whitelistActive());
            assertTrue(packet.showStructureRequirements(), "Absent show-requirements inherits show-latest");
        }
        var noLatest = Arrays.copyOf(WIRE, PARTY_START);
        noLatest[LATEST_COUNT - 1] = 0;
        assertFalse(read(noLatest).showStructureRequirements());
    }

    @Test void rejectsInvalidCountsAtTheirExistingBounds() {
        for (int count : List.of(-1, 100_001)) {
            assertInvalidCount(LATEST_COUNT, buf -> buf.writeVarInt(count), "Invalid latest unlocked count");
            assertInvalidCount(EXCLUDED_START, buf -> buf.writeVarInt(count), "Invalid excluded structure count");
        }
        for (int count : List.of(-1, 5)) {
            assertInvalidCount(LOCKED_COUNT, buf -> buf.writeInt(count), "Invalid requirements count");
            assertInvalidCount(PARTY_START, buf -> buf.writeInt(count), "Invalid requirements count");
        }
    }

    private static Identifier id(String path) {
        return new Identifier("test", path);
    }

    private static RequirementNode sharedTree() {
        return new RequirementNode(Text.literal("All"), false, true,
                List.of(RequirementNode.structure(Text.literal("Visit"), true, id("a"))));
    }

    private static SyncPacket.Contents readThrough(int end) {
        return read(Arrays.copyOf(WIRE, end));
    }

    private static SyncPacket.Contents read(byte[] bytes) {
        var buffer = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            var packet = SyncPacket.read(buffer);
            assertFalse(buffer.isReadable());
            return packet;
        } finally {
            buffer.release();
        }
    }

    private static void assertInvalidCount(int offset, Consumer<PacketByteBuf> writeCount, String expected) {
        var buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeBytes(WIRE, 0, offset);
            writeCount.accept(buffer);
            assertEquals(expected, assertThrows(IllegalArgumentException.class, () -> SyncPacket.read(buffer)).getMessage());
        } finally {
            buffer.release();
        }
    }
}
