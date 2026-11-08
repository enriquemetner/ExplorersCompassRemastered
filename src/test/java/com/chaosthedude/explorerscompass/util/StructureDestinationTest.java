package com.chaosthedude.explorerscompass.util;

import java.util.List;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StructureDestinationTest {
    private static final BlockPos FALLBACK = new BlockPos(16, 0, 32);

    @Test void keepsTheCenterWhenItFallsInsideAPiece() {
        var piece = new BlockBox(-12, 20, 0, -2, 30, 10);
        var target = StructureDestination.refine(List.of(piece), FALLBACK);
        assertEquals(-7, target.pos().getX());
        assertEquals(5, target.pos().getZ());
        assertSame(piece, target.piece());
    }

    @Test void snapsACourtyardCenterToTheInsideEdgeOfAPiece() {
        var left = new BlockBox(0, 20, 0, 4, 30, 20);
        var right = new BlockBox(16, 20, 0, 20, 30, 20);
        var back = new BlockBox(5, 20, 16, 15, 30, 20);
        var target = StructureDestination.refine(List.of(left, right, back), FALLBACK);
        assertSame(left, target.piece());
        assertEquals(3, target.pos().getX());
        assertEquals(10, target.pos().getZ());
    }

    @Test void comparesEuclideanDistanceToTheFootprintAndKeepsThinPiecesUsable() {
        var diagonal = new BlockBox(3, 20, 3, 3, 30, 3);
        var straight = new BlockBox(5, 20, 0, 5, 30, 0);
        var farNegative = new BlockBox(-10, 20, -10, -10, 30, -10);
        var farPositive = new BlockBox(10, 20, 10, 10, 30, 10);
        var target = StructureDestination.refine(List.of(straight, diagonal, farNegative, farPositive), FALLBACK);
        assertSame(diagonal, target.piece());
        assertEquals(3, target.pos().getX());
        assertEquals(3, target.pos().getZ());
    }

    @Test void usesTheSuppliedCoordinatesWhenThePiecesAreUnavailable() {
        assertEquals(FALLBACK, StructureDestination.refine(List.of(), FALLBACK).pos());
    }
}
