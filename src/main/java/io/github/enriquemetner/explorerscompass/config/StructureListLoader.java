package io.github.enriquemetner.explorerscompass.config;

import static io.github.enriquemetner.explorerscompass.config.ServerToml.quotedDiagnosticValue;

import io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Severity;
import io.github.enriquemetner.explorerscompass.structure.StructureSelector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Identifier;

final class StructureListLoader {
	static final String WHITELIST_KEY = "structureWhitelist";
	static final String BLACKLIST_KEY = "structureBlacklist";

	private StructureListLoader() {}

	record Result(List<String> whitelist, List<String> blacklist, StructureListLoad report) {}

	static Result load(Path whitelistFile, Path blacklistFile, Set<Identifier> registeredIds) {
		var whitelist = readStructureList(whitelistFile, WHITELIST_KEY, registeredIds);
		var report = whitelist.result();
		if (!report.valid() || !whitelist.selectors().isEmpty()) {
			if (hasBlacklistEntries(blacklistFile)) {
				var warning = new Diagnostic(Severity.WARNING, "whitelist.toml overrides the nonempty blacklist.toml");
				var diagnostics = new ArrayList<>(report.diagnostics());
				diagnostics.add(0, warning);
				report = new StructureListLoad(report.file(), report.valid(), warning, List.copyOf(diagnostics));
			}
			return new Result(whitelist.selectors(), List.of(), report);
		}
		var blacklist = readStructureList(blacklistFile, BLACKLIST_KEY, registeredIds);
		return new Result(List.of(), blacklist.selectors(), blacklist.result());
	}

	record StructureListLoad(Path file, boolean valid, Diagnostic override,
							 List<Diagnostic> diagnostics) {
		Section summary(int available, int loaded) {
			String message;
			String filename = file.getFileName().toString();
			String loadedCount = StartupConfigReport.structureCount(loaded);
			if (!valid) message = "Could not load " + filename + ". Loaded all " + StartupConfigReport.structureCount(available);
			else if (filename.equals("whitelist.toml")) message = "Whitelisted and loaded " + loadedCount;
			else message = "Blacklisted " + StartupConfigReport.structureCount(available - loaded) + " and loaded " + loadedCount;
			return new Section(message, valid ? null : Severity.ERROR, diagnostics, file);
		}
	}

	private static boolean hasBlacklistEntries(Path file) {
		try {
			return ServerToml.read(file).config().get(BLACKLIST_KEY) instanceof List<?> entries && !entries.isEmpty();
		} catch (IOException | IllegalArgumentException ignored) {
			return false;
		}
	}

	private record StructureList(List<String> selectors, StructureListLoad result) {}

	private static StructureList readStructureList(Path file, String key, Set<Identifier> registeredIds) {
		String filename = file.getFileName().toString();
		ServerToml document;
		try {
			document = ServerToml.read(file);
		} catch (IOException | IllegalArgumentException error) {
			var failure = new Diagnostic(Severity.ERROR, "Could not load " + filename + ": " + StartupConfigReport.cause(error));
			return new StructureList(List.of(), new StructureListLoad(file, false, null, List.of(failure)));
		}
		List<Diagnostic> diagnostics = new ArrayList<>();
		Object value = document.config().get(key);
		List<String> selectors = new ArrayList<>();
		var matchesById = new LinkedHashMap<Identifier, List<String>>();
		if (value instanceof List<?> entries) {
			for (Object entry : entries) {
				if (!(entry instanceof String selector)) {
					diagnostics.add(new Diagnostic(Severity.ERROR, filename + ": Expected a string selector in " + quotedDiagnosticValue(key)
							+ document.assignmentLocation(key) + ", found " + quotedDiagnosticValue(entry)));
					continue;
				}
				selectors.add(selector);
				String detail = quotedDiagnosticValue(selector) + document.selectorLocation(selector);
				Set<Identifier> matches;
				try {
					matches = new StructureSelector(selector).matchingIds(registeredIds);
				} catch (IllegalArgumentException error) {
					diagnostics.add(new Diagnostic(Severity.ERROR, filename + ": Invalid structure selector " + detail));
					continue;
				}
				if (matches.isEmpty()) diagnostics.add(new Diagnostic(Severity.ERROR, filename + ": No structures match selector " + detail));
				for (Identifier match : matches) matchesById.computeIfAbsent(match, ignored -> new ArrayList<>()).add(detail);
			}
		} else if (value != null) {
			diagnostics.add(new Diagnostic(Severity.ERROR, filename + ": Expected a string array for " + quotedDiagnosticValue(key)
					+ document.assignmentLocation(key)));
		}
		boolean valid = diagnostics.isEmpty();
		matchesById.forEach((id, entries) -> {
			if (entries.size() > 1) diagnostics.add(new Diagnostic(Severity.WARNING,
					filename + ": " + id + " is listed more than once by " + String.join(", ", entries)));
		});
		return new StructureList(valid ? List.copyOf(selectors) : List.of(),
				new StructureListLoad(file, valid, null, diagnostics));
	}
}
