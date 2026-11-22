package io.github.enriquemetner.explorerscompass.config;

import static io.github.enriquemetner.explorerscompass.config.ServerToml.quotedDiagnosticValue;
import static io.github.enriquemetner.explorerscompass.config.StructureListLoader.BLACKLIST_KEY;
import static io.github.enriquemetner.explorerscompass.config.StructureListLoader.WHITELIST_KEY;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.StartupConfigReport.Section;
import io.github.enriquemetner.explorerscompass.config.StructureListLoader.StructureListLoad;
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
	private static final String SERVER_SETTINGS = "server/settings.toml";
	private static final String CLIENT_SETTINGS = "client/settings.toml";
	private static final String WHITELIST = "server/whitelist.toml";
	private static final String STRUCTURE_LIST_COMMENTS =
			" Supports the * wildcard. Omitted namespaces default to minecraft:.\n"
			+ " If the whitelist is modified, the blacklist is ignored.";
	private static final String BLACKLIST = "server/blacklist.toml";
	private static final String HIDDEN_STRUCTURES = "client/hidden_structures.toml";
	private static final String HIDDEN_STRUCTURES_COMMENT = " Structures hidden from this client's compass list.";
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
			ensureServerDefaults(null);
			loadServerSettings(configDirectory().resolve(SERVER_SETTINGS), null);
			boolean client = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
			if (client) {
				Path clientSettings = configDirectory().resolve(CLIENT_SETTINGS);
				restoreClientDefaults();
				loadClient(readClientConfig(clientSettings, CLIENT_SETTINGS));
				writeClient(clientSettings);
				Path hiddenFile = configDirectory().resolve(HIDDEN_STRUCTURES);
				hiddenStructures = strings(readClientConfig(hiddenFile, HIDDEN_STRUCTURES), "hiddenStructures", List.of());
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not load Explorer's Compass configuration", e);
		}
	}

	private static void archiveLegacyConfig() throws IOException {
		Path instanceConfig = configDirectory().getParent();
		Path legacy = instanceConfig.resolve("explorerscompass.json");
		Path archive = instanceConfig.resolve("explorerscompass-old.json");
		if (Files.isRegularFile(legacy) && Files.notExists(archive)) {
			Files.move(legacy, archive);
			ExplorersCompass.LOGGER.info("Renamed explorerscompass.json to explorerscompass-old.json");
		}
	}

	private static final List<String> SERVER_CONFIGS = List.of(SERVER_SETTINGS, WHITELIST, BLACKLIST, UNLOCKING_RULES, SEARCH_COSTS);

	private static void ensureServerDefaults(net.minecraft.server.MinecraftServer server) {
		for (String name : SERVER_CONFIGS) {
			Path instance = configDirectory().resolve(name);
			prepareServerDefaults(instance, name, false);
			if (server != null) {
				Path selected = serverConfigFile(server, name);
				if (!selected.equals(instance)) prepareServerDefaults(selected, name, true);
			}
		}
	}

	private static void prepareServerDefaults(Path path, String name, boolean worldOverride) {
		try {
			prepareDefaults(path, name, worldOverride);
		} catch (IOException ignored) {
			// Each server loader reports failures and applies its fallback independently.
		}
	}

	public static Path serverConfigFile(net.minecraft.server.MinecraftServer server, String name) {
		Path override = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
				.resolve(ExplorersCompass.MODID).resolve(Path.of(name).getFileName());
		return Files.exists(override) ? override : configDirectory().resolve(name);
	}

	private static Section startupSettings;
	private static StructureListLoad startupLists;

	public static void loadServer(net.minecraft.server.MinecraftServer server) {
		StartupConfigReport.clear();
		ensureServerDefaults(server);
		startupSettings = loadServerSettings(configDirectory().resolve(SERVER_SETTINGS), serverConfigFile(server, SERVER_SETTINGS));
		startupLists = loadStructureLists(serverConfigFile(server, WHITELIST), serverConfigFile(server, BLACKLIST),
				server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds());
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
					Severity.ERROR, failures);
		}
		CommentedConfig common = document.config();
		var settings = loadCommon(document, selectedFile.getFileName().toString());
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
		String before = writer.writeToString(common);
		writeCommon(common);
		if (!before.equals(writer.writeToString(common))) {
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

	static StructureListLoad loadStructureLists(Path whitelistFile, Path blacklistFile, Set<Identifier> registeredIds) {
		var result = StructureListLoader.load(whitelistFile, blacklistFile, registeredIds);
		structureWhitelist = result.whitelist();
		structureBlacklist = result.blacklist();
		return result.report();
	}

	public static Path configDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExplorersCompass.MODID);
	}

	static void prepareDefaults(Path path, String name, boolean worldOverride) throws IOException {
		boolean missing = Files.notExists(path);
		if (!missing) {
			String source = Files.readString(path);
			boolean empty = source.isBlank() || name.endsWith(".toml")
					&& source.lines().allMatch(line -> line.isBlank() || line.stripLeading().startsWith("#"));
			if (!empty) return;
		}
		String defaults = switch (name) {
			case UNLOCKING_RULES -> DEFAULT_UNLOCKING_RULES;
			case SEARCH_COSTS -> SearchCosts.DEFAULT_JSON;
			case CLIENT_SETTINGS -> serializeClientSettings();
			default -> defaultToml(name);
		};
		Files.createDirectories(path.toAbsolutePath().getParent());
		if (missing) Files.writeString(path, defaults, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		else Files.writeString(path, defaults);
		StartupConfigReport.prepared(path, worldOverride ? "world override " + name : name, missing, name.startsWith("client/"));
	}

	private static String defaultToml(String name) {
		CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
		switch (name) {
			case SERVER_SETTINGS -> {
				loadCommon(new ServerToml(config, List.of()), "settings.toml");
				writeCommon(config);
			}
			case WHITELIST -> writeCommonWhitelist(config);
			case BLACKLIST -> writeCommonBlacklist(config);
			case HIDDEN_STRUCTURES -> put(config, "hiddenStructures", List.of(), HIDDEN_STRUCTURES_COMMENT);
			default -> throw new IllegalArgumentException(name);
		}
		return new TomlWriter().writeToString(config);
	}

	private static CommentedConfig readClientConfig(Path path, String name) throws IOException {
		try {
			prepareDefaults(path, name, false);
			return parse(path);
		} catch (IOException | IllegalArgumentException error) {
			ExplorersCompass.LOGGER.error("Could not load {}", name, error);
			throw error;
		}
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
		compassSharing = settings.progressionBool("compassSharing", false);
		visitsSharing = settings.progressionBool("visitsSharing", false);
		opacPartySharing = settings.progressionBool("opacPartySharing", true);
		allowExcludeVisited = settings.bool("search.allowExcludeVisited", true);
		relockOnReload = settings.progressionBool("relockOnReload", false);
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

		CommentedConfig compass = config.get("compass");
		if (compass == null) {
			compass = config.createSubConfig();
			config.set("compass", compass);
		}
		moveToPosition(config, "compass", 0);
		writeCommonSetting(compass, "displayCoordinates", displayCoordinates, 0,
				"Show the coordinates of located structures.");
		writeCommonSetting(compass, "displayDistance", displayDistance, 1,
				"Show the distance to located structures.");
		writeCommonSetting(compass, "showNetherEquivalent", showNetherEquivalent, 2,
				"Point to equivalent coordinates when tracking a structure across the Overworld and Nether.");
		writeCommonSetting(compass, "useClassicCompass", useClassicCompass, 3,
				"Use the original Explorer’s Compass texture and crafting recipe.");

		CommentedConfig search = config.get("search");
		if (search == null) {
			search = config.createSubConfig();
			config.set("search", search);
		}
		moveToPosition(config, "search", 1);
		writeCommonSetting(search, "allowExcludeVisited", allowExcludeVisited, 0,
				"Allow players to exclude visited structure instances from searches. Visits are always recorded.");
		writeCommonSetting(search, "findOldStructures", findOldStructures, 1,
				"Find existing structures saved under older world generation rules. This has highly variable impact on search duration in explored areas." + "\n"
				+ "Usually only needed if world or structure generation changed significantly through mods or datapacks AFTER those chunks were generated.");
		writeCommonSetting(search, "maxSearchRadius", maxSearchRadius, 2,
				"Maximum search radius in blocks (Max. 50000).");

		CommentedConfig progression = config.get("progression");
		if (progression == null) {
			progression = config.createSubConfig();
			config.set("progression", progression);
		}
		moveToPosition(config, "progression", 2);
		writeCommonSetting(progression, "relockOnReload", relockOnReload, 0,
				"Relock structures at world startup when their unlock requirements change or a prerequisite is no longer met.");
		writeCommonSetting(progression, "compassSharing", compassSharing, 1,
				"Unlock structures which a previous compass holder had unlocked after getting a hold of their compass.");
		writeCommonSetting(progression, "opacPartySharing", opacPartySharing, 2,
				"Share structure unlocks among OPAC party members (not permanent). Requires Open Parties and Claims mod.");
		writeCommonSetting(progression, "visitsSharing", visitsSharing, 3,
				"Include visit history when sharing is enabled for OPAC parties or compass transfers");
	}

	private static void writeCommonSetting(CommentedConfig section, String key, Object value, int index, String comment) {
		section.set(key, value);
		section.setComment(key, comment);
		moveToPosition(section, key, index);
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
		put(config, "hiddenStructures", hiddenStructures, HIDDEN_STRUCTURES_COMMENT);
	}

	private static void saveFile(String name, java.util.function.Consumer<CommentedConfig> update) {
		try {
			Path path = configDirectory().resolve(name);
			CommentedConfig config = Files.exists(path) ? parse(path) : TomlFormat.newConfig(LinkedHashMap::new);
			update.accept(config);
			write(path, config);
		} catch (IOException e) {
			throw new RuntimeException("Could not save Explorer's Compass " + name, e);
		}
	}

	public static void saveClient() {
		try {
			writeClient(configDirectory().resolve(CLIENT_SETTINGS));
		} catch (IOException e) {
			throw new RuntimeException("Could not save Explorer's Compass " + CLIENT_SETTINGS, e);
		}
	}

	public static void saveHiddenStructures() {
		saveFile(HIDDEN_STRUCTURES, ExplorersCompassConfig::writeHiddenStructures);
	}
}
