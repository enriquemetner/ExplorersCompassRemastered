package io.github.enriquemetner.explorerscompass.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Diagnostic;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.Severity;
import io.github.enriquemetner.explorerscompass.util.OverlaySide;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ExplorersCompassConfig {
	public static final String SEARCH_COSTS = "server/search_costs.json";
	public static final String UNLOCKING_RULES = "server/unlocking_rules.json";
	public static final String DEFAULT_UNLOCKING_RULES = """
			{
			  "default": "unlocked",
			  "requirements": {},
			  "prerequisites": {}
			}
			""";
	private static final String RELOCK_COMMENT =
			"Relock player structures at world startup when unlocking definitions changed, then restore satisfied requirements.";
	private static final String SERVER_SETTINGS = "server/settings.toml";
	private static final String CLIENT_SETTINGS = "client/settings.toml";
	private static final String WHITELIST_KEY = "structureWhitelist";
	private static final String BLACKLIST_KEY = "structureBlacklist";
	private static final String WHITELIST = "server/whitelist.toml";
	private static final String STRUCTURE_LIST_COMMENTS =
			" Supports the * wildcard. Omitted namespaces default to minecraft:.\n"
			+ " If the whitelist is modified, the blacklist is ignored.";
	private static final String BLACKLIST = "server/blacklist.toml";
	private static final String HIDDEN_STRUCTURES = "client/hidden_structures.toml";
	private static final String SEARCH_SAVED_STRUCTURES = "search.findOldStructures";
	private static final String SEARCH_RADIUS = "search.maxSearchRadius";
	private static final int DEFAULT_SEARCH_RADIUS = 30_000;
	private static final int MAX_SEARCH_RADIUS = 50_000;

	public static boolean findOldStructures = false;
	public static boolean allowExcludeVisited = true;
	public static int maxSearchRadius = DEFAULT_SEARCH_RADIUS;
	public static boolean relockOnReload = false;
	public static boolean compassSharing = false;
	public static boolean visitsSharing = false;
    private static final String OPAC_SHARING_COMMENT = "Temporarily share saved personal structure unlocks among current OPAC party members when Open Parties and Claims is installed. Requires a restart to change.";
    public static boolean opacPartySharing = true;
	public static boolean displayCoordinates = true;
	public static boolean displayDistance = true;
	public static boolean showNetherEquivalent = false;
	public static boolean useClassicCompass = false;
	public static List<String> structureWhitelist = List.of();
	public static List<String> structureBlacklist = new ArrayList<>();
	public static List<String> hiddenStructures = new ArrayList<>();

	public static boolean displayWithChatOpen;
	public static boolean pauseGameWhenOpen;
	public static boolean creativeUnlocksAll;
	public static boolean showTeleportOnlyInCreative;
	public static boolean clearFiltersOnClose;
	public static boolean clearSearchBarOnClose;
	public static boolean closeOnFound;
	public static boolean checkedFiltersFirst;
	public static boolean adjustRowsToFitView;
	public static boolean showOnlyMatchingFilterOptions;
	public static boolean hideSearchCost;
	public static boolean matchSearchWordsInAnyOrder;
	public static int overlayLineOffset;
	public static OverlaySide overlaySide;
	public static List<String> sortOrder;

	public enum ClientSetting {
		OVERLAY_SIDE(() -> overlaySide = OverlaySide.LEFT),
		OVERLAY_LINE_OFFSET(() -> overlayLineOffset = 0),
		DISPLAY_WITH_CHAT_OPEN(() -> displayWithChatOpen = true),
		ADJUST_ROWS_TO_FIT_VIEW(() -> adjustRowsToFitView = true),
		CHECKED_FILTERS_FIRST(() -> checkedFiltersFirst = false),
		HIDE_SEARCH_COST(() -> hideSearchCost = false),
		CLOSE_ON_FOUND(() -> closeOnFound = false),
		CLEAR_SEARCH_BAR_ON_CLOSE(() -> clearSearchBarOnClose = false),
		CLEAR_FILTERS_ON_CLOSE(() -> clearFiltersOnClose = false),
		MATCH_SEARCH_WORDS_IN_ANY_ORDER(() -> matchSearchWordsInAnyOrder = false),
		SHOW_ONLY_MATCHING_FILTER_OPTIONS(() -> showOnlyMatchingFilterOptions = false),
		CREATIVE_UNLOCKS_ALL(() -> creativeUnlocksAll = true),
		SHOW_TELEPORT_ONLY_IN_CREATIVE(() -> showTeleportOnlyInCreative = false),
		PAUSE_GAME_WHEN_OPEN(() -> pauseGameWhenOpen = false),
		SORT_ORDER(() -> sortOrder = defaultSortOrder());

		private final Runnable reset;

		ClientSetting(Runnable reset) {
			this.reset = reset;
		}

		public void restoreDefault() {
			reset.run();
		}
	}

	static {
		restoreClientDefaults();
	}

	public static void restoreClientDefaults() {
		for (ClientSetting setting : ClientSetting.values()) setting.restoreDefault();
	}

	private static List<String> defaultSortOrder() {
		return new ArrayList<>(Arrays.asList("NAME", "SOURCE", "DIMENSION", "SET"));
	}

	private static List<String> normalizeSortOrder(List<String> savedOrder) {
		if (savedOrder == null || savedOrder.size() != 4 || !new HashSet<>(savedOrder).equals(new HashSet<>(defaultSortOrder()))) {
			return defaultSortOrder();
		}
		return new ArrayList<>(savedOrder);
	}

	public static void load() {
		try {
			archiveLegacyConfig();
			ensureServerDefaults();
			loadServerSettings(directory().resolve(SERVER_SETTINGS), null);
			boolean client = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
			if (client) {
				Path clientSettings = directory().resolve(CLIENT_SETTINGS);
				restoreClientDefaults();
				if (Files.exists(clientSettings)) loadClient(parse(clientSettings));
				writeClient(clientSettings);
				hiddenStructures = strings(readOrCreate(HIDDEN_STRUCTURES), "hiddenStructures", hiddenStructures);
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not load Explorer's Compass configuration", e);
		}
	}

	private static void archiveLegacyConfig() throws IOException {
		Path configDirectory = directory().getParent();
		Path legacy = configDirectory.resolve("explorerscompass.json");
		Path archive = configDirectory.resolve("explorerscompass-old.json");
		if (Files.isRegularFile(legacy) && Files.notExists(archive)) {
			Files.move(legacy, archive);
			ExplorersCompass.LOGGER.info("Renamed explorerscompass.json to explorerscompass-old.json");
		}
	}

	private static void ensureServerDefaults() throws IOException {
		Path defaults = directory();
		Files.createDirectories(defaults.resolve("server"));
		if (Files.notExists(defaults.resolve(SERVER_SETTINGS))) {
			try {
				readOrCreate(SERVER_SETTINGS);
			} catch (IOException ignored) {
				// The settings loader handles unavailable files independently.
			}
		}
		for (String name : List.of(BLACKLIST, WHITELIST)) {
			if (Files.notExists(defaults.resolve(name))) {
				try {
					readOrCreate(name);
				} catch (IOException ignored) {
					// The structure list loader handles unavailable files independently.
				}
			}
		}
		Path rules = defaults.resolve(UNLOCKING_RULES);
		if (Files.notExists(rules)) Files.writeString(rules, DEFAULT_UNLOCKING_RULES);
		Path costs = defaults.resolve(SEARCH_COSTS);
		if (Files.notExists(costs)) Files.writeString(costs, SearchCosts.DEFAULT_JSON);
	}

	public static Path serverConfigFile(net.minecraft.server.MinecraftServer server, String name) {
		Path override = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
				.resolve(ExplorersCompass.MODID).resolve(Path.of(name).getFileName());
		return Files.exists(override) ? override : directory().resolve(name);
	}

	private static Section startupSettings;
	private static StructureListLoad startupLists;

	public static void loadServer(net.minecraft.server.MinecraftServer server) {
		StartupConfigReport.clear();
		try {
			ensureServerDefaults();
			startupSettings = loadServerSettings(directory().resolve(SERVER_SETTINGS), serverConfigFile(server, SERVER_SETTINGS));
			startupLists = loadStructureLists(serverConfigFile(server, WHITELIST), serverConfigFile(server, BLACKLIST),
					server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds());
		} catch (IOException e) {
			throw new RuntimeException("Could not load Explorer's Compass server configuration", e);
		}
	}

	public static void logStartup(Set<Identifier> available, Section rules, Section costs) {
		int loaded = (int) available.stream().filter(id -> !StructureUtils.structureIdIsExcluded(id)).count();
		StartupConfigReport.publish(available.size(), startupLists.override(), startupLists.summary(available.size(), loaded),
				rules, costs, startupSettings);
	}

	static Section loadServerSettings(Path instanceFile, Path worldFile) {
		List<Diagnostic> failures = new ArrayList<>();
		Path selectedFile = worldFile == null ? instanceFile : worldFile;
		ServerToml document = tryReadServerSettings(selectedFile, !selectedFile.equals(instanceFile), failures);
		if (document == null && !selectedFile.equals(instanceFile)) {
			selectedFile = instanceFile;
			document = tryReadServerSettings(selectedFile, false, failures);
		}
		if (document == null) {
			loadCommon(new ServerToml(TomlFormat.newConfig(LinkedHashMap::new), List.of()), "settings.toml");
			return new Section("Could not load settings.toml. All settings fell back to built-in defaults",
					Severity.WARNING, failures);
		}
		CommentedConfig common = document.config();
		var settings = loadCommon(document, selectedFile.getFileName().toString());
		List<Diagnostic> diagnostics = new ArrayList<>(failures);
		diagnostics.addAll(settings.diagnostics);
		String count = settings.loaded + "/" + settings.total;
		String summary = settings.valid ? "Successfully loaded " + count + " settings"
				: count + " settings loaded successfully";
		if (!failures.isEmpty()) summary = "Could not load world settings.toml. Using instance settings. " + summary;
		var result = new Section(summary, diagnostics.isEmpty() ? null : Severity.WARNING, diagnostics);
		if (!settings.valid) return result;

		if (common.contains("compass.compassDurability") || common.contains("search.defaultXpCost")
                || common.contains("search.allowSearchWithoutEnoughXp") || common.get("permissions.allowTeleport") != null || common.get("unlocking.compassSharing") == null
				|| !isAtPosition(common, "compass", 0) || !isAtPosition(common, "unlocking", 2)
				|| !isAtPosition(common.get("unlocking"), "visitsSharing", 2)
				|| common.get("unlocking.opacPartySharing") == null || common.get(SEARCH_RADIUS) == null
                    || common.get(SEARCH_SAVED_STRUCTURES) == null || common.get("compass.showNetherEquivalent") == null
                    || common.get("compass.useClassicCompass") == null
                    || !RELOCK_COMMENT.equals(String.valueOf(common.getComment("unlocking.relockOnReload")).strip())
                    || !OPAC_SHARING_COMMENT.equals(String.valueOf(common.getComment("unlocking.opacPartySharing")).strip())) {
			common.remove("permissions.allowTeleport");
			common.remove("compass.compassDurability");
			common.remove("search.defaultXpCost");
			common.remove("search.allowSearchWithoutEnoughXp");
			writeCommon(common);
			try {
				write(selectedFile, common);
			} catch (IOException ignored) {
				// Settings are already loaded even if the file cannot be updated.
			}
		}
		return result;
	}

	private static ServerToml tryReadServerSettings(Path path, boolean world, List<Diagnostic> failures) {
		try {
			return ServerToml.read(path);
		} catch (IOException | IllegalArgumentException error) {
			String scope = world ? "world" : "instance";
			failures.add(new Diagnostic(Severity.ERROR,
					"Could not load " + scope + " " + path.getFileName() + ": " + StartupConfigReport.cause(error)));
			return null;
		}
	}

	private static Diagnostic fileFailure(Path file, Exception error) {
		return new Diagnostic(Severity.ERROR, "Could not load " + file.getFileName() + ": " + StartupConfigReport.cause(error));
	}

	record StructureListLoad(String filename, boolean valid, Diagnostic override,
							 List<Diagnostic> diagnostics) {
		Section summary(int available, int loaded) {
			String message;
			if (!valid) message = "Could not load " + filename + ". Loaded all " + available + " structures";
			else if (filename.equals("whitelist.toml")) message = "Whitelisted and loaded " + loaded + " structures";
			else message = "Blacklisted " + (available - loaded) + " structures and loaded " + loaded + " structures";
			return new Section(message, valid ? null : Severity.ERROR, diagnostics);
		}
	}

	static StructureListLoad loadStructureLists(Path whitelistFile, Path blacklistFile, Set<Identifier> registeredIds) {
		var whitelist = readStructureList(whitelistFile, WHITELIST_KEY, registeredIds);
		structureWhitelist = List.of();
		structureBlacklist = List.of();
		if (whitelist.selectors() == null || !whitelist.selectors().isEmpty()) {
			var result = whitelist.result();
			if (result.valid()) structureWhitelist = whitelist.selectors();
			if (!hasBlacklistEntries(blacklistFile)) return result;

			var warning = new Diagnostic(Severity.WARNING, "whitelist.toml overrides the nonempty blacklist.toml");
			var diagnostics = new ArrayList<>(result.diagnostics());
			diagnostics.add(0, warning);
			return new StructureListLoad(result.filename(), result.valid(), warning, List.copyOf(diagnostics));
		}
		var blacklist = readStructureList(blacklistFile, BLACKLIST_KEY, registeredIds);
		if (blacklist.selectors() != null) structureBlacklist = blacklist.selectors();
		return blacklist.result();
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
			var failure = List.of(fileFailure(file, error));
			return new StructureList(null, new StructureListLoad(filename, false, null, failure));
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
				String normalized = selector.contains(":") ? selector : "minecraft:" + selector;
				Identifier id = Identifier.tryParse(normalized.replace('*', 'a'));
				if (id == null || id.getNamespace().isEmpty() || id.getPath().isEmpty()
						|| normalized.startsWith(":") || normalized.indexOf(':') != normalized.lastIndexOf(':')) {
					diagnostics.add(new Diagnostic(Severity.ERROR, filename + ": Invalid structure selector " + detail));
					continue;
				}
				var pattern = StructureUtils.structureIdPattern(selector);
				var matches = registeredIds.stream().filter(candidate -> pattern.matcher(candidate.toString()).matches()).sorted().toList();
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
		return new StructureList(valid ? List.copyOf(selectors) : null,
				new StructureListLoad(filename, valid, null, diagnostics));
	}

	private static String quotedDiagnosticValue(Object value) {
		return new com.google.gson.JsonPrimitive(String.valueOf(value)).toString();
	}

	private static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExplorersCompass.MODID);
	}

	private static CommentedConfig readOrCreate(String name) throws IOException {
		Path path = directory().resolve(name);
		if (Files.exists(path)) return parse(path);
		CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
		switch (name) {
			case SERVER_SETTINGS -> {
				loadCommon(new ServerToml(config, List.of()), "settings.toml");
				writeCommon(config);
			}
			case WHITELIST -> writeCommonWhitelist(config);
			case BLACKLIST -> writeCommonBlacklist(config);
			case HIDDEN_STRUCTURES -> writeHiddenStructures(config);
			default -> throw new IllegalArgumentException(name);
		}
		write(path, config);
		return config;
	}

	private static CommentedConfig parse(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
			new TomlParser().parse(reader, config, ParsingMode.MERGE);
			return config;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Invalid TOML in " + path, e);
		}
	}

	private static void write(Path path, CommentedConfig config) throws IOException {
		Files.createDirectories(path.getParent());
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			new TomlWriter().write(config, writer);
		}
	}

	private static boolean bool(CommentedConfig config, String key, boolean fallback) {
		Object value = config.get(key);
		if (value == null) return fallback;
		if (value instanceof Boolean result) return result;
		throw bad(key, "boolean");
	}

	private static int number(CommentedConfig config, String key, int fallback, int minimum) {
		Object value = config.get(key);
		if (value == null) return fallback;
		if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
				|| number.doubleValue() != number.longValue()
				|| number.longValue() < minimum || number.longValue() > Integer.MAX_VALUE) throw bad(key, "integer >= " + minimum);
		return number.intValue();
	}

	private static List<String> strings(CommentedConfig config, String key, List<String> fallback) {
		Object value = config.get(key);
		if (value == null) return new ArrayList<>(fallback);
		if (!(value instanceof List<?> list)) throw bad(key, "string array");
		List<String> result = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof String entry)) throw bad(key, "string array");
			result.add(entry);
		}
		return result;
	}

	private static IllegalArgumentException bad(String key, String expected) {
		return new IllegalArgumentException("Invalid Explorer's Compass setting '" + key + "'; expected " + expected);
	}

	private static ServerSettingsReader loadCommon(ServerToml document, String filename) {
		var settings = new ServerSettingsReader(document, filename);
		maxSearchRadius = settings.searchRadius();
		findOldStructures = settings.bool(SEARCH_SAVED_STRUCTURES, false);
		compassSharing = settings.bool("unlocking.compassSharing", false);
		visitsSharing = settings.bool("unlocking.visitsSharing", false);
		opacPartySharing = settings.bool("unlocking.opacPartySharing", true);
		allowExcludeVisited = settings.bool("search.allowExcludeVisited", true);
		relockOnReload = settings.bool("unlocking.relockOnReload", false);
		displayCoordinates = settings.bool("compass.displayCoordinates", true);
		displayDistance = settings.bool("compass.displayDistance", true);
		showNetherEquivalent = settings.bool("compass.showNetherEquivalent", false);
		useClassicCompass = settings.bool("compass.useClassicCompass", false);
		return settings;
	}

	private static final class ServerSettingsReader {
		private final ServerToml document;
		private final String filename;
		private final List<Diagnostic> diagnostics = new ArrayList<>();
		private boolean valid = true;
		private int loaded, total;

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
				throw bad(section, "a table");
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
				value(SEARCH_RADIUS);
				int radius = number(document.config(), SEARCH_RADIUS, DEFAULT_SEARCH_RADIUS, 1);
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

	static boolean loadClient(CommentedConfig config) {
		displayWithChatOpen = bool(config, clientPath(config, "hud.displayWithChatOpen", "overlay.displayWithChatOpen"), displayWithChatOpen);
		String offsetPath = clientPath(config, "hud.verticalOffset", "overlay.overlayLineOffset");
		overlayLineOffset = Math.max(0, number(config, offsetPath, overlayLineOffset, Integer.MIN_VALUE));
		String sidePath = clientPath(config, "hud.hudSide", "overlay.overlaySide");
		Object side = config.get(sidePath);
		if (side != null) {
			try {
				overlaySide = OverlaySide.valueOf((String) side);
			} catch (ClassCastException | IllegalArgumentException e) {
				throw bad(sidePath, "LEFT or RIGHT");
			}
		}
		adjustRowsToFitView = bool(config, "view.adjustRowsToFitView", adjustRowsToFitView);
		checkedFiltersFirst = bool(config, clientPath(config, "view.checkedFiltersOnTop", "view.checkedFiltersFirst"), checkedFiltersFirst);
		hideSearchCost = bool(config, clientPath(config, "view.hideSearchCosts", "behavior.hideSearchCost"), hideSearchCost);
		closeOnFound = bool(config, "behavior.closeOnFound", closeOnFound);
		clearSearchBarOnClose = bool(config, "behavior.clearSearchBarOnClose", clearSearchBarOnClose);
		clearFiltersOnClose = bool(config, "behavior.clearFiltersOnClose", clearFiltersOnClose);
		matchSearchWordsInAnyOrder = bool(config, clientPath(config, "behavior.flexibleSearch", "behavior.matchSearchWordsInAnyOrder"), matchSearchWordsInAnyOrder);
		showOnlyMatchingFilterOptions = bool(config, "behavior.showOnlyMatchingFilterOptions", showOnlyMatchingFilterOptions);
		creativeUnlocksAll = bool(config, clientPath(config, "creativeAndSingleplayer.creativeUnlocksAll",
				"singleplayerAndCreative.creativeUnlocksAll"), creativeUnlocksAll);
		showTeleportOnlyInCreative = bool(config, clientPath(config, "creativeAndSingleplayer.showTeleportOnlyInCreative",
				"singleplayerAndCreative.showTeleportOnlyInCreative"), showTeleportOnlyInCreative);
		String legacyPausePath = clientPath(config, "singleplayerAndCreative.pauseGameWhenOpen", "behavior.pauseGameWhenOpen");
		pauseGameWhenOpen = bool(config, clientPath(config, "creativeAndSingleplayer.pauseGameWhileOpen", legacyPausePath), pauseGameWhenOpen);
		List<String> order = strings(config, "sorting.sortOrder", sortOrder);
		boolean migratedSortOrder = Collections.replaceAll(order, "GROUP", "SET");
		if (!normalizeSortOrder(order).equals(order)) throw bad("sorting.sortOrder", "permutation of NAME, SOURCE, DIMENSION, SET");
		sortOrder = order;
		return migratedSortOrder;
	}

	private static String clientPath(CommentedConfig config, String key, String legacyKey) {
		return config.get(key) == null ? legacyKey : key;
	}

	private static void put(CommentedConfig config, String key, Object value, String comment) {
		config.set(key, value);
		if (config.getComment(key) == null) config.setComment(key, comment);
	}

	private static boolean isAtPosition(CommentedConfig config, String key, int index) {
		return config != null && config.valueMap().keySet().stream().skip(index).findFirst().filter(key::equals).isPresent();
	}

	private static void moveToPosition(CommentedConfig config, String key, int index) {
		if (isAtPosition(config, key, index)) return;
		var values = config.valueMap();
		Object value = values.remove(key);
		var laterKeys = values.keySet().stream().skip(index).toList();
		var laterValues = new LinkedHashMap<String, Object>();
		for (String laterKey : laterKeys) laterValues.put(laterKey, values.remove(laterKey));
		values.put(key, value);
		values.putAll(laterValues);
	}

	private static void writeCommon(CommentedConfig config) {
        config.set("unlocking.opacPartySharing", opacPartySharing);
        config.setComment("unlocking.opacPartySharing", OPAC_SHARING_COMMENT);
		put(config, "unlocking.compassSharing", compassSharing, "Preserve located targets and unlock structures that the previous compass holder had unlocked when a different player gets hold of the compass.");
		put(config, "unlocking.visitsSharing", visitsSharing, "Share temporary visit history in OPAC parties and copy saved visits on compass transfer when the respective sharing option is enabled.");
		put(config, "search.allowExcludeVisited", allowExcludeVisited, "Allow players to exclude visited structure instances from searches. Visits are always recorded.");
		put(config, SEARCH_SAVED_STRUCTURES, findOldStructures,
                "Find existing structures saved under older world generation rules. Searches can be much slower in explored areas. Usually only needed if world or structure generation changed significantly through mods or datapacks after those chunks were generated.");
		put(config, SEARCH_RADIUS, maxSearchRadius, "Maximum horizontal search radius in blocks (1 to 50000). Requires a restart to change.");
		put(config, "unlocking.relockOnReload", relockOnReload, RELOCK_COMMENT);
		put(config, "compass.displayCoordinates", displayCoordinates, "Show precise coordinates for located structures.");
		put(config, "compass.displayDistance", displayDistance, "Show distance to located structures.");
		put(config, "compass.showNetherEquivalent", showNetherEquivalent, "Point to equivalent coordinates when tracking a structure across the Overworld and Nether.");
		put(config, "compass.useClassicCompass", useClassicCompass, "Use the original Explorer’s Compass textures and crafting recipe. Reopen the world or restart the server to apply.");
        config.remove("search.maxSamples"); // Retired: searches are bounded by radius.
		// Keep the established order of all other sections and settings.
		moveToPosition(config, "compass", 0);
		moveToPosition(config, "unlocking", 2);
		moveToPosition(config.get("unlocking"), "visitsSharing", 2);
	}

	static String serializeClientSettings() {
		CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
		put(config, "hud.hudSide", overlaySide.name(), " HUD side: LEFT or RIGHT.");
		put(config, "hud.verticalOffset", overlayLineOffset, " Vertical offset of the HUD overlay.");
		put(config, "hud.displayWithChatOpen", displayWithChatOpen, " Show compass information while chat is open.");

		put(config, "view.adjustRowsToFitView", adjustRowsToFitView, " Adjust row spacing so list entries fit the view.");
		put(config, "view.checkedFiltersOnTop", checkedFiltersFirst,
				" Show checked filter options separately above unchecked ones.");
		put(config, "view.hideSearchCosts", hideSearchCost,
				" Hide all structure search costs from the list. Reasonable when all structures have the same search cost.");

		put(config, "behavior.closeOnFound", closeOnFound,
				" Immediately close the compass screen when the compass finds a structure.");
		put(config, "behavior.clearSearchBarOnClose", clearSearchBarOnClose,
				" Clear the search bar when the compass screen closes.");
		put(config, "behavior.clearFiltersOnClose", clearFiltersOnClose, " Clear filters when the compass screen closes.");
		put(config, "behavior.flexibleSearch", matchSearchWordsInAnyOrder,
				" Match every typed word fragment contained in a structure name, in any order.\n Example: \"vil pla\" can match \"Village Plains\".");
		put(config, "behavior.showOnlyMatchingFilterOptions", showOnlyMatchingFilterOptions,
				" Hide filter options which would produce an empty list if checked.\n Example: Checking \"The Nether\" would hide the \"Villages\" set.");

		put(config, "creativeAndSingleplayer.creativeUnlocksAll", creativeUnlocksAll,
				" Unlock all structures while in Creative mode. Changing the mode back relocks the right structures.");
		put(config, "creativeAndSingleplayer.showTeleportOnlyInCreative", showTeleportOnlyInCreative,
				" Hide the Teleport button in any game mode other than Creative.");
		put(config, "creativeAndSingleplayer.pauseGameWhileOpen", pauseGameWhenOpen,
				" Pause your Singleplayer world while the compass screen is open.");

		put(config, "sorting.sortOrder", sortOrder, " Order of the four structure sorting keys.");
		return new TomlWriter().writeToString(config);
	}

	private static void writeClient(Path path) throws IOException {
		String settings = serializeClientSettings();
		if (Files.exists(path) && Files.readString(path).equals(settings)) return;
		Files.createDirectories(path.getParent());
		Files.writeString(path, settings);
	}

	private static void writeCommonWhitelist(CommentedConfig config) {
		put(config, WHITELIST_KEY, List.of(), " Structures allowed to be listed by the server.\n" + STRUCTURE_LIST_COMMENTS);
	}

	private static void writeCommonBlacklist(CommentedConfig config) {
		put(config, BLACKLIST_KEY, List.of(), " Structures excluded from the compass list.\n" + STRUCTURE_LIST_COMMENTS);
	}

	private static void writeHiddenStructures(CommentedConfig config) {
		put(config, "hiddenStructures", hiddenStructures, " Structures hidden from this client's compass list.");
	}

	private static void saveFile(String name, java.util.function.Consumer<CommentedConfig> update) {
		try {
			Path path = directory().resolve(name);
			CommentedConfig config = Files.exists(path) ? parse(path) : TomlFormat.newConfig(LinkedHashMap::new);
			update.accept(config);
			write(path, config);
		} catch (IOException e) {
			throw new RuntimeException("Could not save Explorer's Compass " + name, e);
		}
	}

	public static void saveClient() {
		try {
			writeClient(directory().resolve(CLIENT_SETTINGS));
		} catch (IOException e) {
			throw new RuntimeException("Could not save Explorer's Compass " + CLIENT_SETTINGS, e);
		}
	}

	public static void saveHiddenStructures() {
		saveFile(HIDDEN_STRUCTURES, ExplorersCompassConfig::writeHiddenStructures);
	}
}
