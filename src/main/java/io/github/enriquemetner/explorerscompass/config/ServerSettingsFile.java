package io.github.enriquemetner.explorerscompass.config;

import static io.github.enriquemetner.explorerscompass.config.ServerToml.quotedDiagnosticValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;

import io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Severity;

final class ServerSettingsFile {
    static final int DEFAULT_SEARCH_RADIUS = 30_000;

    private static final String SEARCH_SAVED_STRUCTURES = "search.findOldStructures";
    private static final String SEARCH_RADIUS = "search.maxSearchRadius";
    private static final int MAX_SEARCH_RADIUS = 50_000;

    private ServerSettingsFile() {}

    static String defaultContents() {
        ServerToml document = emptyDocument();
        readSettings(document, "settings.toml");
        writeSettings(document.config());
        return new TomlWriter().writeToString(document.config());
    }

    private static ServerToml emptyDocument() {
        return new ServerToml(TomlFormat.newConfig(LinkedHashMap::new), List.of());
    }

    static Section load(Path instanceFile, Path worldFile) {
        List<Diagnostic> failures = new ArrayList<>();
        Path selectedFile = worldFile == null ? instanceFile : worldFile;
        ServerToml document = tryRead(selectedFile, !selectedFile.equals(instanceFile), failures);
        if (document == null && !selectedFile.equals(instanceFile)) {
            selectedFile = instanceFile;
            document = tryRead(selectedFile, false, failures);
        }
        if (document == null) {
            readSettings(emptyDocument(), "settings.toml");
            return new Section("Could not load settings.toml. All settings fell back to built-in defaults",
                    Severity.ERROR, failures);
        }
        CommentedConfig config = document.config();
        var settings = readSettings(document, selectedFile.getFileName().toString());
        List<Diagnostic> diagnostics = new ArrayList<>(failures);
        diagnostics.addAll(settings.diagnostics);
        String count = settings.loaded + "/" + settings.total;
        String summary = settings.valid ? "Successfully loaded " + count + " settings"
                : count + " settings loaded. Some settings were rejected";
        if (!failures.isEmpty()) summary = "Could not load world settings.toml. Using instance settings. " + summary;
        Severity level = !failures.isEmpty() ? Severity.ERROR : diagnostics.isEmpty() ? null : Severity.WARNING;
        var result = new Section(summary, level, diagnostics, selectedFile);
        if (!settings.valid) return result;

        TomlWriter writer = new TomlWriter();
        String before = writer.writeToString(config);
        writeSettings(config);
        if (!before.equals(writer.writeToString(config))) {
            try {
                ExplorersCompassConfig.writeToml(selectedFile, config);
            } catch (IOException ignored) {
                // Settings are already loaded even if the file cannot be updated.
            }
        }
        return result;
    }

    private static ServerToml tryRead(Path path, boolean world, List<Diagnostic> failures) {
        try {
            return ServerToml.read(path);
        } catch (IOException | IllegalArgumentException error) {
            String scope = world ? "world" : "instance";
            failures.add(new Diagnostic(Severity.ERROR,
                    "Could not load " + scope + " " + path.getFileName() + ": " + StartupConfigReport.cause(error)));
            return null;
        }
    }

    private static ServerSettingsReader readSettings(ServerToml document, String filename) {
        var settings = new ServerSettingsReader(document, filename);
        ExplorersCompassConfig.maxSearchRadius = settings.searchRadius();
        ExplorersCompassConfig.findOldStructures = settings.bool(SEARCH_SAVED_STRUCTURES, false);
        ExplorersCompassConfig.compassSharing = settings.progressionBool("compassSharing", false);
        ExplorersCompassConfig.visitsSharing = settings.progressionBool("visitsSharing", false);
        ExplorersCompassConfig.opacPartySharing = settings.progressionBool("opacPartySharing", true);
        ExplorersCompassConfig.allowExcludeVisited = settings.bool("search.allowExcludeVisited", true);
        ExplorersCompassConfig.relockOnReload = settings.progressionBool("relockOnReload", false);
        ExplorersCompassConfig.displayCoordinates = settings.bool("compass.displayCoordinates", true);
        ExplorersCompassConfig.displayDistance = settings.bool("compass.displayDistance", true);
        ExplorersCompassConfig.showNetherEquivalent = settings.bool("compass.showNetherEquivalent", false);
        ExplorersCompassConfig.useClassicCompass = settings.bool("compass.useClassicCompass", false);
        return settings;
    }

    private static final class ServerSettingsReader {
        private final ServerToml document;
        private final String filename;
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private boolean valid = true;
        private int loaded;
        private int total;

        private ServerSettingsReader(ServerToml document, String filename) {
            this.document = document;
            this.filename = filename;
            Object permissions = document.config().get("permissions");
            if (permissions != null && !(permissions instanceof CommentedConfig)) {
                invalid("permissions", "a table");
            }
        }

        private Object value(String key) {
            String section = key.substring(0, key.indexOf('.'));
            Object parent = document.config().get(section);
            if (parent != null && !(parent instanceof CommentedConfig)) {
                throw ExplorersCompassConfig.bad(section, "a table");
            }
            return document.config().get(key);
        }

        private void invalid(String key, String expected) {
            valid = false;
            String location = document.assignmentLocation(key);
            Object received;
            try {
                received = document.config().get(key);
            } catch (ClassCastException ignored) {
                received = null;
            }
            if (location.isEmpty() && key.contains(".")) {
                location = document.assignmentLocation(key.substring(0, key.indexOf('.')));
            }
            diagnostics.add(new Diagnostic(Severity.ERROR, filename + ": Invalid setting " + quotedDiagnosticValue(key) + location
                    + ". Expected " + expected + (received == null ? "" : ", found " + quotedDiagnosticValue(received))));
        }

        private boolean progressionBool(String key, boolean fallback) {
            Object progression = document.config().get("progression");
            if (progression == null || progression instanceof CommentedConfig section && !section.contains(key)) {
                return bool("unlocking." + key, fallback);
            }
            return bool("progression." + key, fallback);
        }

        private boolean bool(String key, boolean fallback) {
            total++;
            try {
                Object value = value(key);
                if (value == null || value instanceof Boolean) {
                    loaded++;
                    return value instanceof Boolean result ? result : fallback;
                }
            } catch (IllegalArgumentException ignored) {
                // The setting's containing table is invalid.
            }
            invalid(key, "a boolean in its settings table");
            return fallback;
        }

        private int searchRadius() {
            total++;
            try {
                int radius = ExplorersCompassConfig.readInteger(value(SEARCH_RADIUS), SEARCH_RADIUS, DEFAULT_SEARCH_RADIUS, 1);
                if (radius <= MAX_SEARCH_RADIUS) {
                    loaded++;
                    return radius;
                }
            } catch (IllegalArgumentException ignored) {
                // The setting or its containing table is invalid.
            }
            invalid(SEARCH_RADIUS, "an integer between 1 and " + MAX_SEARCH_RADIUS);
            return DEFAULT_SEARCH_RADIUS;
        }
    }

    private static void moveToPosition(CommentedConfig config, String key, int index) {
        if (config.valueMap().keySet().stream().skip(index).findFirst().filter(key::equals).isPresent()) return;
        var values = config.valueMap();
        Object value = values.remove(key);
        var laterKeys = values.keySet().stream().skip(index).toList();
        var laterValues = new LinkedHashMap<String, Object>();
        for (String laterKey : laterKeys) laterValues.put(laterKey, values.remove(laterKey));
        values.put(key, value);
        values.putAll(laterValues);
    }

    private static void writeSettings(CommentedConfig config) {
        config.remove("permissions.allowTeleport");
        config.remove("compass.compassDurability");
        config.remove("search.defaultXpCost");
        config.remove("search.allowSearchWithoutEnoughXp");
        config.remove("search.maxSamples");

        if (config.get("unlocking") instanceof CommentedConfig legacy) {
            for (String key : List.of("relockOnReload", "compassSharing", "opacPartySharing", "visitsSharing")) {
                legacy.remove(key);
            }
            if (legacy.isEmpty()) config.remove("unlocking");
        }

        CommentedConfig compass = orderedSection(config, "compass", 0);
        writeSetting(compass, "displayCoordinates", ExplorersCompassConfig.displayCoordinates, 0,
                "Show the coordinates of located structures.");
        writeSetting(compass, "displayDistance", ExplorersCompassConfig.displayDistance, 1,
                "Show the distance to located structures.");
        writeSetting(compass, "showNetherEquivalent", ExplorersCompassConfig.showNetherEquivalent, 2,
                "Point to equivalent coordinates when tracking a structure across the Overworld and Nether.");
        writeSetting(compass, "useClassicCompass", ExplorersCompassConfig.useClassicCompass, 3,
                "Use the original Explorer’s Compass texture and crafting recipe.");

        CommentedConfig search = orderedSection(config, "search", 1);
        writeSetting(search, "allowExcludeVisited", ExplorersCompassConfig.allowExcludeVisited, 0,
                "Allow players to exclude visited structure instances from searches. Visits are always recorded.");
        writeSetting(search, "findOldStructures", ExplorersCompassConfig.findOldStructures, 1,
                "Find existing structures saved under older world generation rules. This has highly variable impact on search duration in explored areas." + "\n"
                + "Usually only needed if world or structure generation changed significantly through mods or datapacks AFTER those chunks were generated.");
        writeSetting(search, "maxSearchRadius", ExplorersCompassConfig.maxSearchRadius, 2,
                "Maximum search radius in blocks (Max. 50000).");

        CommentedConfig progression = orderedSection(config, "progression", 2);
        writeSetting(progression, "relockOnReload", ExplorersCompassConfig.relockOnReload, 0,
                "Relock structures at world startup when their unlock requirements change or a prerequisite is no longer met.");
        writeSetting(progression, "compassSharing", ExplorersCompassConfig.compassSharing, 1,
                "Unlock structures which a previous compass holder had unlocked after getting a hold of their compass.");
        writeSetting(progression, "opacPartySharing", ExplorersCompassConfig.opacPartySharing, 2,
                "Share structure unlocks among OPAC party members (not permanent). Requires Open Parties and Claims mod.");
        writeSetting(progression, "visitsSharing", ExplorersCompassConfig.visitsSharing, 3,
                "Include visit history when sharing is enabled for OPAC parties or compass transfers");
    }

    private static CommentedConfig orderedSection(CommentedConfig config, String key, int index) {
        CommentedConfig section = config.get(key);
        if (section == null) {
            section = config.createSubConfig();
            config.set(key, section);
        }
        moveToPosition(config, key, index);
        return section;
    }

    private static void writeSetting(CommentedConfig section, String key, Object value, int index, String comment) {
        section.set(key, value);
        section.setComment(key, comment);
        moveToPosition(section, key, index);
    }
}
