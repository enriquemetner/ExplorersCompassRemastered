package com.chaosthedude.explorerscompass.config;

import com.chaosthedude.explorerscompass.util.StructureUtils;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StructureListsConfigTest {
    private static final Identifier CITY = new Identifier("minecraft:ancient_city");
    private static final Identifier VILLAGE = new Identifier("minecraft:village_plains");

    @TempDir Path configDir;

    @Test void invalidFilesFallBackWithoutAbortingOrKeepingPreviousFilters() throws IOException {
        var previousWhitelist = ExplorersCompassConfig.structureWhitelist;
        var previousBlacklist = ExplorersCompassConfig.structureBlacklist;
        try {
            assertFilters("[]", "[\"ancient_city\"]", true, false);
            for (String invalid : List.of("[", "123", "[\"village_*\", false]", "[\"village_*\", \"missing\"]",
                    "[\"no_such_mod:*\"]", "[\"village_?\"]")) {
                assertFilters(invalid, "[\"ancient_city\"]", false, false);
                assertFilters("[\"village_*\"]", invalid, true, false);
                assertFilters("[]", invalid, false, false);
            }
            Files.delete(configDir.resolve("whitelist.toml"));
            ExplorersCompassConfig.loadStructureLists(configDir.resolve("whitelist.toml"),
                    configDir.resolve("blacklist.toml"), Set.of(CITY, VILLAGE));
            assertFalse(StructureUtils.structureIdIsExcluded(CITY));
            assertFalse(StructureUtils.structureIdIsExcluded(VILLAGE));
        } finally {
            ExplorersCompassConfig.structureWhitelist = previousWhitelist;
            ExplorersCompassConfig.structureBlacklist = previousBlacklist;
        }
    }

    private void assertFilters(String whitelistValue, String blacklistValue, boolean cityExcluded, boolean villageExcluded)
            throws IOException {
        String whitelistContents = "structureWhitelist = " + whitelistValue + "\n";
        String blacklistContents = "structureBlacklist = " + blacklistValue + "\n";
        Path whitelist = Files.writeString(configDir.resolve("whitelist.toml"), whitelistContents);
        Path blacklist = Files.writeString(configDir.resolve("blacklist.toml"), blacklistContents);

        ExplorersCompassConfig.loadStructureLists(whitelist, blacklist, Set.of(CITY, VILLAGE));

        assertEquals(cityExcluded, StructureUtils.structureIdIsExcluded(CITY));
        assertEquals(villageExcluded, StructureUtils.structureIdIsExcluded(VILLAGE));
        assertEquals(whitelistContents, Files.readString(whitelist));
        assertEquals(blacklistContents, Files.readString(blacklist));
    }
}
