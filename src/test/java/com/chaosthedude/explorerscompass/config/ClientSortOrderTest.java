package com.chaosthedude.explorerscompass.config;

import java.util.List;

import com.electronwill.nightconfig.toml.TomlFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSortOrderTest {
    @Test void migratesTheLegacySortKeyWithoutChangingItsPriority() {
        List<String> previous = ExplorersCompassConfig.sortOrder;
        var config = TomlFormat.newConfig();
        try {
            config.set("sorting.sortOrder", List.of("SOURCE", "GROUP", "NAME", "DIMENSION"));
            assertTrue(ExplorersCompassConfig.loadClient(config));
            var expected = List.of("SOURCE", "SET", "NAME", "DIMENSION");
            assertEquals(expected, ExplorersCompassConfig.sortOrder);

            config.set("sorting.sortOrder", expected);
            assertFalse(ExplorersCompassConfig.loadClient(config));
            assertEquals(expected, ExplorersCompassConfig.sortOrder);
        } finally {
            ExplorersCompassConfig.sortOrder = previous;
        }
    }
}
