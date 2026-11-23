package io.github.enriquemetner.explorerscompass.items;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompassSearchDataTest {
    private static final Identifier PYRAMID = new Identifier("minecraft:desert_pyramid");
    private static final Identifier OVERWORLD = new Identifier("minecraft:overworld");
    private static final UUID HOLDER = new UUID(1, 2);
    private static final String UNRELATED = "SearchEnergy:12,Damage:3,LastCompassHolder:[I;0,1,0,2],custom:{name:'Keep me'}";

    @Test void legacyFoundWritesOnlyTheOriginalFieldsAfterClearingPreviousSearch() throws Exception {
        var data = withOldTarget();
        CompassSearchData.clear(data);
        CompassSearchData.writeFound(data, PYRAMID, -21, 37, 8);
        assertEquals(nbt("State:2,StructureID:'minecraft:desert_pyramid',FoundX:-21,FoundZ:37,Samples:8"), data);
    }

    @Test void liveFoundStoresDestinationAndVisibilityAndKeepsOtherNbt() throws Exception {
        var data = withOldTarget();
        CompassSearchData.commitFound(data, PYRAMID, -21, 37, 8, OVERWORLD, 42L, true, false, true);
        assertEquals(nbt("""
                State:2,StructureID:'minecraft:desert_pyramid',FoundX:-21,FoundZ:37,Samples:8,
                FoundDimension:'minecraft:overworld',FoundStartChunk:42L,CreativeBypass:1b,
                DisplayCoordinates:0b,DisplayDistance:1b
                """), data);

        CompassSearchData.commitFound(data, PYRAMID, 7, 16, 2, OVERWORLD, 47L, false, true, false);
        assertEquals(nbt("""
                State:2,StructureID:'minecraft:desert_pyramid',FoundX:7,FoundZ:16,Samples:2,
                FoundDimension:'minecraft:overworld',FoundStartChunk:47L,DisplayCoordinates:1b,DisplayDistance:0b
                """), data);
    }

    @Test void rollbackRejectsStaleTokenAndRestoresOnlyTargetFields() throws Exception {
        var data = nbt("State:2,StructureID:'minecraft:desert_pyramid',FoundX:-21,FoundZ:37,Samples:8,InheritedCompassTarget:1b");
        var active = new UUID(3, 4);
        CompassSearchData.begin(data, active, CompassSearchData.snapshot(data));
        CompassSearchData.writeSearching(data, new Identifier("minecraft:fortress"));
        assertEquals(-21, CompassSearchData.pointingData(data).getInt("FoundX"));
        data.putInt("SearchEnergy", 9);
        data.putUuid(CompassTransfer.HOLDER, HOLDER);
        var beforeStaleRollback = data.copy();
        assertFalse(CompassSearchData.restore(data, new UUID(5, 6)));
        assertEquals(beforeStaleRollback, data);
        assertTrue(CompassSearchData.restore(data, active));
        var expected = nbt("State:2,StructureID:'minecraft:desert_pyramid',FoundX:-21,FoundZ:37,Samples:8,InheritedCompassTarget:1b");
        expected.putInt("SearchEnergy", 9);
        assertEquals(expected, data);
    }

    private static NbtCompound withOldTarget() throws Exception {
        var data = nbt("""
                State:1,StructureID:'minecraft:fortress',SearchRadius:100,PrevPos:[I;1,2,3],
                IsSet:1b,IsListed:1b,IsGroup:1b,InheritedCompassTarget:1b
                """);
        CompassSearchData.begin(data, new UUID(5, 6), CompassSearchData.snapshot(data));
        return data;
    }

    private static NbtCompound nbt(String targetFields) throws Exception {
        return StringNbtReader.parse("{" + UNRELATED + "," + targetFields + "}");
    }
}
