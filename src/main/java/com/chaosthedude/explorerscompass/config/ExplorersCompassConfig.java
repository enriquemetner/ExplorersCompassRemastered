package com.chaosthedude.explorerscompass.config;

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

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.OverlaySide;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public class ExplorersCompassConfig {
	public static final String UNLOCKING_RULES = "server/unlocking_rules.json";
	public static final String DEFAULT_UNLOCKING_RULES = "{\n  \"default\": \"unlocked\",\n  \"rules\": {}\n}\n";
	private static final String SERVER_SETTINGS = "server/settings.toml";
	private static final String CLIENT_SETTINGS = "client/settings.toml";
	private static final String WHITELIST = "server/whitelist.toml";
	private static final String WHITELIST_PRECEDENCE = "When the structure whitelist contains any entries, the blacklist is ignored.";
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
	public static int compassDurability = 0;
	public static int defaultXpCost = 0;
	public static boolean allowSearchWithoutEnoughXp = false;
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
	public static boolean allowIdSearches;
	public static boolean showOnlyMatchingFilterOptions;
	public static boolean matchSearchWordsInAnyOrder;
	public static int overlayLineOffset;
	public static OverlaySide overlaySide;
	public static List<String> sortOrder;

	public enum ClientSetting {
		OVERLAY_SIDE(() -> overlaySide = OverlaySide.LEFT),
		OVERLAY_LINE_OFFSET(() -> overlayLineOffset = 0),
		DISPLAY_WITH_CHAT_OPEN(() -> displayWithChatOpen = true),
		CHECKED_FILTERS_FIRST(() -> checkedFiltersFirst = true),
		ADJUST_ROWS_TO_FIT_VIEW(() -> adjustRowsToFitView = true),
		CLOSE_ON_FOUND(() -> closeOnFound = true),
		CLEAR_FILTERS_ON_CLOSE(() -> clearFiltersOnClose = true),
		CLEAR_SEARCH_BAR_ON_CLOSE(() -> clearSearchBarOnClose = true),
		ALLOW_ID_SEARCHES(() -> allowIdSearches = false),
		SHOW_ONLY_MATCHING_FILTER_OPTIONS(() -> showOnlyMatchingFilterOptions = false),
		MATCH_SEARCH_WORDS_IN_ANY_ORDER(() -> matchSearchWordsInAnyOrder = false),
		PAUSE_GAME_WHEN_OPEN(() -> pauseGameWhenOpen = false),
		CREATIVE_UNLOCKS_ALL(() -> creativeUnlocksAll = false),
		SHOW_TELEPORT_ONLY_IN_CREATIVE(() -> showTeleportOnlyInCreative = true),
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
			loadServerFiles(directory().resolve(SERVER_SETTINGS), directory().resolve(BLACKLIST), directory().resolve(WHITELIST));
			boolean client = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
			if (client) {
				Files.createDirectories(directory().resolve("client"));
				CommentedConfig clientConfig = readOrCreate(CLIENT_SETTINGS);
				boolean migrateClient = clientConfig.get("behavior.searchTechnicalNames") != null
						|| clientConfig.get("view.showTechnicalNames") != null
						|| clientConfig.get("behavior.allowIdSearches") == null
						|| clientConfig.get("behavior.showOnlyMatchingFilterOptions") == null
						|| clientConfig.get("behavior.matchSearchWordsInAnyOrder") == null
						|| !new ArrayList<>(clientConfig.valueMap().keySet()).get(clientConfig.valueMap().size() - 1).equals("sorting")
						|| clientConfig.get("behavior.pauseGameWhenOpen") != null
						|| clientConfig.get("singleplayerAndCreative.pauseGameWhenOpen") == null
						|| clientConfig.get("singleplayerAndCreative.creativeUnlocksAll") == null
						|| clientConfig.get("singleplayerAndCreative.showTeleportOnlyInCreative") == null;
				if (loadClient(clientConfig) || migrateClient) {
					clientConfig.remove("behavior.pauseGameWhenOpen");
					writeClient(clientConfig);
					write(directory().resolve(CLIENT_SETTINGS), clientConfig);
				}
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
		for (String name : List.of(SERVER_SETTINGS, BLACKLIST, WHITELIST)) {
			if (Files.notExists(defaults.resolve(name))) readOrCreate(name);
		}
		Path rules = defaults.resolve(UNLOCKING_RULES);
		if (Files.notExists(rules)) Files.writeString(rules, DEFAULT_UNLOCKING_RULES);
	}

	public static Path serverConfigFile(net.minecraft.server.MinecraftServer server, String name) {
		Path override = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
				.resolve(ExplorersCompass.MODID).resolve(Path.of(name).getFileName());
		return Files.exists(override) ? override : directory().resolve(name);
	}

	public static void loadServer(net.minecraft.server.MinecraftServer server) {
		try {
			ensureServerDefaults();
			loadServerFiles(serverConfigFile(server, SERVER_SETTINGS), serverConfigFile(server, BLACKLIST),
					serverConfigFile(server, WHITELIST));
		} catch (IOException e) {
			throw new RuntimeException("Could not load Explorer's Compass server configuration", e);
		}
	}

	private static void loadServerFiles(Path commonFile, Path blacklistFile, Path whitelistFile) throws IOException {
		CommentedConfig common = parse(commonFile);
		loadCommon(common);
		if (common.get("permissions.allowTeleport") != null || common.get("unlocking.compassSharing") == null
				|| !isAtPosition(common, "compass", 0) || !isAtPosition(common, "unlocking", 2)
				|| !isAtPosition(common.get("unlocking"), "visitsSharing", 2)
				|| common.get("unlocking.opacPartySharing") == null || common.get(SEARCH_RADIUS) == null
                    || common.get(SEARCH_SAVED_STRUCTURES) == null || common.get("compass.showNetherEquivalent") == null
                    || !OPAC_SHARING_COMMENT.equals(String.valueOf(common.getComment("unlocking.opacPartySharing")).strip())) {
			common.remove("permissions.allowTeleport");
			writeCommon(common);
			write(commonFile, common);
		}
		structureWhitelist = strings(parse(whitelistFile), "structureWhitelist", List.of());
		CommentedConfig blacklist = parse(blacklistFile);
		structureBlacklist = strings(blacklist, "structureBlacklist", List.of());
		String comment = blacklist.getComment("structureBlacklist");
		if (comment == null || !comment.contains(WHITELIST_PRECEDENCE)) {
			if (comment == null) writeCommonBlacklist(blacklist);
			else blacklist.setComment("structureBlacklist", comment + "\n" + WHITELIST_PRECEDENCE);
			write(blacklistFile, blacklist);
		}
	}

	private static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExplorersCompass.MODID);
	}

	private static CommentedConfig readOrCreate(String name) throws IOException {
		Path path = directory().resolve(name);
		if (Files.exists(path)) return parse(path);
		CommentedConfig config = TomlFormat.newConfig(LinkedHashMap::new);
		switch (name) {
			case SERVER_SETTINGS -> writeCommon(config);
			case CLIENT_SETTINGS -> writeClient(config);
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

	private static void loadCommon(CommentedConfig config) {
		int radius = number(config, SEARCH_RADIUS, DEFAULT_SEARCH_RADIUS, 1);
		if (radius > MAX_SEARCH_RADIUS) throw bad(SEARCH_RADIUS, "integer between 1 and " + MAX_SEARCH_RADIUS);
		maxSearchRadius = radius;
		findOldStructures = bool(config, SEARCH_SAVED_STRUCTURES, false);
		compassSharing = bool(config, "unlocking.compassSharing", false);
		visitsSharing = bool(config, "unlocking.visitsSharing", false);
        opacPartySharing = bool(config, "unlocking.opacPartySharing", true);
		allowExcludeVisited = bool(config, "search.allowExcludeVisited", true);
		relockOnReload = bool(config, "unlocking.relockOnReload", false);
		displayCoordinates = bool(config, "compass.displayCoordinates", true);
		displayDistance = bool(config, "compass.displayDistance", true);
		showNetherEquivalent = bool(config, "compass.showNetherEquivalent", false);
		compassDurability = number(config, "compass.compassDurability", 0, 0);
		defaultXpCost = number(config, "search.defaultXpCost", 0, 0);
		allowSearchWithoutEnoughXp = bool(config, "search.allowSearchWithoutEnoughXp", false);
	}

	static boolean loadClient(CommentedConfig config) {
		displayWithChatOpen = bool(config, "overlay.displayWithChatOpen", displayWithChatOpen);
		pauseGameWhenOpen = config.get("singleplayerAndCreative.pauseGameWhenOpen") != null
				? bool(config, "singleplayerAndCreative.pauseGameWhenOpen", pauseGameWhenOpen)
				: bool(config, "behavior.pauseGameWhenOpen", pauseGameWhenOpen);
		creativeUnlocksAll = bool(config, "singleplayerAndCreative.creativeUnlocksAll", creativeUnlocksAll);
		showTeleportOnlyInCreative = bool(config, "singleplayerAndCreative.showTeleportOnlyInCreative", showTeleportOnlyInCreative);
		clearFiltersOnClose = bool(config, "behavior.clearFiltersOnClose", clearFiltersOnClose);
		clearSearchBarOnClose = bool(config, "behavior.clearSearchBarOnClose", clearSearchBarOnClose);
		closeOnFound = bool(config, "behavior.closeOnFound", closeOnFound);
		checkedFiltersFirst = bool(config, "view.checkedFiltersFirst", checkedFiltersFirst);
		adjustRowsToFitView = bool(config, "view.adjustRowsToFitView", adjustRowsToFitView);
		allowIdSearches = config.get("behavior.allowIdSearches") != null
				? bool(config, "behavior.allowIdSearches", allowIdSearches)
				: bool(config, "behavior.searchTechnicalNames", allowIdSearches);
		showOnlyMatchingFilterOptions = bool(config, "behavior.showOnlyMatchingFilterOptions", showOnlyMatchingFilterOptions);
		matchSearchWordsInAnyOrder = bool(config, "behavior.matchSearchWordsInAnyOrder", false);
		overlayLineOffset = number(config, "overlay.overlayLineOffset", overlayLineOffset, Integer.MIN_VALUE);
		Object side = config.get("overlay.overlaySide");
		if (side != null) {
			try {
				overlaySide = OverlaySide.valueOf((String) side);
			} catch (ClassCastException | IllegalArgumentException e) {
				throw bad("overlay.overlaySide", "LEFT or RIGHT");
			}
		}
		List<String> order = strings(config, "sorting.sortOrder", sortOrder);
		boolean migratedSortOrder = Collections.replaceAll(order, "GROUP", "SET");
		if (!normalizeSortOrder(order).equals(order)) throw bad("sorting.sortOrder", "permutation of NAME, SOURCE, DIMENSION, SET");
		sortOrder = order;
		return migratedSortOrder;
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
		put(config, "unlocking.relockOnReload", relockOnReload, "Relock all player structures when unlocking definitions reload, then restore satisfied requirements. Requires a restart to change.");
		put(config, "compass.displayCoordinates", displayCoordinates, "Show precise coordinates for located structures.");
		put(config, "compass.displayDistance", displayDistance, "Show distance to located structures.");
		put(config, "compass.showNetherEquivalent", showNetherEquivalent, "Point to equivalent coordinates when tracking a structure across the Overworld and Nether.");
        config.remove("search.maxSamples"); // Retired: searches are bounded by radius.
		put(config, "compass.compassDurability", compassDurability, "Successful searches before the compass breaks; 0 disables durability.");
		put(config, "search.defaultXpCost", defaultXpCost, "XP levels consumed after a successful search.");
		put(config, "search.allowSearchWithoutEnoughXp", allowSearchWithoutEnoughXp, "Permit searching with insufficient XP; a successful search drains remaining XP.");
		// Keep the established order of all other sections and settings.
		moveToPosition(config, "compass", 0);
		moveToPosition(config, "unlocking", 2);
		moveToPosition(config.get("unlocking"), "visitsSharing", 2);
	}

	private static void writeClient(CommentedConfig config) {
		put(config, "overlay.overlaySide", overlaySide.name(), "HUD overlay side: LEFT or RIGHT.");
		put(config, "overlay.overlayLineOffset", overlayLineOffset, "Vertical line offset of the HUD overlay.");
		put(config, "overlay.displayWithChatOpen", displayWithChatOpen, "Show compass information while chat is open.");
		put(config, "view.checkedFiltersFirst", checkedFiltersFirst, "Show checked filters before unchecked filters.");
		put(config, "view.adjustRowsToFitView", adjustRowsToFitView, "Adjust row spacing so list entries fit the view.");
		config.remove("view.showTechnicalNames");
		put(config, "behavior.closeOnFound", closeOnFound, "Close the compass screen after finding a structure.");
		put(config, "behavior.clearFiltersOnClose", clearFiltersOnClose, "Clear filters when the compass screen closes.");
		put(config, "behavior.clearSearchBarOnClose", clearSearchBarOnClose, "Clear the search bar when the compass screen closes.");
		config.remove("behavior.pauseGameWhenOpen");
		config.remove("behavior.searchTechnicalNames");
		put(config, "behavior.allowIdSearches", allowIdSearches, "Enables searching for structure IDs such as minecraft:ancient_city as well.\nWorks for filter searches too.");
		put(config, "behavior.showOnlyMatchingFilterOptions", showOnlyMatchingFilterOptions,
				"Show only unchecked filter options with matching structures under the filters in the other columns.");
		put(config, "behavior.matchSearchWordsInAnyOrder", matchSearchWordsInAnyOrder, "Match every part of the main structure search in any order.");
		put(config, "singleplayerAndCreative.pauseGameWhenOpen", pauseGameWhenOpen, "Pause singleplayer while the compass screen is open, except while locating a structure.");
		put(config, "singleplayerAndCreative.creativeUnlocksAll", creativeUnlocksAll, "Temporarily unlock all structures while in Creative mode without changing saved unlocks.");
		put(config, "singleplayerAndCreative.showTeleportOnlyInCreative", showTeleportOnlyInCreative, "Show the Teleport button only in Creative mode. Teleport permission is still required.");
		// Keep sorting last, including when rewriting an existing configuration.
		CommentedConfig sorting = config.remove("sorting");
		if (sorting != null) config.set("sorting", sorting);
		put(config, "sorting.sortOrder", sortOrder, "Order of the four structure sorting keys.");
	}

	private static void writeCommonWhitelist(CommentedConfig config) {
		put(config, "structureWhitelist", List.of(), "Structures allowed by the server. Supports the * wildcard. Omitted namespaces default to minecraft:.\n" + WHITELIST_PRECEDENCE);
	}

	private static void writeCommonBlacklist(CommentedConfig config) {
		put(config, "structureBlacklist", structureBlacklist, "Structures excluded from the compass. Supports the * wildcard. Omitted namespaces default to minecraft:.\n" + WHITELIST_PRECEDENCE);
	}

	private static void writeHiddenStructures(CommentedConfig config) {
		put(config, "hiddenStructures", hiddenStructures, "Structures hidden from this client's compass list.");
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
		saveFile(CLIENT_SETTINGS, ExplorersCompassConfig::writeClient);
	}

	public static void saveHiddenStructures() {
		saveFile(HIDDEN_STRUCTURES, ExplorersCompassConfig::writeHiddenStructures);
	}
}
