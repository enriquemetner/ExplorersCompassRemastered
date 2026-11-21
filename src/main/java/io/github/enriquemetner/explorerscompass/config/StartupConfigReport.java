package io.github.enriquemetner.explorerscompass.config;

import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Severity;

import org.apache.logging.log4j.LogManager;

import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class StartupConfigReport {
    public record Section(String summary, Severity level, List<Diagnostic> diagnostics, Path file) {
        public Section(String summary, Severity level, List<Diagnostic> diagnostics) {
            this(summary, level, diagnostics, null);
        }

        public Section {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger("explorerscompass");
    private static List<Diagnostic> latest = List.of();
    private static final Map<Path, String> preparedFiles = new LinkedHashMap<>();

    private StartupConfigReport() {}

    public static List<Diagnostic> report(Severity severity) {
        return latest.stream().filter(entry -> severity == null || entry.severity() == severity).toList();
    }

    public static void clear() {
        latest = List.of();
    }

    static void prepared(Path file, String name, boolean missing, boolean client) {
        String message = missing ? name + " doesn't exist. File generated" : "Empty " + name + " file. Defaults restored";
        if (client) LOGGER.info(message);
        else preparedFiles.put(normalize(file), message);
    }

    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }

    public static String cause(Exception error) {
        if (error instanceof FileSystemException failure) {
            return Objects.requireNonNullElse(failure.getReason(), failure.getClass().getSimpleName());
        }
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
    }

    public static void publish(int available, Diagnostic override, Section lists, Section rules, Section costs,
                               Section settings) {
        var sections = List.of(lists, rules, costs, settings);
        latest = sections.stream().flatMap(section -> section.diagnostics().stream()).toList();
        writeStartup(available, override, sections, (severity, message) -> {
            if (severity == Severity.ERROR) LOGGER.error(message);
            else if (severity == Severity.WARNING) LOGGER.warn(message);
            else LOGGER.info(message);
        });
    }

    static void write(int available, Diagnostic override, List<Section> sections, BiConsumer<Severity, String> log) {
        write(available, override, sections, Map.of(), log);
    }

    static void writeStartup(int available, Diagnostic override, List<Section> sections, BiConsumer<Severity, String> log) {
        var prepared = new LinkedHashMap<>(preparedFiles);
        preparedFiles.clear();
        write(available, override, sections, prepared, log);
    }

    private static void write(int available, Diagnostic override, List<Section> sections, Map<Path, String> prepared,
                              BiConsumer<Severity, String> log) {
        log.accept(null, structureCount(available) + " available");
        if (override != null) log.accept(override.severity(), override.message());
        prepared.values().forEach(message -> log.accept(null, message));
        for (Section section : sections) {
            if (section.level() == null && section.file() != null && prepared.containsKey(normalize(section.file()))) continue;
            log.accept(section.level(), section.summary());
        }
        if (sections.stream().anyMatch(section -> !section.diagnostics().isEmpty())) {
            log.accept(null, "Use /ecomp report for configuration diagnostics");
        }
    }

    static String structureCount(int count) {
        return count + (count == 1 ? " structure" : " structures");
    }
}
