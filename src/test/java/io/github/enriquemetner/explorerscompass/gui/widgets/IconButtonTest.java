package io.github.enriquemetner.explorerscompass.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IconButtonTest {
    @Test void centersUsingTheAssetWidthAndHeight() {
        assertEquals(16, new IconButton.Size(7, 7).centeredX(10, 20));
        assertEquals(15, new IconButton.Size(9, 9).centeredX(10, 20));
        assertEquals(25, new IconButton.Size(7, 9).centeredY(20, 20));
    }
}
