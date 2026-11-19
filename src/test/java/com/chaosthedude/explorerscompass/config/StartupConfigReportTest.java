package com.chaosthedude.explorerscompass.config;

import com.chaosthedude.explorerscompass.config.StartupConfigReport.Section;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules.Severity;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StartupConfigReportTest {
    @TempDir Path config;
    private static final Identifier CITY = new Identifier("minecraft:ancient_city");
    private static final Identifier VILLAGE = new Identifier("minecraft:village_plains");

    @Test void cleanStartupPrintsOnlySummaries() {
        var lists = new Section("Blacklisted 1 structures and loaded 1 structures", null, List.of());
        var rules = new Section("Successfully loaded 0 unlocking requirements and 0 prerequisites", null, List.of());
        var costs = new Section("Successfully loaded search_costs.json successfully", null, List.of());
        var settings = new Section("Successfully loaded 11/11 settings", null, List.of());
        var messages = new ArrayList<String>();
        StartupConfigReport.write(2, null, List.of(lists, rules, costs, settings), (severity, text) -> {
            assertNull(severity);
            messages.add(text);
        });
        assertEquals(List.of("2 structures available", lists.summary(), rules.summary(), costs.summary(), settings.summary()), messages);
    }

    @Test void terminalSummariesOmitCountsAndReportKeepsFailureDetails() throws IOException {
        var previousWhitelist = ExplorersCompassConfig.structureWhitelist;
        var previousBlacklist = ExplorersCompassConfig.structureBlacklist;
        try {
            var whitelist = Files.writeString(config.resolve("whitelist.toml"), """
                    structureWhitelist = [
                      "ancient_*",
                      "minecraft:ancient_city"
                    ]
                    """);
            var blacklist = Files.writeString(config.resolve("blacklist.toml"), "structureBlacklist = [\"village_plains\"]");
            var lists = ExplorersCompassConfig.loadStructureLists(whitelist, blacklist, Set.of(CITY, VILLAGE));
            assertEquals(List.of("ancient_*", "minecraft:ancient_city"), ExplorersCompassConfig.structureWhitelist);
            assertTrue(lists.diagnostics().get(1).message().contains("at line 2"));
            assertTrue(lists.diagnostics().get(1).message().contains("at line 3"));

            var invalidRule = new Diagnostic(Severity.ERROR, "unlocking_rules.json: Ignored invalid requirement at line 2");
            var rules = new Section("Successfully loaded 1 unlocking requirements and 0 prerequisites", null, List.of(invalidRule));
            var invalidCost = new Diagnostic(Severity.ERROR, "Could not load search_costs.json: Invalid JSON at line 3");
            var costs = new Section("Could not load search_costs.json. All structures fell back to costing nothing",
                    Severity.ERROR, List.of(invalidCost));
            var instance = Files.writeString(config.resolve("settings.toml"), "[search]\nmaxSearchRadius = \"far\"\n");
            var settings = ExplorersCompassConfig.loadServerSettings(instance, null);
            assertEquals(Severity.WARNING, settings.level());
            assertTrue(settings.diagnostics().get(0).message().contains("at line 2"));

            var listSummary = lists.summary(2, 1);
            var messages = new ArrayList<String>();
            StartupConfigReport.write(2, lists.override(), List.of(listSummary, rules, costs, settings),
                    (severity, text) -> messages.add(text));
            assertEquals(List.of(
                    "2 structures available",
                    "whitelist.toml overrides the nonempty blacklist.toml",
                    "Whitelisted and loaded 1 structures",
                    "Successfully loaded 1 unlocking requirements and 0 prerequisites",
                    "Could not load search_costs.json. All structures fell back to costing nothing",
                    "10/11 settings loaded successfully",
                    "Use /ecomp report for configuration diagnostics"
            ), messages);
            StartupConfigReport.publish(2, lists.override(), listSummary, rules, costs, settings);
            assertEquals(5, StartupConfigReport.report(null).size());
            assertEquals(3, StartupConfigReport.report(Severity.ERROR).size());
            assertTrue(StartupConfigReport.report(Severity.ERROR).contains(invalidCost));
            assertEquals(2, StartupConfigReport.report(Severity.WARNING).size());

            Files.writeString(whitelist, "structureWhitelist = [");
            var failed = ExplorersCompassConfig.loadStructureLists(whitelist, blacklist, Set.of(CITY, VILLAGE));
            assertFalse(failed.valid());
            assertEquals(lists.override(), failed.override());
            assertTrue(failed.diagnostics().contains(failed.override()));
            assertTrue(ExplorersCompassConfig.structureBlacklist.isEmpty());
            messages.clear();
            StartupConfigReport.write(2, failed.override(), List.of(failed.summary(2, 2), rules, costs, settings),
                    (severity, text) -> messages.add(text));
            assertEquals(failed.override().message(), messages.get(1));
            assertEquals("Could not load whitelist.toml. Loaded all 2 structures", messages.get(2));
        } finally {
            StartupConfigReport.clear();
            ExplorersCompassConfig.structureWhitelist = previousWhitelist;
            ExplorersCompassConfig.structureBlacklist = previousBlacklist;
            ExplorersCompassConfig.loadServerSettings(config.resolve("missing.toml"), null);
        }
    }
}
