package io.github.enriquemetner.explorerscompass.config;

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
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
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
	public static boolean findOldStructures = false;
	public static boolean allowExcludeVisited = true;
	public static int maxSearchRadius = ServerSettingsFile.DEFAULT_SEARCH_RADIUS;
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
		return ServerSettingsFile.load(instanceFile, worldFile);
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
			case SERVER_SETTINGS -> ServerSettingsFile.defaultContents();
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

	static void writeToml(Path path, CommentedConfig config) throws IOException {
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

	static int readInteger(Object value, String key, int fallback, int minimum) {
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

	static IllegalArgumentException bad(String key, String expected) {
		return new IllegalArgumentException("Invalid Explorer's Compass setting '" + key + "'; expected " + expected);
	}

	static boolean loadClient(CommentedConfig config) {
		displayWithChatOpen = bool(config, clientPath(config, "hud.displayWithChatOpen", "overlay.displayWithChatOpen"), displayWithChatOpen);
		String offsetPath = clientPath(config, "hud.verticalOffset", "overlay.overlayLineOffset");
		overlayLineOffset = Math.max(0, readInteger(config.get(offsetPath), offsetPath, overlayLineOffset, Integer.MIN_VALUE));
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
			writeToml(path, config);
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
