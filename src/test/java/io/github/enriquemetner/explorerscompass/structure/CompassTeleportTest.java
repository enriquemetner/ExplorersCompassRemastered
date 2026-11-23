package io.github.enriquemetner.explorerscompass.structure;

import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompassTeleportTest {
    @Test void prefersTheRefinedPointAndIgnoresPiecesOutsideTheLandingArea() {
        var target = new BlockPos(0, 0, 0);
        var exact = List.of(new BlockBox(-2, 20, -2, 2, 24, 2));
        var contains = List.of(new BlockBox(0, 20, 0, 5, 24, 5));
        var nearby = List.of(new BlockBox(2, 20, -1, 5, 24, 1));
        var outside = List.of(new BlockBox(3, 20, -1, 5, 24, 1));
        assertTrue(CompassTeleport.matchDistance(exact, target) < CompassTeleport.matchDistance(contains, target));
        assertEquals(4, CompassTeleport.matchDistance(nearby, target));
        assertEquals(Integer.MAX_VALUE, CompassTeleport.matchDistance(outside, target));
    }

    @Test void resolvesTiesToTheCurrentDimensionAndReportsOtherTies() throws CommandSyntaxException {
        var chunk = new ChunkPos(0, 0);
        var overworldKey = new Identifier("minecraft:overworld");
        var netherKey = new Identifier("minecraft:the_nether");
        var endKey = new Identifier("minecraft:the_end");
        var nether = new CompassTeleport.Match(netherKey, chunk, -1);
        var end = new CompassTeleport.Match(endKey, chunk, -1);
        assertEquals(nether, CompassTeleport.selectMatch(List.of(end, nether), netherKey));
        assertThrows(CommandSyntaxException.class, () -> CompassTeleport.selectMatch(List.of(end, nether), overworldKey));
        var nearby = new CompassTeleport.Match(overworldKey, chunk, 4);
        assertEquals(nether, CompassTeleport.selectMatch(List.of(nearby, nether), overworldKey));
    }
}
