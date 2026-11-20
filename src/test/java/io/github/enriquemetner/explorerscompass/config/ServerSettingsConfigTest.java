package io.github.enriquemetner.explorerscompass.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsConfigTest {
    @TempDir Path directory;

    @AfterEach void resetSettings() {
        ExplorersCompassConfig.loadServerSettings(directory.resolve("missing.toml"), null);
    }

    @Test void movesLegacyProgressionSettingsAndUsesTheNewValueWhenBothArePresent() throws IOException {
        Path file = Files.writeString(directory.resolve("settings.toml"), """
                [unlocking]
                opacPartySharing = true
                visitsSharing = true
                compassSharing = true
                relockOnReload = true
                [progression]
                opacPartySharing = false
                """);

        ExplorersCompassConfig.loadServerSettings(file, null);
        assertFalse(ExplorersCompassConfig.opacPartySharing);
        assertTrue(ExplorersCompassConfig.visitsSharing);
        assertTrue(ExplorersCompassConfig.compassSharing);
        assertTrue(ExplorersCompassConfig.relockOnReload);
        var saved = ServerToml.read(file).config();
        assertEquals(List.of("compass", "search", "progression"), List.copyOf(saved.valueMap().keySet()));
        CommentedConfig progression = saved.get("progression");
        assertEquals(List.of("relockOnReload", "compassSharing", "opacPartySharing", "visitsSharing"),
                List.copyOf(progression.valueMap().keySet()));
        assertEquals(Boolean.FALSE, progression.get("opacPartySharing"));
        assertEquals(Boolean.TRUE, progression.get("visitsSharing"));
    }

    @Test void invalidValuesUseTheirOwnDefaultsAndPreserveTheFile() throws IOException {
        for (String invalidRadius : List.of("\"far\"", "-1", "50001", "150.5", "inf", "9999999999")) {
            String source = """
                    compass = false
                    [search]
                    maxSearchRadius = %s
                    allowExcludeVisited = false
                    [unlocking]
                    compassSharing = true
                    opacPartySharing = "no"
                    """.formatted(invalidRadius);
            Path file = Files.writeString(directory.resolve("settings.toml"), source);
            ExplorersCompassConfig.loadServerSettings(file, null);
            assertEquals(30_000, ExplorersCompassConfig.maxSearchRadius, invalidRadius);
            assertTrue(ExplorersCompassConfig.displayCoordinates);
            assertFalse(ExplorersCompassConfig.allowExcludeVisited);
            assertTrue(ExplorersCompassConfig.compassSharing);
            assertTrue(ExplorersCompassConfig.opacPartySharing);
            assertEquals(source, Files.readString(file));
        }
    }

    @Test void malformedWorldSettingsFallBackToInstanceThenBuiltInDefaults() throws IOException {
        Path instance = Files.writeString(directory.resolve("instance.toml"), """
                [compass]
                useClassicCompass = true
                [search]
                maxSearchRadius = 12345
                """);
        String malformed = """
                [compass]
                useClassicCompass = false
                [search]
                maxSearchRadius = [1, 2
                """;
        Path world = Files.writeString(directory.resolve("world.toml"), malformed);
        ExplorersCompassConfig.loadServerSettings(instance, world);
        assertEquals(12345, ExplorersCompassConfig.maxSearchRadius);
        assertTrue(ExplorersCompassConfig.useClassicCompass);
        assertEquals(malformed, Files.readString(world));

        Files.writeString(instance, malformed);
        ExplorersCompassConfig.loadServerSettings(instance, world);
        assertEquals(30_000, ExplorersCompassConfig.maxSearchRadius);
        assertFalse(ExplorersCompassConfig.useClassicCompass);
        assertEquals(malformed, Files.readString(instance));
    }

    @Test void parsedWorldSettingsUseBuiltInDefaultsForMissingAndInvalidValues() throws IOException {
        Path instance = Files.writeString(directory.resolve("instance.toml"), """
                [compass]
                useClassicCompass = true
                [search]
                maxSearchRadius = 12345
                """);
        ExplorersCompassConfig.loadServerSettings(instance, null);
        Path world = Files.writeString(directory.resolve("world.toml"), """
                [search]
                maxSearchRadius = "far"
                allowExcludeVisited = false
                """);

        ExplorersCompassConfig.loadServerSettings(instance, world);
        assertEquals(30_000, ExplorersCompassConfig.maxSearchRadius);
        assertFalse(ExplorersCompassConfig.useClassicCompass);
        assertFalse(ExplorersCompassConfig.allowExcludeVisited);
    }
}
