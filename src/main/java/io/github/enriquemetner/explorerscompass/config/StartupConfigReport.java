package io.github.enriquemetner.explorerscompass.config;

import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Severity;

import org.apache.logging.log4j.LogManager;

import java.nio.file.FileSystemException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class StartupConfigReport {
    public record Section(String summary, Severity level, List<Diagnostic> diagnostics) {
        public Section {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger("explorerscompass");
    private static List<Diagnostic> latest = List.of();

    private StartupConfigReport() {}

    public static List<Diagnostic> report(Severity severity) {
        return latest.stream().filter(entry -> severity == null || entry.severity() == severity).toList();
    }

    public static void clear() {
        latest = List.of();
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
        write(available, override, sections, (severity, message) -> {
            if (severity == Severity.ERROR) LOGGER.error(message);
            else if (severity == Severity.WARNING) LOGGER.warn(message);
            else LOGGER.info(message);
        });
    }

    static void write(int available, Diagnostic override, List<Section> sections, BiConsumer<Severity, String> log) {
        log.accept(null, available + " structures available");
        if (override != null) log.accept(override.severity(), override.message());
        for (Section section : sections) {
            log.accept(section.level(), section.summary());
        }
        if (sections.stream().anyMatch(section -> !section.diagnostics().isEmpty())) {
            log.accept(null, "Use /ecomp report for configuration diagnostics");
        }
    }
}
