package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.compat.jes.JesThumbnails;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.ClientSetting;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import io.github.enriquemetner.explorerscompass.network.CurrentStructurePacket;
import io.github.enriquemetner.explorerscompass.network.SearchPacket;
import io.github.enriquemetner.explorerscompass.network.SearchListedPacket;
import io.github.enriquemetner.explorerscompass.network.CancelSearchPacket;
import io.github.enriquemetner.explorerscompass.network.SearchResultPacket;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.github.enriquemetner.explorerscompass.network.RefreshCompassPacket;
import io.github.enriquemetner.explorerscompass.network.TeleportPacket;
import io.github.enriquemetner.explorerscompass.sorting.SortCriterion;
import io.github.enriquemetner.explorerscompass.util.CompassState;
import io.github.enriquemetner.explorerscompass.util.ItemUtils;
import io.github.enriquemetner.explorerscompass.util.OverlaySide;
import io.github.enriquemetner.explorerscompass.util.RenderUtils;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.font.TextRenderer;
import io.github.enriquemetner.explorerscompass.mixins.DrawContextAccessor;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class ExplorersCompassScreen extends Screen {
	private static final int SIDEBAR_X = 10;
	private static final int SMALL_BUTTON_SIZE = 20;
	private static final int SEARCH_BUTTON_GAP = 5;
	private static final String SELECTION_TOGGLE_TOOLTIP = "List all selected structures, regardless of searched or filtered results.\n"
			+ "Editing the search bar or filters returns to normal results.";
	private static final int SORT_ROW_HEIGHT = 20;
	private static final int SORT_LABEL_GAP = 11;
	private static final int SORT_HIGHLIGHT_PADDING = 4;
	private static final int HEADER_GAP = 10;
	private static final int PREFERRED_FILTER_ROW_HEIGHT = 20;
	private static final int FILTER_HEADER_HEIGHT = 20;
	private static final int FILTER_HEADER_GAP = 1;
	private static final int FILTER_COLUMN_GAP = 1;
	private static final int FILTER_SECTION_GAP = 1;
	private static final int FILTER_CHECKBOX_SIZE = 9;
	private static final int HEADER_TITLE_MARGIN = 16;
	private static final Identifier CLOSE_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/close.png");
	private static final int CLOSE_ICON_SIZE = 7;
	private static final Identifier CLEAR_FILTERS_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/clear_filters.png");
	private static final int CLEAR_FILTERS_ICON_SIZE = 11;
	private static final int HEADER_BUTTON_TEXT_PADDING = 20;
	private static final int EXCLUDE_BUTTON_CONTENT_PADDING = HEADER_BUTTON_TEXT_PADDING / 2;
	private static final int EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP = 5;
	private static final int SCROLLBAR_WIDTH = Scrollbar.WIDTH;
	private static final int SIDEBAR_BUTTON_STEP = 25;
	private static final int SETTINGS_ROW_HEIGHT = SIDEBAR_BUTTON_STEP;
	private static final int SETTINGS_CONTROL_WIDTH = 90;
	private static final Identifier SETTINGS_RESET_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/restore_default.png");
	private static final int SETTINGS_RESET_ICON_SIZE = 9;
	private static final int SETTINGS_RESET_LABEL_GAP = 4;
	private static final String SETTINGS_RESET_ALL_LABEL = "All";
	private static final int SETTINGS_OFFSET_ROW = 2;
	private static final int SETTINGS_HIDE_SEARCH_COST_ROW = 7;
	private static final int SETTINGS_CLOSE_ON_FOUND_ROW = 9;
	private static final int SETTINGS_CLEAR_SEARCH_ROW = 10;
	private static final int SETTINGS_CLEAR_FILTERS_ROW = 11;
	private static final int SETTINGS_MATCH_SEARCH_WORDS_ROW = 12;
	private static final int SETTINGS_MATCHING_FILTER_OPTIONS_ROW = 13;
	private static final int SETTINGS_HIDDEN_ROW = 19;
	private static final String HIDDEN_STRUCTURES_TITLE = "Hidden Structures";
	private static final int SETTINGS_PAUSE_ROW = 17;
	private static final int SETTINGS_CREATIVE_UNLOCK_ROW = 15;
	private static final int SETTINGS_TELEPORT_ROW = 16;
	private static final int SETTINGS_DIVIDER_COLOR = 0xff909090;
	private static String sessionSearchText = "";
	private String lastSearchFieldText;
	private static final int SEARCH_DIALOG_WIDTH = 245;
	private static final int SEARCH_DIALOG_HEIGHT = 119;
	private static final int SEARCH_DIALOG_TITLE_Y = 32;
	private static final int FOUND_ACTION_BUTTON_WIDTH = 70;
	private static final int FOUND_ACTION_BUTTON_GAP = 5;
	private static final int NOT_FOUND_BUTTON_GAP = 5;
	private static final String[] SETTINGS_LABELS = {
		"HUD",
		"HUD Side",
		"Vertical HUD offset",
		"Show While Chat Is Open",
		"View",
		"Adjust Rows to Fit View",
		"Checked Filters On Top",
		"Hide Search Costs",
		"Behavior",
		"Close UI When located",
		"Clear Search Bar on Close",
		"Clear Filters on Close",
		"Flexible Search",
		"Show Only Matching Filter Options",
		"Creative & Singleplayer",
		"Unlock All Structures in Creative",
		"Show Teleport Only in Creative",
		"Pause Game While Open",
		"Other",
		HIDDEN_STRUCTURES_TITLE,
	};
	private static final String[] SETTINGS_TOOLTIPS = {
		null,
		"Choose the position of the compass pointing information on the screen.",
		"Move the compass pointing information vertically.\nHigher values move it down;\nLower values move it up.",
		"Keep compass pointing information visible while the chat is shown.",
		null,
		"Adjusts the spacing of rows, showing less content but only fully visible list entries.",
		"Show checked filter options separately above unchecked ones.",
		"Hide all structure search costs from the list.\nReasonable when all structures have the same search cost.",
		null,
		"Immediately close the compass screen when the compass finds a structure.",
		"Clear the search bar when you close the screen.",
		"Clear selected filters when you close the compass screen.",
		"Match every typed word fragment contained in a structure name, in any order.\nExample: \"vil pla\" can match \"Village Plains\".",
		"Hide filter options which would produce an empty list if checked.\nExample: Checking \"The Nether\" would hide the \"Villages\" set.",
		null,
		"Unlock all structures while in Creative mode.\nChanging the mode back relocks the right structures.",
		"Hide the teleport button in any game mode other than Creative.",
		"Pauses your Singleplayer world while the compass screen is open.",
		null,
		"View and restore structures hidden on this client.",
	};

	private World world;
	private PlayerEntity player;
	private List<Identifier> allowedStructureIDs;
	private List<Identifier> structureIDsMatchingSearch;
	private Identifier foundStructureId;
	private ItemStack stack;
	private ExplorersCompassItem explorersCompass;
	private enum ButtonLayer { STRUCTURES, FILTERS, SETTINGS, HIDDEN, SORT }
	private ButtonWidget searchButton;
	private ButtonWidget selectionToggleButton;
	private boolean selectedOnly;
	private TransparentButton sortOrderButton;
	private ButtonWidget filtersButton;
	private ButtonWidget currentStructureButton;
	private ButtonWidget settingsButton;
	private ButtonWidget restoreDefaultsButton;
	private final Map<Integer, ButtonWidget> settingResetButtons = new HashMap<>();
	private ButtonWidget chatOverlayButton;
	private ButtonWidget showOnlyMatchingFilterOptionsButton;
	private ButtonWidget matchSearchWordsButton;
	private ButtonWidget hideSearchCostButton;
	private ButtonWidget overlaySideButton;
	private ButtonWidget clearOnCloseButton;
	private ButtonWidget clearSearchBarOnCloseButton;
	private ButtonWidget pauseGameWhenOpenButton;
	private ButtonWidget creativeUnlocksAllButton;
	private ButtonWidget showTeleportOnlyInCreativeButton;
	private ButtonWidget closeOnFoundButton;
	private ButtonWidget checkedFiltersFirstButton;
	private ButtonWidget adjustRowsToFitViewButton;
	private ButtonWidget offsetDecreaseButton;
	private ButtonWidget offsetIncreaseButton;
	private ButtonWidget clearFiltersButton;
	private ButtonWidget clearFiltersTopButton;
	private ButtonWidget excludeVisitedButton;
	private ButtonWidget excludeLockedButton;
    private ButtonWidget newlyUnlockedButton;
	private ButtonWidget backButton;
	private ButtonWidget teleportButton;
	private ButtonWidget cancelButton;
	private TransparentTextField searchTextField;
	private TransparentTextField filterSearchTextField;
	private TransparentTextField settingsSearchTextField;
	private TransparentTextField hiddenSearchTextField;
	private final StructureSelection structureSelection = new StructureSelection();
	private StructureSearchList selectionList;
	private boolean awaitingOpeningSync = true;
	private EntryDetailsPopup entryDetails;
	private CurrentStructurePopup currentStructurePopup;
	private UUID currentStructureRequest;
	private Identifier revealedStructure;
	private final Set<Identifier> pendingHiddenStructures = new HashSet<>();
	private String hiddenSearchText = "";
	private boolean filtersOpen;
	private boolean settingsOpen;
    private boolean hiddenStructuresOpen;
    private int hiddenScroll;
    private ButtonWidget hiddenStructuresButton;
    public void preparePartyRefresh() {
        questReturnScroll = selectionList.getScrollAmount();
        restoreQuestListAfterSync = true;
    }
    private DisabledStructureView disabledView;
    private int listedDisabledCount;
    private Identifier navigationTarget;
    private Identifier selectionAfterSync;
    private Identifier excludedStructure;
    private boolean excludedByWhitelist;
    private enum Dialog { NONE, LOCKED, DISABLED, WRONG_DIMENSION, INSUFFICIENT_COST, MULTI_COST, SEARCHING, FOUND, NOT_FOUND, INVALID_SEARCH, EXCLUDED, REJECTED }
    private Dialog dialog = Dialog.NONE;
    private LockedRequirementsView lockedRequirementsView;
    private Identifier lockedRequirementsId;
    private boolean refreshAfterQuestBook;
    private boolean restoreQuestListAfterSync;
    private double questReturnScroll;
    private int insufficientCost;
    private int availableEnergy;
    private long dialogStarted;
    private long notFoundCompassUpdated;
    private double notFoundCompassAngle;
    private double notFoundCompassSpeed;
    private int activeRequestId;
    private boolean searchConfirmed;
    private int searchRadius;
    private int failedRadius;
    private boolean searchedExcludeVisited;
    private static final int FOUND_DETAILS_TOP = 50;
    private static final int FOUND_LINE_STEP = 12;
    private static final String FOUND_BIOMES_LABEL = "Biomes: ";
    private List<Text> foundBiomes = List.of();
    private Boolean foundVisited;
    private double foundScroll;
    private double searchFailureScroll;
    private int searchFailureWrapWidth;
    private List<SearchFailureLine> searchFailureLines = List.of();
    private record SearchFailureLine(OrderedText text, boolean centered, int indent, boolean bullet) {
        SearchFailureLine(OrderedText text, boolean centered) {
            this(text, centered, 0, false);
        }
    }
    private Identifier searchedId;
    private boolean searchedListed;
    private int listedSelectedCount;
    private int listedSkippedCount;
    private boolean successfulClose;
    private int foundX;
    private int foundZ;
    private boolean showFoundCoordinates;
    private boolean showFoundDistance;
	private double filterScroll;
	private int settingsFirstRow;
	private List<Integer> settingsRows = List.of();
	private boolean lastCreativeBypass;
	private enum ScrollPanel { FILTERS, SETTINGS, HIDDEN, FOUND, SEARCH_FAILURE }
	private ScrollPanel draggedScrollbar;
	private boolean draggingStructureScrollbar;
	private double scrollbarGrabOffset;
	private final List<List<FilterOption>> filterOptions = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	private FilterSelections.AvailableOptions availableFilterOptions;
	private boolean showSourceColumn = true;
	private final List<SortCriterion> sortOrder = new ArrayList<SortCriterion>();
	private boolean sortMenuOpen;
	private int draggedSortRow = -1;
	private boolean sortOrderChangedDuringDrag;
	private Identifier entryFiltersId;
	private int entryFilterFirstRow;
	private static final int ENTRY_FILTER_ROW_HEIGHT = 20;
	private record EntryFilterRow(String category, String value, int column, FilterOption option) {}

	private static class FilterOption {
		private final String label;
		private final String source;
		private final Identifier id;
		private final boolean current;

		private FilterOption(String label, String source, Identifier id, boolean current) {
			this.label = label;
			this.source = source;
			this.id = id;
			this.current = current;
		}
	}

	public ExplorersCompassScreen(World world, PlayerEntity player, ItemStack stack, ExplorersCompassItem explorersCompass, List<Identifier> allowedStructureIDs) {
		super(Text.literal("Select Structure"));
		this.world = world;
		this.player = player;
		this.stack = stack;
		this.explorersCompass = explorersCompass;
		lastCreativeBypass = creativeBypassActive();
		FilterSelections.openedInDimension(world.getRegistryKey().getValue());

		this.allowedStructureIDs = new ArrayList<Identifier>(allowedStructureIDs);
		structureIDsMatchingSearch = new ArrayList<Identifier>(allowedStructureIDs);
		for (String name : ExplorersCompassConfig.sortOrder) {
			sortOrder.add(SortCriterion.valueOf(name));
		}

		if (explorersCompass.getState(stack) == CompassState.FOUND) {
			foundStructureId = explorersCompass.getStructureID(stack);
		}
	}

    private final JesThumbnails jesThumbnails = JesThumbnails.create();
    private boolean openingJesPreview;
    private static Screen pausedIntegrationScreen;

    JesThumbnails jesThumbnails() {
        return jesThumbnails;
    }

    void openJesPreview(Identifier id) {
        openingJesPreview = true;
        try {
            if (jesThumbnails.open(this, id)) pauseOpenedIntegration();
        } finally {
            openingJesPreview = false;
        }
    }

	@Override
	public boolean shouldPause() {
		return ExplorersCompassConfig.pauseGameWhenOpen;
	}

	public static void clearWorldSearchState() {
		sessionSearchText = "";
		FilterSelections.clear();
	}

	@Override
	public void removed() {
        if (openingRequirementAdvancement) return;
		currentStructureRequest = null;
		currentStructurePopup = null;
		clearCurrentStructureReveal();
        if (openingJesPreview) return;
        if (jesThumbnails != null) jesThumbnails.close();
        var handler = client.getNetworkHandler();
        boolean connected = handler != null && handler.getConnection().isOpen();
        if (dialog == Dialog.SEARCHING && !successfulClose && connected) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(activeRequestId));
        }
		if (ExplorersCompassConfig.clearFiltersOnClose && !FilterSelections.isEmpty()) {
			FilterSelections.clear();
			showNormalResults();
		}
		if (connected) {
			sessionSearchText = ExplorersCompassConfig.clearSearchBarOnClose ? "" : searchTextField.getText();
		}
		pendingHiddenStructures.clear();
		hiddenSearchText = "";
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double scroll1, double scroll2, double scroll3) {
        if (dialog == Dialog.DISABLED) { disabledView.scroll(scroll3); return true; }
		if (currentStructurePopup != null) { currentStructurePopup.scroll(scroll3, width, height); return true; }
		if (entryDetails != null) { entryDetails.scroll(scroll3, width, height); return true; }
		if (entryFiltersId != null) {
			List<EntryFilterRow> rows = entryFilterRows();
			entryFilterFirstRow = MathHelper.clamp(entryFilterFirstRow - (int) Math.signum(scroll3), 0,
					Math.max(0, rows.size() - entryFilterVisibleRows(rows.size())));
			return true;
		}
        if (dialog == Dialog.LOCKED) { lockedRequirementsView.scroll(scroll3); return true; }
        if (dialog == Dialog.FOUND) {
            foundScroll = Scrollbar.scrollPixels(foundScroll, scroll3, FOUND_LINE_STEP, foundMaxScroll());
            return true;
        }
        if (isSearchFailureDialog()) { scrollSearchFailure(scroll3); return true; }
        if (dialog != Dialog.NONE) return true;
        if (hiddenStructuresOpen) {
            hiddenScroll = Scrollbar.scrollRows(hiddenScroll, scroll3, 2, hiddenMaxScroll());
            return true;
        }
		if (settingsOpen) {
			settingsFirstRow = Scrollbar.scrollRows(settingsFirstRow, scroll3, 1, settingsMaxFirstRow());
			updateViewButtons();
			return true;
		}
		if (filtersOpen) {
			if (scroll1 >= filterLeft() && scroll1 < filterScrollbarRight() && scroll2 >= filterTop() && scroll2 < filterBottom()) {
				filterScroll = Scrollbar.scrollPixels(filterScroll, scroll3, filterRowHeight(), filterMaxScroll());
				return true;
			}
			return super.mouseScrolled(scroll1, scroll2, scroll3);
		}
		if (sortMenuOpen) return true;
		return selectionList.mouseScrolled(scroll1, scroll2, scroll3);
	}

	@Override
	protected void init() {
		boolean firstOpen = selectionList == null;
		double scroll = firstOpen ? 0 : selectionList.getScrollAmount();
		if (searchTextField != null) sessionSearchText = searchTextField.getText();
		clearChildren();
		setupButtons();
		selectionList = new StructureSearchList(this, client, structureSelection, structureListX(), 40, width - structureListX() - StructureSearchList.SCROLLBAR_WIDTH, height - 50, 50);
		if (isStructureView()) {
			addDrawableChild(selectionList);
		}
		entryFiltersId = null;
		setupTextFields();
		rebuildFilterOptions();
		processSearchTerm();
		if (firstOpen) selectionList.focusStructure(foundStructureId);
		else selectionList.setScrollAmount(scroll);
		focusActiveSearchField();
		if (refreshAfterQuestBook && client.getNetworkHandler() != null) {
			refreshAfterQuestBook = false;
			ExplorersCompass.synced = false;
			ClientPlayNetworking.send(RefreshCompassPacket.ID, new RefreshCompassPacket());
		}
	}

	@Override
	public void tick() {
        if ((dialog == Dialog.WRONG_DIMENSION || dialog == Dialog.INSUFFICIENT_COST) && System.currentTimeMillis() - dialogStarted >= 3000) {
			dialog = Dialog.NONE;
			focusActiveSearchField();
		}
		searchTextField.tick();
		filterSearchTextField.tick();
		settingsSearchTextField.tick();
		hiddenSearchTextField.tick();
		if (lastCreativeBypass != creativeBypassActive()) {
			lastCreativeBypass = creativeBypassActive();
			if (lastCreativeBypass && dialog == Dialog.LOCKED && !isUnlockedThroughParty(lockedRequirementsId)) {
				dialog = Dialog.NONE;
				lockedRequirementsView = null;
				lockedRequirementsId = null;
			}
			processSearchTerm();
		}
		teleportButton.visible = isStructureView() && showTeleportButton();

		refreshSelectedOnlyRows();
		updateSearchButtons();
		teleportButton.active = teleportButton.visible && canTeleportToTarget();
		sortOrderButton.active = isStructureView();

		// Check if the allowed structure list has synced
		if (ExplorersCompass.synced) {
			if (dialog == Dialog.LOCKED && lockedRequirementsId != null) {
				var partyRequirements = ClientPacketHandlers.partyRequirements.get(lockedRequirementsId);
				var updated = partyRequirements != null ? partyRequirements : ExplorersCompass.lockedRequirements.get(lockedRequirementsId);
				if (updated == null) {
                    backLockedPopup();
                } else if (!lockedRequirementsView.shows(updated)) {
                    double popupScroll = lockedRequirementsView.scrollPosition();
                    setLockedPopup(lockedRequirementsId, updated, null);
                    lockedRequirementsView.restoreScroll(popupScroll);
                }
			}
			entryFiltersId = null;
			boolean focusTrackedStructure = awaitingOpeningSync && !restoreQuestListAfterSync;
			awaitingOpeningSync = false;
			foundStructureId = ClientPacketHandlers.trackedStructureId;
			double scroll = restoreQuestListAfterSync ? questReturnScroll : selectionList.getScrollAmount();
			remove(selectionList);
			allowedStructureIDs = new ArrayList<Identifier>(ExplorersCompass.allowedStructureIDs);
			structureSelection.retain(new HashSet<>(allowedStructureIDs)::contains);
			structureIDsMatchingSearch = new ArrayList<Identifier>(allowedStructureIDs);
			selectionList = new StructureSearchList(this, client, structureSelection, structureListX(), 40, width - structureListX() - StructureSearchList.SCROLLBAR_WIDTH, height - 50, 50);
			if (isStructureView()) {
				addDrawableChild(selectionList);
			}

			teleportButton.visible = isStructureView() && showTeleportButton();
			positionFilterButtons();
			positionTopButtons();
			positionFilterSearchField();
			updateViewButtons();
			rebuildFilterOptions();
			processSearchTerm();
			selectionList.setScrollAmount(scroll);
            if (selectionAfterSync != null) {
                selectSyncedStructure(selectionAfterSync);
                selectionAfterSync = null;
            } else if (focusTrackedStructure) {
                selectionList.focusStructure(foundStructureId);
            }
			restoreQuestListAfterSync = false;
			focusActiveSearchField();

			ExplorersCompass.synced = false;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        // Covered layers still draw, but only the top layer gets the pointer.
        final int offscreen = -10000;
        boolean dialogExposed = entryDetails == null && currentStructurePopup == null;
        boolean filtersExposed = dialogExposed && dialog == Dialog.NONE;
        boolean sortExposed = filtersExposed && entryFiltersId == null;
        boolean pageExposed = sortExposed && !sortMenuOpen;
        renderPage(context, pageExposed ? mouseX : offscreen, pageExposed ? mouseY : offscreen, partialTicks);
        if (sortMenuOpen) renderSortMenu(context, sortExposed ? mouseX : offscreen, sortExposed ? mouseY : offscreen);
        if (entryFiltersId != null) renderEntryFilters(context, filtersExposed ? mouseX : offscreen, filtersExposed ? mouseY : offscreen);
        if (dialog != Dialog.NONE && (dialog != Dialog.SEARCHING || searchConfirmed)) {
            renderSearchDialog(context, dialogExposed ? mouseX : offscreen, dialogExposed ? mouseY : offscreen);
        }
        if (entryDetails != null) entryDetails.render(context, width, height, mouseX, mouseY);
        if (currentStructurePopup != null) currentStructurePopup.render(context, width, height, mouseX, mouseY);
    }

    private void renderPage(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        renderBackground(context);
		if (filtersOpen) {
			renderFilterPanel(context, mouseX, mouseY);
        } else if (hiddenStructuresOpen) {
            renderHiddenStructures(context, mouseX, mouseY);
		} else if (settingsOpen) {
			renderSettingsPanel(context);
		}
        SearchCosts costs = SearchCosts.remote();
        boolean showBalance = isStructureView() && costs.showsSearchCosts();
        String headerLabel = switch (costs.type()) {
            case ITEMS -> "Energy: ";
            case XP -> "Experience: ";
            case XP_LEVELS -> "XP Levels: ";
        };
        int balance = showBalance ? mainViewBalance(costs) : 0;
        int balanceWidth = showBalance ? costLineWidth(headerLabel, balance, "") : 0;
        int balanceX = cancelButton.getX() - HEADER_GAP - balanceWidth;
        if (isStructureView()) {
            searchTextField.setWidth(Math.max(1, balanceX - searchTextField.getX() - (showBalance ? HEADER_GAP : 0)));
        }
		super.render(context, mouseX, mouseY, partialTicks);
        if (showBalance) {
            int balanceY = cancelButton.getY() + (cancelButton.getHeight() - textRenderer.fontHeight) / 2;
            renderCostLine(context, balanceX + balanceWidth / 2, balanceY, headerLabel, balance, "");
            if (costs.type() == SearchCosts.Type.ITEMS && mouseX >= balanceX && mouseX < balanceX + balanceWidth
                    && mouseY >= balanceY && mouseY < balanceY + textRenderer.fontHeight) {
                renderEnergyTooltip(context, mouseX, mouseY);
            }
        }
		renderExcludeButton(context, excludeVisitedButton, "Exclude Visited", FilterSelections.excludeVisited(), mouseX, mouseY);
		renderExcludeButton(context, excludeLockedButton, "Exclude Locked", FilterSelections.excludeLocked(), mouseX, mouseY);
        renderExcludeButton(context, newlyUnlockedButton, "Newly Unlocked", FilterSelections.newlyUnlocked(), mouseX, mouseY);
		if (excludeVisitedButton.visible && excludeVisitedButton.isMouseOver(mouseX, mouseY)) {
			drawQuestionTooltip(context, "Skip locations you’ve already visited when locating a structure.", mouseX, mouseY);
		}
		if (excludeLockedButton.visible && excludeLockedButton.isMouseOver(mouseX, mouseY)) {
			drawQuestionTooltip(context, "Hide structures you haven’t unlocked from the structure list.", mouseX, mouseY);
		}
        if (newlyUnlockedButton.visible && newlyUnlockedButton.isMouseOver(mouseX, mouseY))
            drawQuestionTooltip(context, "Shows only structures unlocked in your most recent unlocking event.", mouseX, mouseY);
		renderCloseIcon(context, cancelButton);
		if (filtersOpen) {
			renderFilterTooltip(context, mouseX, mouseY);
		}
		if (filtersOpen) {
			context.drawTextWithShadow(textRenderer, Text.literal("Filters"), HEADER_TITLE_MARGIN, 15, 0xffffff);
        } else if (hiddenStructuresOpen) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(HIDDEN_STRUCTURES_TITLE,
                    Math.max(1, hiddenSearchTextField.getX() - 20)), 10, 15, 0xffffff);
		} else if (settingsOpen) {
			context.drawTextWithShadow(textRenderer, Text.literal("Settings"), HEADER_TITLE_MARGIN, 15, 0xffffff);
		} else {
			context.drawTextWithShadow(textRenderer, title, HEADER_TITLE_MARGIN, 15, 0xffffff);
		}
		if (settingsOpen) {
			renderSettingsTooltip(context, mouseX, mouseY);
		}
		if (selectionToggleButton.visible && selectionToggleButton.isMouseOver(mouseX, mouseY)) {
			drawQuestionTooltip(context, SELECTION_TOGGLE_TOOLTIP, mouseX, mouseY);
		}
	}

    private void renderEnergyTooltip(DrawContext context, int mouseX, int mouseY) {
        Text instructions = Text.literal(
                "In your inventory, pick up a valid energy item and hold it over the compass.\n"
                + "Middle-click to consume the whole stack, or scroll to consume one item at a time.");
        List<TooltipComponent> rows = new ArrayList<>();
        textRenderer.wrapLines(instructions, Math.max(1, Math.min(220, width - 30)))
                .forEach(line -> rows.add(TooltipComponent.of(line)));
        rows.add(TooltipComponent.of(Text.empty().asOrderedText()));
        rows.add(TooltipComponent.of(Text.literal("Compass Charging Rates:")
                .formatted(net.minecraft.util.Formatting.AQUA).asOrderedText()));
        SearchCosts.remote().items().entrySet().stream()
                .sorted(Map.Entry.<Identifier, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> {
                    Identifier id = entry.getKey();
                    ItemStack item = Registries.ITEM.get(id).getDefaultStack();
                    String name = item.isEmpty() ? id.toString() : item.getName().getString();
                    rows.add(new ChargingRateRow(item, name + ": ", entry.getValue().toString(), textRenderer.fontHeight));
                });
        ((DrawContextAccessor) context).explorersCompass$drawTooltip(
                textRenderer, rows, mouseX, mouseY, HoveredTooltipPositioner.INSTANCE);
    }

    private record ChargingRateRow(ItemStack item, String label, String rate, int iconSize) implements TooltipComponent {
        @Override
        public int getHeight() {
            return iconSize + 1;
        }

        @Override
        public int getWidth(TextRenderer font) {
            return font.getWidth("• ") + iconSize + 2 + font.getWidth(label) + font.getWidth(rate) + 8;
        }

        @Override
        public void drawItems(TextRenderer font, int x, int y, DrawContext context) {
            int textY = y + (getHeight() - font.fontHeight) / 2;
            context.drawTextWithShadow(font, "• ", x, textY, 0xffffff);
            int iconX = x + font.getWidth("• ");
            if (item.isOf(ExplorersCompass.EXPLORERS_COMPASS_ITEM)) {
                CompassTextures.drawStillIcon(context, iconX, textY, iconSize);
            } else {
                float scale = iconSize / 16.0f;
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(iconX, textY, 0);
                matrices.scale(scale, scale, 1);
                context.drawItem(item, 0, 0);
                matrices.pop();
            }
            int labelX = iconX + iconSize + 2;
            context.drawTextWithShadow(font, label, labelX, textY, 0xffffff);
            int rateX = labelX + font.getWidth(label);
            drawExperienceNumber(context, font, rate, rateX, textY);
            drawEnergyOrb(context, font, rateX + font.getWidth(rate) + 3, textY);
        }
    }

    private int mainViewBalance(SearchCosts costs) {
        ItemStack held = ItemUtils.getHeldItem(player, explorersCompass);
        return costs.available(player, held.isEmpty() ? stack : held);
    }

	private void updateSearchButtons() {
		int count = structureSelection.size();
		boolean showCount = isStructureView() && count >= (selectedOnly ? 1 : 2);
		selectionToggleButton.visible = showCount;
		if (showCount) selectionToggleButton.setMessage(Text.literal(Integer.toString(count)));
		searchButton.setWidth(showCount ? sidebarWidth() - SMALL_BUTTON_SIZE - SEARCH_BUTTON_GAP : sidebarWidth());
		searchButton.active = isStructureView() && !structureSelection.isEmpty();
		boolean canClear = selectedOnly || !FilterSelections.isEmpty();
		clearFiltersButton.active = canClear;
		clearFiltersTopButton.active = canClear;
	}

	private void refreshSelectedOnlyRows() {
		if (!selectedOnly) return;
		if (structureSelection.size() == 1 && selectionList.awaitingPlainDoubleClick()) return;
		if (structureSelection.isEmpty() || structureIDsMatchingSearch.size() != structureSelection.size()
				|| !structureIDsMatchingSearch.stream().allMatch(structureSelection::contains)) {
			double scroll = selectionList.getScrollAmount();
			processSearchTerm();
			if (selectedOnly) selectionList.setScrollAmount(scroll);
		}
	}

	private int sortMenuWidth() {
		return Math.max(40, Math.min(LockedRequirementsView.WINDOW_WIDTH, width - 24));
	}

	private int sortMenuX() {
		return (width - sortMenuWidth()) / 2;
	}

	private int sortMenuY() {
		return (height - sortMenuHeight()) / 2;
	}

	private int sortMenuHeight() { return 65 + sortOrder.size() * SORT_ROW_HEIGHT; }
	private int sortRowsY() { return sortMenuY() + 30; }
	private int sortBackY() { return sortRowsY() + sortOrder.size() * SORT_ROW_HEIGHT + 10; }
	private int sortLabelWidth() {
		int width = 0;
		for (SortCriterion criterion : sortOrder) width = Math.max(width, textRenderer.getWidth(criterion.getLabel()));
		return width;
	}
	private int sortNumberRightX() { return sortMenuX() + sortMenuWidth() / 2 - sortLabelWidth() / 2 - SORT_LABEL_GAP; }
	private int sortArrowsX() { return sortMenuX() + sortMenuWidth() / 2 + (sortLabelWidth() + 1) / 2 + SORT_LABEL_GAP; }
	private int sortContentLeft() {
		return sortNumberRightX() - textRenderer.getWidth(sortOrder.size() + ".") - SORT_HIGHLIGHT_PADDING;
	}
	private int sortContentRight() { return sortArrowsX() + 5 + SORT_HIGHLIGHT_PADDING; }

	private void closeSortMenu() {
		commitSortOrder();
		sortMenuOpen = false;
		draggedSortRow = -1;
	}

	private void commitSortOrder() {
		if (sortOrderChangedDuringDrag) {
			ExplorersCompassConfig.sortOrder = sortOrder.stream().map(Enum::name).collect(Collectors.toList());
			ExplorersCompassConfig.saveClient();
			selectionList.refreshList();
			sortOrderChangedDuringDrag = false;
		}
	}

	private int sortRowAt(double mouseX, double mouseY) {
		int y = sortRowsY();
		if (mouseX < sortContentLeft() || mouseX >= sortContentRight() || mouseY < y || mouseY >= y + sortOrder.size() * SORT_ROW_HEIGHT) {
			return -1;
		}
		return (int) (mouseY - y) / SORT_ROW_HEIGHT;
	}

	private void renderSortMenu(DrawContext context, int mouseX, int mouseY) {
		int x = sortMenuX();
		int y = sortMenuY();
		int menuWidth = sortMenuWidth();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 300);
		context.fill(0, 0, width, height, 0x88000000);
		context.fill(x - 1, y - 1, x + menuWidth + 1, y + sortMenuHeight() + 1, 0xff909090);
		context.fill(x, y, x + menuWidth, y + sortMenuHeight(), 0xff000000);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Sort Priority"), x + menuWidth / 2, y + 10, 0xffffff);
		int hoveredRow = sortRowAt(mouseX, mouseY);
		int centerX = x + menuWidth / 2;
		int numberRightX = sortNumberRightX();
		int arrowsX = sortArrowsX();
		for (int i = 0; i < sortOrder.size(); i++) {
			int rowY = sortRowsY() + i * SORT_ROW_HEIGHT;
			if (i == draggedSortRow || i == hoveredRow)
				context.fill(sortContentLeft(), rowY, sortContentRight(), rowY + SORT_ROW_HEIGHT, 0xff242424);
			String number = (i + 1) + ".";
			context.drawTextWithShadow(textRenderer, number, numberRightX - textRenderer.getWidth(number), rowY + 6, 0xffaaaaaa);
			context.drawCenteredTextWithShadow(textRenderer, sortOrder.get(i).getLabel(), centerX, rowY + 6, 0xffffff);
			renderSortArrows(context, arrowsX, rowY + 6);
		}
		int backX = x + (menuWidth - 70) / 2;
		int backY = sortBackY();
		drawDialogButton(context, "Back", backX, backY, 70, mouseX, mouseY);
		context.getMatrices().pop();
	}

	private void renderSortArrows(DrawContext context, int x, int y) {
		int color = 0xffaaaaaa;
		context.fill(x + 2, y, x + 3, y + 1, color);
		context.fill(x + 1, y + 1, x + 4, y + 2, color);
		context.fill(x, y + 2, x + 5, y + 3, color);
		context.fill(x, y + 4, x + 5, y + 5, color);
		context.fill(x + 1, y + 5, x + 4, y + 6, color);
		context.fill(x + 2, y + 6, x + 3, y + 7, color);
	}

	private int filterLeft() {
		return SCROLLBAR_WIDTH;
	}

	private int filterRight() {
		return width - SCROLLBAR_WIDTH;
	}

	private int filterScrollbarRight() {
		return filterRight() + SCROLLBAR_WIDTH;
	}

	private int filterTop() {
		return filtersOpen ? 70 : 40;
	}

	private int filterHeaderTop() {
		return filterTop();
	}

	private int filterRowsTop() {
		return filterHeaderTop() + FILTER_HEADER_HEIGHT + FILTER_HEADER_GAP;
	}

	private int filterBottom() {
		return height - 10;
	}

	private int filterVisibleRows() {
		int availableHeight = Math.max(1, filterBottom() - filterRowsTop() - FILTER_SECTION_GAP);
		return Math.max(1, availableHeight / PREFERRED_FILTER_ROW_HEIGHT);
	}

	private int filterRowHeight() {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return PREFERRED_FILTER_ROW_HEIGHT;
		}
		int availableHeight = Math.max(1, filterBottom() - filterRowsTop() - FILTER_SECTION_GAP);
		return Math.max(PREFERRED_FILTER_ROW_HEIGHT, availableHeight / filterVisibleRows());
	}

	private int filterFullRowsBottom(int sectionStart) {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return filterBottom();
		}
		int rowsBottom = filterRowsTop() + filterVisibleRows() * filterRowHeight();
		if (sectionStart >= 0 && filterRowsTop() + sectionStart * filterRowHeight() - visibleFilterScroll() < rowsBottom) {
			rowsBottom += FILTER_SECTION_GAP;
		}
		return Math.min(filterBottom(), rowsBottom);
	}

	private int filterColumnLeft(int column) {
		int firstColumn = showSourceColumn ? 0 : 1;
		int columnCount = showSourceColumn ? 3 : 2;
		return filterLeft() + (filterRight() - filterLeft()) * (column - firstColumn) / columnCount;
	}

	private int filterColumnRight(int column) {
		int firstColumn = showSourceColumn ? 0 : 1;
		int columnCount = showSourceColumn ? 3 : 2;
		int edge = filterLeft() + (filterRight() - filterLeft()) * (column - firstColumn + 1) / columnCount;
		return column == 2 ? edge : edge - FILTER_COLUMN_GAP;
	}

	private int filterColumnAt(double mouseX, double mouseY) {
		if (mouseX < filterLeft() || mouseX >= filterRight() || mouseY < filterHeaderTop() || mouseY >= filterBottom()) {
			return -1;
		}
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			if (mouseX >= filterColumnLeft(column) && mouseX < filterColumnRight(column)) {
				return column;
			}
		}
		return -1;
	}

	private List<FilterOption> visibleFilterOptions(int column) {
		String query = filterSearchTextField.getText().toLowerCase(Locale.ROOT);
		List<FilterOption> visible = filterOptions.get(column);
		if (ExplorersCompassConfig.showOnlyMatchingFilterOptions) {
			if (availableFilterOptions == null) {
				availableFilterOptions = FilterSelections.availableOptions(allowedStructureIDs,
						ExplorersCompass.structureIDsToSetIDs, ExplorersCompass.allowedStructureIDsToDimensionIDs,
						this::passesStructureVisibility);
			}
			visible = visible.stream().filter(option -> filterChecked(column, option) || switch (column) {
				case 0 -> availableFilterOptions.sources().contains(option.source);
				case 1 -> availableFilterOptions.dimensions().contains(option.id);
				default -> availableFilterOptions.sets().contains(option.id);
			}).toList();
		}
		if (!query.isEmpty()) {
			visible = visible.stream().filter(option -> StructureSearchQuery.matchesFilter(query, option.label,
					option.source != null ? option.source : option.id == null ? null : option.id.toString())).toList();
		}
		if (!ExplorersCompassConfig.checkedFiltersFirst) {
			return visible;
		}
		List<FilterOption> ordered = new ArrayList<>(visible.size());
		for (FilterOption option : visible) {
			if (filterChecked(column, option)) {
				ordered.add(option);
			}
		}
		for (FilterOption option : visible) {
			if (!filterChecked(column, option)) {
				ordered.add(option);
			}
		}
		return ordered;
	}

	private int filterSectionStart(int column, List<FilterOption> options) {
		if (!ExplorersCompassConfig.checkedFiltersFirst) {
			return -1;
		}
		int checkedCount = 0;
		for (FilterOption option : options) {
			if (filterChecked(column, option)) {
				checkedCount++;
			}
		}
		return checkedCount > 0 && checkedCount < options.size() ? checkedCount : -1;
	}

	private int filterRowY(int index, int sectionStart) {
		return filterRowsTop() + index * filterRowHeight() - visibleFilterScroll()
				+ (sectionStart >= 0 && index >= sectionStart ? FILTER_SECTION_GAP : 0);
	}

	private int visibleFilterScroll() {
		return (int) Math.floor(filterScroll);
	}

	private int filterOptionIndexAt(double mouseY, List<FilterOption> options, int sectionStart) {
		if (mouseY >= filterFullRowsBottom(sectionStart)) {
			return -1;
		}
		double offset = mouseY - filterRowsTop() + visibleFilterScroll();
		if (sectionStart >= 0) {
			int gapStart = sectionStart * filterRowHeight();
			if (offset >= gapStart && offset < gapStart + FILTER_SECTION_GAP) {
				return -1;
			}
			if (offset >= gapStart + FILTER_SECTION_GAP) {
				offset -= FILTER_SECTION_GAP;
			}
		}
		int index = (int) Math.floor(offset / filterRowHeight());
		return index >= 0 && index < options.size() ? index : -1;
	}

	private int filterMaxScroll() {
		int maxScroll = 0;
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			List<FilterOption> options = visibleFilterOptions(column);
			if (ExplorersCompassConfig.adjustRowsToFitView) {
				maxScroll = Math.max(maxScroll, (options.size() - filterVisibleRows()) * filterRowHeight());
			} else {
				int columnHeight = options.size() * filterRowHeight()
						+ (filterSectionStart(column, options) >= 0 ? FILTER_SECTION_GAP : 0);
				maxScroll = Math.max(maxScroll, columnHeight - (filterBottom() - filterRowsTop()));
			}
		}
		return Math.max(0, maxScroll);
	}

	private Scrollbar scrollbarFor(ScrollPanel panel) {
		int trackHeight = filterBottom() - filterTop();
		return switch (panel) {
			case FILTERS -> new Scrollbar(filterRight(), filterTop(), filterBottom(),
					Scrollbar.pixelThumbHeight(trackHeight, filterMaxScroll(), 32, 8), filterScroll, filterMaxScroll());
			case SETTINGS -> new Scrollbar(filterRight(), filterTop(), filterBottom(),
					Scrollbar.rowThumbHeight(trackHeight, settingsVisibleRows(), settingsRows.size()), settingsFirstRow, settingsMaxFirstRow());
			case HIDDEN -> new Scrollbar(filterRight(), filterTop(), filterBottom(),
					Scrollbar.rowThumbHeight(trackHeight, hiddenVisibleRows(), filteredHiddenStructures().size()), hiddenScroll, hiddenMaxScroll());
			case FOUND -> foundScrollbar();
			case SEARCH_FAILURE -> searchFailureScrollbar();
		};
	}

	private void rebuildFilterOptions() {
		availableFilterOptions = null;
		for (List<FilterOption> options : filterOptions) {
			options.clear();
		}
		Map<String, FilterOption> sources = new HashMap<>();
		Set<Identifier> dimensions = new TreeSet<>();
		Set<Identifier> sets = new TreeSet<>();
		for (Identifier structure : allowedStructureIDs) {
			String namespace = structure.getNamespace();
			sources.putIfAbsent(namespace, new FilterOption(StructureUtils.getStructureSource(structure), namespace, null, false));
			dimensions.addAll(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structure));
			Identifier set = ExplorersCompass.structureIDsToSetIDs.get(structure);
			if (set != null) {
				sets.add(set);
			}
		}
		showSourceColumn = sources.size() != 1 || !sources.containsKey("minecraft");
		if (!showSourceColumn && !FilterSelections.sources.isEmpty()) {
			FilterSelections.sources.clear();
			showNormalResults();
		}
		dimensions.add(world.getRegistryKey().getValue());
		filterOptions.get(0).addAll(sources.values());
		for (Identifier dimension : dimensions) {
			filterOptions.get(1).add(new FilterOption(StructureUtils.getDimensionName(dimension), null, dimension, false));
		}
		for (Identifier set : sets) {
			filterOptions.get(2).add(new FilterOption(StructureUtils.formatIdPath(set), null, set, false));
		}
		Collator collator = englishCollator();
		Comparator<FilterOption> alphabetical = Comparator.comparing((FilterOption option) -> option.label, collator)
				.thenComparing(option -> option.source != null ? option.source : option.id.toString());
		for (List<FilterOption> options : filterOptions) {
			options.sort(alphabetical);
		}
		filterOptions.get(0).sort(Comparator.comparing((FilterOption option) -> !"minecraft".equals(option.source)).thenComparing(alphabetical));
		filterOptions.get(1).add(0, new FilterOption(Text.literal("Current").getString(), null, world.getRegistryKey().getValue(), true));
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	private boolean filterChecked(int column, FilterOption option) {
		if (option.current) {
			return FilterSelections.isCurrentSelected();
		}
		return switch (column) {
			case 0 -> FilterSelections.sources.contains(option.source);
			case 1 -> FilterSelections.dimensions.contains(option.id);
			default -> FilterSelections.sets.contains(option.id);
		};
	}

	private void toggleFilter(int column, FilterOption option) {
		clearCurrentStructureReveal();
		if (column == 0) {
			toggleSet(FilterSelections.sources, option.source);
		} else if (column == 1) {
			if (option.current) {
				FilterSelections.toggleCurrent();
			} else {
				FilterSelections.toggleDimension(option.id);
			}
		} else {
			toggleSet(FilterSelections.sets, option.id);
		}
		showNormalResults();
		processSearchTerm();
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	boolean isSelectedOnly() {
		return selectedOnly;
	}

	public void showNormalResults() {
		selectedOnly = false;
	}

	private void clearFilters() {
		clearCurrentStructureReveal();
		FilterSelections.clear();
		showNormalResults();
		processSearchTerm();
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		updateSearchButtons();
	}

	private static <T> void toggleSet(Set<T> values, T value) {
		if (!values.add(value)) {
			values.remove(value);
		}
	}

	public void openEntryDetails(Identifier id) {
		if (sortMenuOpen) closeSortMenu();
		draggingStructureScrollbar = false;
		setDragging(false);
		setFocused(null);
		searchTextField.setFocused(false);
		entryDetails = new EntryDetailsPopup(client, id, () -> isStructureHidden(id),
				() -> toggleStructureHidden(id), this::closeEntryDetails);
	}

	private void closeEntryDetails() {
		entryDetails = null;
		finalizePendingHides();
		focusActiveSearchField();
	}

	public void openEntryFilters(Identifier id) {
		if (sortMenuOpen) closeSortMenu();
		entryFiltersId = id;
		entryFilterFirstRow = 0;
	}

	private List<EntryFilterRow> entryFilterRows() {
		if (entryFiltersId == null) return List.of();
		List<EntryFilterRow> rows = new ArrayList<>();
		if (showSourceColumn) {
			rows.add(new EntryFilterRow("Source:", StructureUtils.getStructureSource(entryFiltersId), 0,
					new FilterOption("", entryFiltersId.getNamespace(), null, false)));
		}
		for (Identifier dimension : ExplorersCompass.allowedStructureIDsToDimensionIDs.get(entryFiltersId)) {
			rows.add(new EntryFilterRow("Dimension:", StructureUtils.getDimensionName(dimension), 1,
					new FilterOption("", null, dimension, false)));
		}
		Identifier set = ExplorersCompass.structureIDsToSetIDs.get(entryFiltersId);
		if (set != null) {
			rows.add(new EntryFilterRow("Set:", StructureUtils.formatIdPath(set), 2,
					new FilterOption("", null, set, false)));
		}
		return rows;
	}

	private int entryFilterVisibleRows(int count) {
		return Math.min(count, Math.max(1, (height - 70) / ENTRY_FILTER_ROW_HEIGHT));
	}

	private int entryFilterWidth() { return Math.max(40, Math.min(LockedRequirementsView.WINDOW_WIDTH, width - 24)); }
	private int entryFilterX() { return (width - entryFilterWidth()) / 2; }
	private int entryFilterHeight(int count) { return 65 + entryFilterVisibleRows(count) * ENTRY_FILTER_ROW_HEIGHT; }
	private int entryFilterY(int count) { return (height - entryFilterHeight(count)) / 2; }

	private void renderEntryFilters(DrawContext context, int mouseX, int mouseY) {
		List<EntryFilterRow> rows = entryFilterRows();
		int x = entryFilterX(), w = entryFilterWidth(), h = entryFilterHeight(rows.size());
		int y = entryFilterY(rows.size()), bodyY = y + 30;
		int visible = entryFilterVisibleRows(rows.size());
		int backY = bodyY + visible * ENTRY_FILTER_ROW_HEIGHT + 10;
		int checkboxX = x + w - 19;
		int valueX = x + 12 + textRenderer.getWidth("Dimension:") + 8;
		String hoveredValue = null;
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 300);
		context.fill(0, 0, width, height, 0x88000000);
		context.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xff909090);
		context.fill(x, y, x + w, y + h, 0xff000000);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Filters"), x + w / 2, y + 10, 0xffffff);
		context.enableScissor(x + 8, bodyY, x + w - 8, bodyY + visible * ENTRY_FILTER_ROW_HEIGHT);
		for (int i = 0; i < visible; i++) {
			EntryFilterRow row = rows.get(entryFilterFirstRow + i);
			int rowY = bodyY + i * ENTRY_FILTER_ROW_HEIGHT;
			if (mouseX >= x + 8 && mouseX < x + w - 8 && mouseY >= rowY && mouseY < rowY + ENTRY_FILTER_ROW_HEIGHT)
				context.fill(x + 8, rowY, x + w - 8, rowY + ENTRY_FILTER_ROW_HEIGHT, 0xff242424);
			context.drawTextWithShadow(textRenderer, row.category(), x + 12, rowY + 6, 0xffffff);
			context.drawTextWithShadow(textRenderer,
					textRenderer.trimToWidth(row.value(), Math.max(1, checkboxX - valueX - 8)), valueX, rowY + 6, 0xffffff);
			if (textRenderer.getWidth(row.value()) > checkboxX - valueX - 8
					&& mouseX >= valueX && mouseX < checkboxX && mouseY >= rowY && mouseY < rowY + ENTRY_FILTER_ROW_HEIGHT)
				hoveredValue = row.value();
			drawFilterCheckbox(context, checkboxX, rowY + 5, filterChecked(row.column(), row.option()));
		}
		context.disableScissor();
		int buttonX = x + (w - 70) / 2;
		drawDialogButton(context, "Back", buttonX, backY, 70, mouseX, mouseY);
		context.getMatrices().pop();
		if (hoveredValue != null) context.drawTooltip(textRenderer, Text.literal(hoveredValue), mouseX, mouseY);
	}

	private boolean clickEntryFilters(double mouseX, double mouseY, int button) {
		List<EntryFilterRow> rows = entryFilterRows();
		int x = entryFilterX(), y = entryFilterY(rows.size()), w = entryFilterWidth();
		if (mouseX < x - 1 || mouseX >= x + w + 1 || mouseY < y - 1 || mouseY >= y + entryFilterHeight(rows.size()) + 1) {
			entryFiltersId = null;
			return true;
		}
		if (button != 0) return true;
		int index = (int) (mouseY - y - 30) / ENTRY_FILTER_ROW_HEIGHT;
		if (mouseY >= y + 30 && index >= 0 && index < entryFilterVisibleRows(rows.size())) {
			EntryFilterRow row = rows.get(entryFilterFirstRow + index);
			double scroll = selectionList.getScrollAmount();
			toggleFilter(row.column(), row.option());
			selectionList.setScrollAmount(scroll);
			if (!selectionList.containsStructure(entryFiltersId)) entryFiltersId = null;
		}
		return true;
	}

	private void drawFilterCheckbox(DrawContext context, int x, int y, boolean checked) {
		context.fill(x, y, x + FILTER_CHECKBOX_SIZE, y + FILTER_CHECKBOX_SIZE, 0xffb0b0b0);
		context.fill(x + 1, y + 1, x + FILTER_CHECKBOX_SIZE - 1, y + FILTER_CHECKBOX_SIZE - 1, 0xff161616);
		if (checked) {
			for (int pixel = 0; pixel < 5; pixel++) {
				context.fill(x + 2 + pixel, y + 2 + pixel, x + 3 + pixel, y + 3 + pixel, 0xffffffff);
				context.fill(x + 6 - pixel, y + 2 + pixel, x + 7 - pixel, y + 3 + pixel, 0xffffffff);
			}
		}
	}

	private void renderExcludeButton(DrawContext context, ButtonWidget button, String label, boolean checked, int mouseX, int mouseY) {
		if (!button.visible) return;
		drawFilterCheckbox(context,
				button.getX() + button.getWidth() - FILTER_CHECKBOX_SIZE - EXCLUDE_BUTTON_CONTENT_PADDING,
				button.getY() + (button.getHeight() - FILTER_CHECKBOX_SIZE) / 2, checked);
		int availableTextWidth = button.getWidth() - EXCLUDE_BUTTON_CONTENT_PADDING * 2
				- FILTER_CHECKBOX_SIZE - EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP;
		if (textRenderer.getWidth(label) > availableTextWidth && button.isHovered()) {
			context.drawTooltip(textRenderer, Text.literal(label), mouseX, mouseY);
		}
	}

	private void renderCloseIcon(DrawContext context, ButtonWidget button) {
		int x = button.getX() + (button.getWidth() - CLOSE_ICON_SIZE) / 2;
		int y = button.getY() + (button.getHeight() - CLOSE_ICON_SIZE) / 2;
		drawIconWithShadow(context, CLOSE_ICON, x, y, CLOSE_ICON_SIZE, button.active ? 1.0f : 144 / 255.0f);
	}

	private static void drawIconWithShadow(DrawContext context, Identifier icon, int x, int y, int size, float brightness) {
		float shadow = brightness * 0.25f;
		context.setShaderColor(shadow, shadow, shadow, 1.0f);
		context.drawTexture(icon, x + 1, y + 1, 0, 0, size, size, size, size);
		context.setShaderColor(brightness, brightness, brightness, 1.0f);
		context.drawTexture(icon, x, y, 0, 0, size, size, size, size);
		context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
	}

	private int settingsRowY(int row) {
		return filterTop() + (settingsRows.indexOf(row) - settingsFirstRow) * SETTINGS_ROW_HEIGHT;
	}

	private int settingsVisibleRows() {
		return Math.max(1, (filterBottom() - filterTop()) / SETTINGS_ROW_HEIGHT);
	}

	private int settingsMaxFirstRow() {
		return Math.max(0, settingsRows.size() - settingsVisibleRows());
	}

	private boolean isSettingsRowVisible(int row) {
		int index = settingsRows.indexOf(row);
		return index >= settingsFirstRow && index < settingsFirstRow + settingsVisibleRows();
	}

	private int settingsControlHeight() {
		return 20;
	}

	private int settingsControlX() {
		return settingsResetX() - SEARCH_BUTTON_GAP - SETTINGS_CONTROL_WIDTH;
	}

	private int settingsResetX() {
		return filterRight() - 10 - SMALL_BUTTON_SIZE;
	}

	private boolean isSettingsDivider(int row) {
		return SETTINGS_TOOLTIPS[row] == null;
	}

	private void renderSettingsDivider(DrawContext context, int row) {
		String title = SETTINGS_LABELS[row];
		int centerX = (filterLeft() + filterRight()) / 2;
		int centerY = settingsRowY(row) + SETTINGS_ROW_HEIGHT / 2;
		int halfTitleWidth = textRenderer.getWidth(title) / 2;
		int gap = 8;
		context.fill(filterLeft() + 10, centerY, centerX - halfTitleWidth - gap, centerY + 1, SETTINGS_DIVIDER_COLOR);
		context.fill(centerX + halfTitleWidth + gap, centerY, filterRight() - 10, centerY + 1, SETTINGS_DIVIDER_COLOR);
		context.drawText(textRenderer, title, centerX - halfTitleWidth,
				centerY - textRenderer.fontHeight / 2, SETTINGS_DIVIDER_COLOR, false);
	}

	private void renderSettingsPanel(DrawContext context) {
		context.fill(filterLeft(), filterTop(), filterRight(), filterBottom(),
				RenderUtils.blackWithOpacity(RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT));
		if (settingsRows.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, "No matching settings", (filterLeft() + filterRight()) / 2,
					filterTop() + 10, SETTINGS_DIVIDER_COLOR);
			return;
		}
		int lastVisibleRow = Math.min(settingsRows.size(), settingsFirstRow + settingsVisibleRows());
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		for (int index = settingsFirstRow; index < lastVisibleRow; index++) {
			int row = settingsRows.get(index);
			if (isSettingsDivider(row)) {
				renderSettingsDivider(context, row);
				continue;
			}
			String label = SETTINGS_LABELS[row];
			int labelY = settingsRowY(row) + (SETTINGS_ROW_HEIGHT - textRenderer.fontHeight) / 2;
			int textWidth = textRenderer.getWidth(label);
			if (textWidth <= labelWidth) {
				context.drawTextWithShadow(textRenderer, label, labelX, labelY, 0xffffff);
			} else {
				float scale = (float) labelWidth / textWidth;
				context.getMatrices().push();
				context.getMatrices().translate(labelX, labelY + (textRenderer.fontHeight * (1.0f - scale)) / 2.0f, 0);
				context.getMatrices().scale(scale, scale, 1.0f);
				context.drawTextWithShadow(textRenderer, label, 0, 0, 0xffffff);
				context.getMatrices().pop();
			}
		}
		String offset = Integer.toString(ExplorersCompassConfig.overlayLineOffset);
		float offsetScale = Math.min(1.0f, 46.0f / Math.max(1, textRenderer.getWidth(offset)));
		if (isSettingsRowVisible(SETTINGS_OFFSET_ROW)) {
			context.getMatrices().push();
			context.getMatrices().translate(settingsControlX() + SETTINGS_CONTROL_WIDTH / 2.0,
					settingsRowY(SETTINGS_OFFSET_ROW) + (SETTINGS_ROW_HEIGHT - textRenderer.fontHeight * offsetScale) / 2.0, 0);
			context.getMatrices().scale(offsetScale, offsetScale, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, offset, 0, 0, 0xffffff);
			context.getMatrices().pop();
		}
		scrollbarFor(ScrollPanel.SETTINGS).render(context);
	}

	private void renderSettingsTooltip(DrawContext context, int mouseX, int mouseY) {
		if (mouseY < filterTop() || mouseY >= filterBottom()) {
			return;
		}
		int rowIndex = settingsFirstRow + (mouseY - filterTop()) / SETTINGS_ROW_HEIGHT;
		if (rowIndex >= settingsRows.size() || rowIndex >= settingsFirstRow + settingsVisibleRows()) {
			return;
		}
		int row = settingsRows.get(rowIndex);
		if (isSettingsDivider(row)) return;
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		String label = SETTINGS_LABELS[row];
		int textWidth = textRenderer.getWidth(label);
		float scale = Math.min(1.0f, (float) labelWidth / Math.max(1, textWidth));
		double textTop = settingsRowY(row) + (SETTINGS_ROW_HEIGHT - textRenderer.fontHeight) / 2.0f
				+ textRenderer.fontHeight * (1.0f - scale) / 2.0f;
		if (mouseX < labelX || mouseX >= labelX + textWidth * scale
				|| mouseY < textTop || mouseY >= textTop + textRenderer.fontHeight * scale) {
			return;
		}
		List<Text> lines = new ArrayList<>();
		for (OrderedText line : textRenderer.wrapLines(Text.literal(SETTINGS_TOOLTIPS[row]), Math.min(240, width - 30))) {
			StringBuilder content = new StringBuilder();
			line.accept((index, style, codePoint) -> {
				content.appendCodePoint(codePoint);
				return true;
			});
			lines.add(Text.literal(content.toString()));
		}
		context.drawTooltip(textRenderer, lines, mouseX, mouseY);
	}

	private void updateSettingsButtonLabels() {
		chatOverlayButton.setMessage(onOff(ExplorersCompassConfig.displayWithChatOpen));
		hideSearchCostButton.setMessage(onOff(ExplorersCompassConfig.hideSearchCost));
		matchSearchWordsButton.setMessage(onOff(ExplorersCompassConfig.matchSearchWordsInAnyOrder));
		showOnlyMatchingFilterOptionsButton.setMessage(onOff(ExplorersCompassConfig.showOnlyMatchingFilterOptions));
		clearOnCloseButton.setMessage(onOff(ExplorersCompassConfig.clearFiltersOnClose));
		clearSearchBarOnCloseButton.setMessage(onOff(ExplorersCompassConfig.clearSearchBarOnClose));
		pauseGameWhenOpenButton.setMessage(onOff(ExplorersCompassConfig.pauseGameWhenOpen));
		creativeUnlocksAllButton.setMessage(onOff(ExplorersCompassConfig.creativeUnlocksAll));
		showTeleportOnlyInCreativeButton.setMessage(onOff(ExplorersCompassConfig.showTeleportOnlyInCreative));
		checkedFiltersFirstButton.setMessage(onOff(ExplorersCompassConfig.checkedFiltersFirst));
		closeOnFoundButton.setMessage(onOff(ExplorersCompassConfig.closeOnFound));
		adjustRowsToFitViewButton.setMessage(onOff(ExplorersCompassConfig.adjustRowsToFitView));
		overlaySideButton.setMessage(Text.literal(ExplorersCompassConfig.overlaySide == OverlaySide.LEFT ? "Left" : "Right"));
	}

	private void addSettingResetButton(int row, ClientSetting setting) {
		Text label = Text.literal("Restore default for " + SETTINGS_LABELS[row]);
		var button = new TransparentButton(settingsResetX(), settingsRowY(row), SMALL_BUTTON_SIZE,
				settingsControlHeight(), label, ignored -> restoreSettings(setting::restoreDefault)) {
			@Override
			protected void renderLabel(DrawContext context, net.minecraft.client.font.TextRenderer renderer) {
				int x = getX() + (getWidth() - SETTINGS_RESET_ICON_SIZE) / 2;
				drawSettingsResetIcon(context, x, getY(), getHeight());
			}
		};
		button.setTooltip(Tooltip.of(label));
		settingResetButtons.put(row, addDrawableChild(button));
	}

	private static void drawSettingsResetIcon(DrawContext context, int x, int top, int height) {
		int y = top + (height - SETTINGS_RESET_ICON_SIZE) / 2;
		drawIconWithShadow(context, SETTINGS_RESET_ICON, x, y, SETTINGS_RESET_ICON_SIZE, 1.0f);
	}

	private void restoreSettings(Runnable reset) {
		boolean fittedRows = ExplorersCompassConfig.adjustRowsToFitView;
		double scroll = selectionList.getScrollAmount();
		reset.run();
		ExplorersCompassConfig.saveClient();
		updateSettingsButtonLabels();
		sortOrder.clear();
		for (String name : ExplorersCompassConfig.sortOrder) sortOrder.add(SortCriterion.valueOf(name));
		if (fittedRows != ExplorersCompassConfig.adjustRowsToFitView) {
			remove(selectionList);
			selectionList = new StructureSearchList(this, client, structureSelection, structureListX(), 40,
					width - structureListX() - StructureSearchList.SCROLLBAR_WIDTH, height - 50, 50);
		}
		processSearchTerm();
		selectionList.setScrollAmount(scroll);
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	private static Text onOff(boolean enabled) {
		return Text.literal(enabled ? "On" : "Off");
	}

	private void renderFilterPanel(DrawContext context, int mouseX, int mouseY) {
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		int right = filterRight();
		int bottom = filterBottom();
		int backgroundColor = RenderUtils.blackWithOpacity(RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT);
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			int columnLeft = filterColumnLeft(column);
			int columnRight = filterColumnRight(column);
			context.fill(columnLeft, filterTop(), columnRight, filterRowsTop() - FILTER_HEADER_GAP, backgroundColor);
			List<FilterOption> options = visibleFilterOptions(column);
			int sectionStart = filterSectionStart(column, options);
			if (sectionStart >= 0) {
				int gapY = filterRowsTop() + sectionStart * filterRowHeight() - visibleFilterScroll();
				context.fill(columnLeft, filterRowsTop(), columnRight, MathHelper.clamp(gapY, filterRowsTop(), bottom), backgroundColor);
				context.fill(columnLeft, MathHelper.clamp(gapY + FILTER_SECTION_GAP, filterRowsTop(), bottom), columnRight, bottom, backgroundColor);
			} else {
				context.fill(columnLeft, filterRowsTop(), columnRight, bottom, backgroundColor);
			}
		}
		String[] headings = {
			Text.literal("Source").getString(),
			Text.literal("Dimension").getString(),
			Text.literal("Set").getString()
		};
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			int columnLeft = filterColumnLeft(column);
			int columnRight = filterColumnRight(column);
			context.enableScissor(columnLeft + 2, filterHeaderTop() + 1, columnRight - 2, filterRowsTop() - FILTER_HEADER_GAP);
			String heading = textRenderer.trimToWidth(headings[column], columnRight - columnLeft - 6);
			context.drawCenteredTextWithShadow(textRenderer, heading, (columnLeft + columnRight) / 2, filterHeaderTop() + 6, 0xffffff);
			context.disableScissor();
			List<FilterOption> options = visibleFilterOptions(column);
			int sectionStart = filterSectionStart(column, options);
			int rowsBottom = filterFullRowsBottom(sectionStart);
			int rowHeight = filterRowHeight();
			context.enableScissor(columnLeft + 1, filterRowsTop(), columnRight - 1, rowsBottom);
			int checkboxX = columnRight - FILTER_CHECKBOX_SIZE - 7;
			for (int index = 0; index < options.size(); index++) {
				int rowY = filterRowY(index, sectionStart);
				if (rowY + rowHeight <= filterRowsTop() || rowY >= rowsBottom) {
					continue;
				}
				if (mouseX >= columnLeft && mouseX < columnRight && mouseY >= rowY && mouseY < rowY + rowHeight) {
					context.fill(columnLeft + 1, rowY, columnRight - 1, rowY + rowHeight, 0x55242424);
				}
				FilterOption option = options.get(index);
				drawFilterOptionLabel(context, option, columnLeft + 5, rowY, Math.max(0, checkboxX - columnLeft - 9));
				drawFilterCheckbox(context, checkboxX, rowY + (rowHeight - FILTER_CHECKBOX_SIZE) / 2, filterChecked(column, option));
			}
			context.disableScissor();
		}
		scrollbarFor(ScrollPanel.FILTERS).render(context);
	}

	private Text filterOptionText(FilterOption option, String label) {
		Text text = Text.literal(label);
		return option.current || "minecraft".equals(option.source) ? text.copy().formatted(Formatting.ITALIC) : text;
	}

	private Text currentDimensionSuffix(FilterOption option) {
		return Text.literal(" (" + StructureUtils.getDimensionName(option.id) + ")").formatted(Formatting.GRAY);
	}

	private void drawFilterOptionLabel(DrawContext context, FilterOption option, int x, int rowY, int availableWidth) {
		if (availableWidth <= 1) {
			return;
		}
		Text label = filterOptionText(option, option.label);
		int labelWidth = textRenderer.getWidth(label);
		if (option.current) {
			int y = rowY + (filterRowHeight() - textRenderer.fontHeight) / 2;
			context.drawTextWithShadow(textRenderer, label, x, y, 0xffffff);
			RenderUtils.drawTextToFit(context, textRenderer, currentDimensionSuffix(option),
					x + labelWidth, y, availableWidth - labelWidth, 0xffffff);
			return;
		}
		if (labelWidth <= availableWidth) {
			context.drawTextWithShadow(textRenderer, label, x, rowY + (filterRowHeight() - textRenderer.fontHeight) / 2, 0xffffff);
			return;
		}
		float scale = (availableWidth - 1.0f) / labelWidth;
		context.getMatrices().push();
		context.getMatrices().translate(x, rowY + (filterRowHeight() - textRenderer.fontHeight * scale) / 2.0f, 0);
		context.getMatrices().scale(scale, scale, 1.0f);
		context.drawTextWithShadow(textRenderer, label, 0, 0, 0xffffff);
		context.getMatrices().pop();
	}

	private void renderFilterTooltip(DrawContext context, int mouseX, int mouseY) {
		int column = filterColumnAt(mouseX, mouseY);
		if (column < 0 || mouseY < filterRowsTop()) {
			return;
		}
		List<FilterOption> options = visibleFilterOptions(column);
		int index = filterOptionIndexAt(mouseY, options, filterSectionStart(column, options));
		if (index < 0) {
			return;
		}
		int availableWidth = Math.max(0, filterColumnRight(column) - FILTER_CHECKBOX_SIZE - 7 - filterColumnLeft(column) - 9);
		FilterOption option = options.get(index);
		var label = filterOptionText(option, option.label).copy();
		if (option.current) label.append(currentDimensionSuffix(option));
		if (textRenderer.getWidth(label) > availableWidth) {
			context.drawTooltip(textRenderer, label, mouseX, mouseY);
		}
	}

	private ButtonPress buttonAt(double mouseX, double mouseY) {
		if (currentStructurePopup != null) return currentStructurePopup.buttonAt(mouseX, mouseY, width, height);
		if (entryDetails != null) return entryDetails.buttonAt(mouseX, mouseY, width, height);
		if (dialog == Dialog.DISABLED) return disabledView.buttonAt(mouseX, mouseY, width, height);
		if (entryFiltersId != null) {
			int count = entryFilterRows().size();
			int x = entryFilterX() + (entryFilterWidth() - 70) / 2;
			int y = entryFilterY(count) + 40 + entryFilterVisibleRows(count) * ENTRY_FILTER_ROW_HEIGHT;
			return ButtonPress.at(mouseX, mouseY, x, y, 70, 20, entryFiltersId, "back", () -> entryFiltersId = null);
		}
		if (sortMenuOpen) {
			return ButtonPress.at(mouseX, mouseY, sortMenuX() + (sortMenuWidth() - 70) / 2, sortBackY(), 70, 20,
					ButtonLayer.SORT, "back", this::closeSortMenu);
		}
		if (dialog == Dialog.LOCKED) return lockedRequirementsView.buttonAt(mouseX, mouseY, width, height, this::backLockedPopup, this::dismissDialog);
		if (dialog != Dialog.NONE) return dialogButtonAt(mouseX, mouseY);
		for (var child : children()) {
			if (child instanceof TransparentButton widget && widget.active && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
				return new ButtonPress(widget, "widget", widget::onPress);
			}
		}
		if (hiddenStructuresOpen) return hiddenRemoveButtonAt(mouseX, mouseY);
		return isStructureView() ? selectionList.buttonAt(mouseX, mouseY) : null;
	}

	private ButtonPress dialogButtonAt(double mouseX, double mouseY) {
		int x = dialogX(), y = dialogY(), w = dialogWidth();
		int backX = x + (w - 70) / 2;
		if (dialog == Dialog.FOUND) {
			int actionY = searchActionsY();
			if (showTeleportButton() && ButtonPress.contains(mouseX, mouseY, foundTeleportX(), actionY, foundActionWidth(), 20)) {
				return new ButtonPress(dialog, "teleport", this::teleport);
			}
			if (ButtonPress.contains(mouseX, mouseY, foundCloseX(), actionY, foundActionWidth(), 20)) {
				return new ButtonPress(dialog, "close", () -> client.setScreen(null));
			}
			return ButtonPress.at(mouseX, mouseY, foundBackX(), actionY, foundActionWidth(), 20, dialog, "back", this::dismissDialog);
		}
		if (isSearchFailureDialog()) {
			int actionY = searchFailureActionsY();
			int buttonWidth = searchFailureButtonWidth();
			if (showNotFoundHide() && ButtonPress.contains(mouseX, mouseY, searchFailureFirstButtonX(), actionY, buttonWidth, 20)) {
				return new ButtonPress(dialog, "hide", () -> toggleStructureHidden(searchedId));
			}
			return ButtonPress.at(mouseX, mouseY, searchFailureBackX(), actionY, buttonWidth, 20, dialog, "back", this::dismissDialog);
		}
		int backY = switch (dialog) {
			case SEARCHING -> searchConfirmed ? searchActionsY() : Integer.MIN_VALUE;
			case REJECTED -> y + 57;
			case MULTI_COST -> y + dialogHeight() - 26;
			default -> Integer.MIN_VALUE;
		};
		return backY == Integer.MIN_VALUE ? null : ButtonPress.at(mouseX, mouseY, backX, backY, 70, 20, dialog, "back", this::dismissDialog);
	}

	private ButtonPress hiddenRemoveButtonAt(double mouseX, double mouseY) {
		int firstY = filterTop() + 6;
		if (mouseY < firstY || mouseY >= filterBottom()) return null;
		int row = (int) (mouseY - firstY) / 25;
		if (row >= hiddenVisibleRows() || !ButtonPress.contains(mouseX, mouseY, width - 95, firstY + row * 25, 78, 20)) return null;
		List<String> visible = filteredHiddenStructures();
		int index = hiddenScroll + row;
		if (index >= visible.size()) return null;
		String id = visible.get(index);
		return new ButtonPress(ButtonLayer.HIDDEN, id, () -> {
			ExplorersCompassConfig.hiddenStructures.remove(id);
			ExplorersCompassConfig.saveHiddenStructures();
			hiddenScroll = MathHelper.clamp(hiddenScroll, 0, hiddenMaxScroll());
			processSearchTerm();
		});
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			ButtonPress hit = buttonAt(mouseX, mouseY);
			if (hit != null) {
				selectionList.resetDoubleClick();
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				hit.action().run();
				if (client.currentScreen == this) {
					refreshSelectedOnlyRows();
					updateSearchButtons();
					if (dialog == Dialog.NONE && entryFiltersId == null && !sortMenuOpen) focusActiveSearchField();
				}
				return true;
			}
		}
		if (selectionList != null && (button != 0 || !isStructureView() || dialog != Dialog.NONE
				|| entryDetails != null || entryFiltersId != null || sortMenuOpen || !selectionList.isMouseOver(mouseX, mouseY))) {
			selectionList.resetDoubleClick();
		}
		draggingStructureScrollbar = currentStructurePopup == null && entryDetails == null && button == 0 && dialog == Dialog.NONE
				&& entryFiltersId == null && !filtersOpen && !settingsOpen && !hiddenStructuresOpen && !sortMenuOpen
				&& selectionList != null && selectionList.isScrollbarAt(mouseX, mouseY);
		try {
			return handleMouseClicked(mouseX, mouseY, button);
		} finally {
			refreshSelectedOnlyRows();
			updateSearchButtons();
			// Minecraft routes drag events to the focused child. Keep the list focused
			// after grabbing its scrollbar instead of returning focus to search.
			if (client.currentScreen == this && entryDetails == null && dialog == Dialog.NONE && entryFiltersId == null && !sortMenuOpen && !draggingStructureScrollbar) focusActiveSearchField();
		}
	}

	private boolean handleMouseClicked(double mouseX, double mouseY, int button) {
		if (currentStructurePopup != null) { currentStructurePopup.click(mouseX, mouseY, button, width, height); return true; }
        if (dialog == Dialog.DISABLED) { disabledView.click(mouseX,mouseY,button,width,height); return true; }
		if (entryDetails != null) { entryDetails.click(mouseX, mouseY, button, width, height); return true; }
		if (entryFiltersId != null) return clickEntryFilters(mouseX, mouseY, button);
		if (sortMenuOpen) return clickSortMenu(mouseX, mouseY, button);
        if (dialog == Dialog.LOCKED) {
            if (lockedRequirementsView.click(mouseX, mouseY, button, width, height)) backLockedPopup();
            return true;
        }
        if (dialog == Dialog.SEARCHING && !searchConfirmed) return true;
        if (dialog != Dialog.NONE) {
            if (dialog == Dialog.FOUND && button == 0 && beginScrollbarDrag(ScrollPanel.FOUND, mouseX, mouseY)) return true;
            if (isSearchFailureDialog() && button == 0 && beginScrollbarDrag(ScrollPanel.SEARCH_FAILURE, mouseX, mouseY)) return true;
            if (mouseX < dialogX() - 1 || mouseX >= dialogX() + dialogWidth() + 1
                    || mouseY < dialogY() - 1 || mouseY >= dialogY() + dialogHeight() + 1) {
                dismissDialog();
                return true;
            }
            if (dialog == Dialog.WRONG_DIMENSION || dialog == Dialog.INSUFFICIENT_COST) { dismissDialog(); return true; }
            return true;
        }
		if (button == 1) {
			TransparentTextField field = searchFieldAt(mouseX, mouseY);
			if (field != null) {
				super.mouseClicked(mouseX, mouseY, 0);
				field.setText("");
				field.setFocused(true);
				return true;
			}
		}
        if (hiddenStructuresOpen) {
            if (button == 0 && beginScrollbarDrag(ScrollPanel.HIDDEN, mouseX, mouseY)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
		if (settingsOpen && button == 0 && beginScrollbarDrag(ScrollPanel.SETTINGS, mouseX, mouseY)) return true;
		if (filtersOpen && button == 0) {
			if (beginScrollbarDrag(ScrollPanel.FILTERS, mouseX, mouseY)) return true;
			int column = filterColumnAt(mouseX, mouseY);
			if (column >= 0 && mouseY >= filterRowsTop()) {
				List<FilterOption> options = visibleFilterOptions(column);
				int index = filterOptionIndexAt(mouseY, options, filterSectionStart(column, options));
				if (index >= 0) {
					toggleFilter(column, options.get(index));
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickSortMenu(double mouseX, double mouseY, int button) {
		int x = sortMenuX(), y = sortMenuY(), w = sortMenuWidth();
		if (mouseX < x - 1 || mouseX >= x + w + 1 || mouseY < y - 1 || mouseY >= y + sortMenuHeight() + 1) {
			closeSortMenu();
			return true;
		}
		if (button != 0) return true;
		int row = sortRowAt(mouseX, mouseY);
		if (row >= 0) draggedSortRow = row;
		return true;
	}

	private TransparentTextField searchFieldAt(double mouseX, double mouseY) {
		for (TransparentTextField field : new TransparentTextField[] {searchTextField, filterSearchTextField, settingsSearchTextField, hiddenSearchTextField}) {
			if (field != null && field.isVisible() && mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
					&& mouseY >= field.getY() && mouseY < field.getY() + field.getHeight()) return field;
		}
		return null;
	}

	private TransparentTextField activeSearchField() {
		if (hiddenStructuresOpen) return hiddenSearchTextField;
		if (filtersOpen) return filterSearchTextField;
		return settingsOpen ? settingsSearchTextField : searchTextField;
	}

	private void focusActiveSearchField() {
		if (currentStructurePopup != null || entryDetails != null) return;
		TransparentTextField active = activeSearchField();
		for (TransparentTextField field : new TransparentTextField[] {searchTextField, filterSearchTextField, settingsSearchTextField, hiddenSearchTextField}) {
			if (field != null && field != active) field.setFocused(false);
		}
		if (active == null) {
			setFocused(null);
		} else if (getFocused() != active || !active.isFocused()) {
			setFocused(active);
			active.setFocused(true);
		}
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dialog == Dialog.DISABLED) { disabledView.drag(mouseY, width, height); return true; }
		if (currentStructurePopup != null) { currentStructurePopup.drag(mouseY, width, height); return true; }
		if (entryDetails != null) { entryDetails.drag(mouseY, width, height); return true; }
		if (entryFiltersId != null) return true;
        if (dialog == Dialog.LOCKED) { lockedRequirementsView.drag(mouseY); return true; }
		if (draggedScrollbar != null && button == 0) {
			moveScrollbar(mouseY);
			return true;
		}
		if (draggedSortRow >= 0 && button == 0) {
			if (mouseX >= sortMenuX() && mouseX < sortMenuX() + sortMenuWidth()) {
				int targetRow = MathHelper.clamp((int) Math.floor((mouseY - sortRowsY()) / SORT_ROW_HEIGHT), 0, sortOrder.size() - 1);
				if (targetRow != draggedSortRow) {
					SortCriterion criterion = sortOrder.remove(draggedSortRow);
					sortOrder.add(targetRow, criterion);
					draggedSortRow = targetRow;
					sortOrderChangedDuringDrag = true;
				}
			}
			return true;
		}
		if (sortMenuOpen) return true;
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dialog == Dialog.DISABLED) { disabledView.release(); return true; }
		if (currentStructurePopup != null) { currentStructurePopup.release(); return true; }
		if (entryDetails != null) { entryDetails.release(); return true; }
		if (entryFiltersId != null) return true;
        if (draggingStructureScrollbar && button == 0) {
            draggingStructureScrollbar = false;
            super.mouseReleased(mouseX, mouseY, button);
            if (client.currentScreen == this && dialog == Dialog.NONE) focusActiveSearchField();
            return true;
        }
        if (dialog == Dialog.LOCKED) { lockedRequirementsView.release(); return true; }
		if (draggedScrollbar != null && button == 0) {
			draggedScrollbar = null;
			return true;
		}
		if (draggedSortRow >= 0 && button == 0) {
			commitSortOrder();
			draggedSortRow = -1;
			return true;
		}
		if (sortMenuOpen) return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int par1, int par2, int par3) {
		if (currentStructurePopup != null) {
			if (par1 == 256) closeCurrentStructurePopup();
			return true;
		}
		if (entryDetails != null) {
			if (par1 == 256) closeEntryDetails();
			return true;
		}
		if (entryFiltersId != null) {
			if (par1 == 256) entryFiltersId = null;
			return true;
		}
		if (sortMenuOpen) {
			if (par1 == 256) closeSortMenu();
			return true;
		}
        if (dialog != Dialog.NONE) {
            if (par1 == 256) {
                if (dialog != Dialog.LOCKED) dismissDialog();
                else if (!lockedRequirementsView.closeQuestPopup()) backLockedPopup();
            }
            else if (dialog == Dialog.LOCKED) lockedRequirementsView.keyPressed(par1);
            else if (dialog == Dialog.DISABLED && (par1 == 264 || par1 == 265 || par1 == 266 || par1 == 267))
                disabledView.scroll(par1 == 264 || par1 == 267 ? -1 : 1);
            else if (isSearchFailureDialog() && (par1 == 264 || par1 == 265 || par1 == 266 || par1 == 267))
                scrollSearchFailure(par1 == 264 || par1 == 267 ? -1 : 1);
            return true;
        }
		if (isStructureView()) {
			if (isSelectAll(par1)) {
				selectionList.selectAllMatches();
				refreshSelectedOnlyRows();
				updateSearchButtons();
				return true;
			}
			if (par1 == 264 || par1 == 265) {
				selectionList.selectRelative(par1 == 264 ? 1 : -1);
				refreshSelectedOnlyRows();
				updateSearchButtons();
				focusActiveSearchField();
				return true;
			}
			if (par1 == 257 || par1 == 335) {
				if (structureSelection.isEmpty()) {
					StructureSearchEntry entry = selectionList.soleSearchableEntry();
					if (entry != null) entry.searchForStructure();
				} else {
					searchSelected();
				}
				if (dialog == Dialog.NONE) focusActiveSearchField();
				return true;
			}
		}
        if (hiddenStructuresOpen && par1 == 256) {
            leaveHiddenStructures();
            return true;
        }
		if (settingsOpen && par1 == 256) {
			setSettingsOpen(false);
			return true;
		}
		if (filtersOpen && par1 == 256) {
			setFiltersOpen(false);
			return true;
		}
		boolean ret = super.keyPressed(par1, par2, par3);
		focusActiveSearchField();
		return ret;
	}

	@Override
	public boolean charTyped(char typedChar, int keyCode) {
		if (currentStructurePopup != null || entryDetails != null || dialog != Dialog.NONE || entryFiltersId != null || sortMenuOpen) return true;
		boolean ret = super.charTyped(typedChar, keyCode);
		focusActiveSearchField();
		return ret;
	}

	private boolean beginScrollbarDrag(ScrollPanel panel, double mouseX, double mouseY) {
		Scrollbar scrollbar = scrollbarFor(panel);
		if (!scrollbar.contains(mouseX, mouseY)) return false;
		int padding = panel == ScrollPanel.SETTINGS ? 0 : Scrollbar.GRAB_PADDING;
		boolean jump = scrollbar.outsideThumb(mouseY, padding);
		scrollbarGrabOffset = scrollbar.grabOffset(mouseY, padding);
		draggedScrollbar = panel;
		if (jump) moveScrollbar(mouseY);
		return true;
	}

	private void moveScrollbar(double mouseY) {
		if (draggedScrollbar == null) return;
		double offset = scrollbarFor(draggedScrollbar).offsetForMouse(mouseY, scrollbarGrabOffset);
		switch (draggedScrollbar) {
			case FILTERS -> filterScroll = offset;
			case SETTINGS -> {
				settingsFirstRow = (int)Math.round(offset);
				updateViewButtons();
			}
			case HIDDEN -> hiddenScroll = (int)Math.round(offset);
			case FOUND -> foundScroll = offset;
			case SEARCH_FAILURE -> searchFailureScroll = offset;
		}
	}

	private void setFiltersOpen(boolean open) {
		if (filtersOpen == open) {
			return;
		}
		if (sortMenuOpen) {
			closeSortMenu();
		}
		entryFiltersId = null;
		filtersOpen = open;
		if (open) {
			pendingHiddenStructures.clear();
			availableFilterOptions = null;
		}
		draggedScrollbar = null;
		searchTextField.setVisible(isStructureView());
		searchTextField.setFocused(false);
		filterSearchTextField.setVisible(open);
		filterSearchTextField.setFocused(false);
		if (open) {
			remove(selectionList);
		} else {
			addDrawableChild(selectionList);
			processSearchTerm();
		}
		updateViewButtons();
		focusActiveSearchField();
	}

	private void setSettingsOpen(boolean open) {
		if (settingsOpen == open) {
			return;
		}
		if (sortMenuOpen) {
			closeSortMenu();
		}
		entryFiltersId = null;
		settingsOpen = open;
		if (open) {
			pendingHiddenStructures.clear();
		}
		draggedScrollbar = null;
		searchTextField.setVisible(isStructureView());
		searchTextField.setFocused(false);
		if (open) {
			remove(selectionList);
		} else {
			addDrawableChild(selectionList);
			processSearchTerm();
		}
		updateViewButtons();
		focusActiveSearchField();
	}

	private boolean isStructureView() {
		return !filtersOpen && !settingsOpen && !hiddenStructuresOpen;
	}

    private boolean beginSearch(Identifier id) {
        if (dialog != Dialog.NONE || id == null) return false;
        searchedId = id;
        searchedListed = false;
        searchedExcludeVisited = ExplorersCompass.canExcludeVisited && FilterSelections.excludeVisited();
        listedDisabledCount = 0;
        listedSkippedCount = 0;
        activeRequestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        searchRadius = 0;
        searchConfirmed = false;
        dialog = Dialog.SEARCHING;
        return true;
    }

    public void searchForStructure(Identifier structureID) {
		if (isHiddenFromStructureList(structureID)) return;
        boolean started = beginSearch(structureID);
		finalizePendingHides();
        if (started) ClientPlayNetworking.send(SearchPacket.ID, new SearchPacket(structureID, false, searchedExcludeVisited, ExplorersCompassConfig.creativeUnlocksAll, activeRequestId));
    }

    private void searchSelected() {
        if (dialog != Dialog.NONE) return;
        finalizePendingHides();
        List<Identifier> selected = structureSelection.ids();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) {
            searchForStructure(selected.get(0));
            return;
        }
        searchedListed = true;
        searchedExcludeVisited = ExplorersCompass.canExcludeVisited && FilterSelections.excludeVisited();
        searchedId = null;
        listedSelectedCount = selected.size();
        listedSkippedCount = 0;
        listedDisabledCount = 0;
        activeRequestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        searchRadius = 0;
        searchConfirmed = false;
        dialog = Dialog.SEARCHING;
        ClientPlayNetworking.send(SearchListedPacket.ID, new SearchListedPacket(selected, searchedExcludeVisited, ExplorersCompassConfig.creativeUnlocksAll, activeRequestId));
    }

    public void onSearchAvailability(io.github.enriquemetner.explorerscompass.network.SearchAvailabilityPacket.Response response) {
        if (!response.appliesTo(activeRequestId,dialog == Dialog.SEARCHING)) return;
        listedSkippedCount = response.wrongDimension();
        listedDisabledCount = response.disabled().size();
        if (searchedListed && response.isInvalidSelection(listedSelectedCount)) {
            searchFailureScroll = 0;
            searchFailureWrapWidth = 0;
            dialog = Dialog.INVALID_SEARCH;
        } else if (response.eligible() == 0 && !response.disabled().isEmpty()) {
            dialog = Dialog.DISABLED;
            disabledView = new DisabledStructureView(textRenderer,response.disabled(),!searchedListed,
                    this::dismissDialog,() -> toggleStructureHidden(response.disabled().get(0).structure()),
                    () -> isStructureHidden(response.disabled().get(0).structure()),this::navigateToReplacement);
        }
    }
    private void requestCurrentStructure() {
        currentStructureRequest = UUID.randomUUID();
        var packet = PacketByteBufs.create();
        packet.writeUuid(currentStructureRequest);
        ClientPlayNetworking.send(CurrentStructurePacket.ID, packet);
    }

    public void onCurrentStructure(UUID request, List<Identifier> matches) {
        if (!request.equals(currentStructureRequest)) return;
        currentStructureRequest = null;
        if (client.world != world || !isStructureView() || dialog != Dialog.NONE
                || entryDetails != null || entryFiltersId != null || sortMenuOpen) return;
        List<Identifier> available = matches.stream().filter(allowedStructureIDs::contains).toList();
        if (available.size() == 1) selectCurrentStructure(available.get(0));
        else currentStructurePopup = new CurrentStructurePopup(textRenderer, available, searchDialogHeight(foundRowsHeight(4)),
                this::selectCurrentStructure, this::closeCurrentStructurePopup);
    }

    private void closeCurrentStructurePopup() {
        currentStructurePopup = null;
        focusActiveSearchField();
    }

    private void selectCurrentStructure(Identifier id) {
        closeCurrentStructurePopup();
        clearCurrentStructureReveal();
        structureSelection.clear();
        searchTextField.setText(StructureUtils.getStructureName(id));
        revealedStructure = id;
        focusNavigationTarget(id);
        updateSearchButtons();
    }

    private void clearCurrentStructureReveal() {
        if (revealedStructure == null) return;
        if (revealedStructure.equals(navigationTarget)) navigationTarget = null;
        revealedStructure = null;
    }

    boolean isHiddenFromStructureList(Identifier id) {
        return !id.equals(revealedStructure) && isStructureHidden(id);
    }

    public void selectStructureAfterSync(Identifier id) {
        selectionAfterSync = id;
        structureSelection.clear();
        updateSearchButtons();
    }

    private void selectSyncedStructure(Identifier id) {
        if (ClientPacketHandlers.excludedStructures.contains(id)) {
            showExcluded(id, ClientPacketHandlers.whitelistActive);
            return;
        }
        if (isStructureHidden(id)) {
            openHiddenStructures(id);
            return;
        }
        if (!allowedStructureIDs.contains(id)) {
            client.getToastManager().add(net.minecraft.client.toast.SystemToast.create(client,
                    net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Open in Compass"), Text.literal("This structure is unavailable in the compass")));
            return;
        }
        focusNavigationTarget(id);
    }

    private void navigateToReplacement(Identifier id) {
        dismissDialog();
        focusNavigationTarget(id);
    }

    private void focusNavigationTarget(Identifier id) {
        navigationTarget = id;
        showNormalResults();
        searchTextField.setText(StructureUtils.getStructureName(id));
        processSearchTerm();
        selectionList.focusStructure(id);
    }

    public void onSearchProgress(int requestId, int radius) {
        if (dialog != Dialog.SEARCHING || activeRequestId != requestId) return;
        if (!searchConfirmed) {
            searchConfirmed = true;
            dialogStarted = System.currentTimeMillis();
        }
        searchRadius = Math.max(searchRadius, radius);
    }

    public void onSearchResult(int requestId, int result, int radius, Identifier resultId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, Set<Identifier> validBiomes, Boolean visited, RequirementNode requirements, int balance) {
        if (dialog != Dialog.SEARCHING || activeRequestId != requestId) return;
        if (result == SearchResultPacket.FOUND) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 1.0F));
            if (ExplorersCompassConfig.closeOnFound) {
                successfulClose = true;
                client.setScreen(null);
            } else {
                foundStructureId = resultId;
                this.foundX = foundX;
                this.foundZ = foundZ;
                showFoundCoordinates = displayCoordinates;
                showFoundDistance = displayDistance;
                foundBiomes = sortedBiomeNames(biomes, validBiomes);
                foundVisited = visited;
                foundScroll = 0;
				double scrollAmount = selectionList.getScrollAmount();
				selectionList.refreshList();
				selectionList.setScrollAmount(scrollAmount);
                dialog = Dialog.FOUND;
            }
        } else if (result == SearchResultPacket.NOT_FOUND) {
            failedRadius = radius;
            searchFailureScroll = 0;
            searchFailureWrapWidth = 0;
            notFoundCompassUpdated = System.nanoTime();
            notFoundCompassAngle = 0.0D;
            notFoundCompassSpeed = 0.0D;
            dialog = Dialog.NOT_FOUND;
        } else if (result == SearchResultPacket.BLACKLISTED || result == SearchResultPacket.NOT_WHITELISTED) {
            showExcluded(resultId, result == SearchResultPacket.NOT_WHITELISTED);
        } else if (result == SearchResultPacket.LOCKED) {
            showLockedRequirements(searchedId, requirements);
        } else if (result == SearchResultPacket.WRONG_DIMENSION) {
            showWrongDimension();
        } else if (result == SearchResultPacket.INSUFFICIENT_COST || result == SearchResultPacket.MULTI_COST) {
            showInsufficientCost(radius, balance, result == SearchResultPacket.MULTI_COST);
        } else {
            dialog = Dialog.REJECTED;
        }
    }

    private void showInsufficientCost(int cost, int balance, boolean multiple) {
        insufficientCost = cost;
        availableEnergy = balance;
        dialog = multiple ? Dialog.MULTI_COST : Dialog.INSUFFICIENT_COST;
        dialogStarted = System.currentTimeMillis();
    }

    private void showWrongDimension() {
        dialog = Dialog.WRONG_DIMENSION;
        dialogStarted = System.currentTimeMillis();
    }

	private void dismissDialog() {
        lockedHistory.clear();
        if (dialog == Dialog.DISABLED || dialog == Dialog.NOT_FOUND) {
            finalizePendingHides();
            disabledView = null;
        }
        if (draggedScrollbar == ScrollPanel.SEARCH_FAILURE || draggedScrollbar == ScrollPanel.FOUND) draggedScrollbar = null;
        if (dialog == Dialog.SEARCHING && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(activeRequestId));
        }
		dialog = Dialog.NONE;
		lockedRequirementsView = null;
		lockedRequirementsId = null;
		focusActiveSearchField();
    }

    private int dialogWidth() {
        if (dialog == Dialog.WRONG_DIMENSION) {
            return Math.min(width - 20, textRenderer.getWidth(Text.literal("You're In The Wrong Dimension")) + 12);
        }
        if (dialog == Dialog.INSUFFICIENT_COST || dialog == Dialog.MULTI_COST) {
            int contentWidth = Math.max(textRenderer.getWidth(costTitle()), costLineWidth(costLabel(), insufficientCost, costSuffix(insufficientCost)));
            contentWidth = Math.max(contentWidth, costLineWidth(balanceLabel(), availableEnergy, costSuffix(availableEnergy)));
            if (dialog == Dialog.MULTI_COST) contentWidth = Math.max(contentWidth, 205);
            return Math.min(width - 20, contentWidth + 16);
        }
        if (isSearchFailureDialog()) return Math.min(width - 20, 340);
        if (dialog == Dialog.FOUND || dialog == Dialog.SEARCHING) {
            int locationWidth = Math.max(textRenderer.getWidth("Coordinates:"), textRenderer.getWidth("Distance: 99999"));
            int detailsWidth = 36 + textRenderer.getWidth("Source: Repurposed Structures")
                    + locationWidth;
            return Math.min(width - 20, Math.max(SEARCH_DIALOG_WIDTH, detailsWidth));
        }
        return Math.min(width - 20, SEARCH_DIALOG_WIDTH);
    }
    private void showExcluded(Identifier id, boolean whitelistActive) {
        excludedByWhitelist = whitelistActive;
        excludedStructure = id;
        searchFailureScroll = 0;
        searchFailureWrapWidth = 0;
        dialog = Dialog.EXCLUDED;
    }

    private boolean isSearchFailureDialog() {
        return dialog == Dialog.NOT_FOUND || dialog == Dialog.INVALID_SEARCH || dialog == Dialog.EXCLUDED;
    }

    private boolean showNotFoundHide() { return dialog == Dialog.NOT_FOUND && !searchedListed && searchedId != null; }
    private int searchFailureButtonWidth() {
        int available = dialogWidth() - 24;
        return Math.max(1, Math.min(70, showNotFoundHide() ? (available - NOT_FOUND_BUTTON_GAP) / 2 : available));
    }
    private int searchFailureFirstButtonX() {
        int total = showNotFoundHide() ? 2 * searchFailureButtonWidth() + NOT_FOUND_BUTTON_GAP : searchFailureButtonWidth();
        return dialogX() + (dialogWidth() - total) / 2;
    }
    private int searchFailureBackX() {
        return searchFailureFirstButtonX() + (showNotFoundHide() ? searchFailureButtonWidth() + NOT_FOUND_BUTTON_GAP : 0);
    }
    private int searchFailureActionsY() { return dialogY() + dialogHeight() - 25; }
    private int searchFailureTextOffset() {
        if (dialog == Dialog.INVALID_SEARCH || dialog == Dialog.EXCLUDED) return 12 + textRenderer.fontHeight;
        return height >= 90 ? 32 : 6;
    }
    private int searchFailureTextHeight() { return Math.max(0, dialogHeight() - searchFailureTextOffset() - 30); }
    private int searchFailureContentHeight() { return searchFailureLines().size() * textRenderer.fontHeight; }
    private int searchFailureMaxScroll() { return Math.max(0, searchFailureContentHeight() - searchFailureTextHeight()); }

    private List<SearchFailureLine> searchFailureLines() {
        int wrapWidth = Math.max(1, dialogWidth() - 24 - SCROLLBAR_WIDTH);
        if (wrapWidth != searchFailureWrapWidth) {
            List<SearchFailureLine> lines = new ArrayList<>();
            if (dialog == Dialog.EXCLUDED) {
                addSearchFailureLines(lines, Text.literal(StructureUtils.getStructureName(excludedStructure)), wrapWidth, false);
                addSearchFailureLines(lines, Text.literal(excludedStructure.toString()).formatted(Formatting.GRAY), wrapWidth, false);
                lines.add(new SearchFailureLine(OrderedText.EMPTY, false));
                if (excludedByWhitelist) {
                    addSearchFailureLines(lines, Text.literal("This structure isn’t on the server’s whitelist, so it doesn’t appear in the compass list and can’t be searched."), wrapWidth, false);
                } else {
                    addSearchFailureLines(lines, Text.literal("The server has blacklisted this structure, so it doesn’t appear in the compass list and can’t be searched"), wrapWidth, false);
                    lines.add(new SearchFailureLine(OrderedText.EMPTY, false));
                    addSearchFailureLines(lines, Text.literal("Possible reasons:"), wrapWidth, false);
                    addSearchFailureBullet(lines, "The structure is disabled", wrapWidth);
                    addSearchFailureBullet(lines, "The server or modpack creator left it out to keep the list focused on useful destinations", wrapWidth);
                    addSearchFailureBullet(lines, "The creator intended players to find it another way", wrapWidth);
                    addSearchFailureBullet(lines, "The creator may have another reason for excluding it", wrapWidth);
                }
            } else if (dialog == Dialog.INVALID_SEARCH) {
                addSearchFailureLines(lines, Text.literal("None of your selected structures are valid for searching."), wrapWidth, false);
                lines.add(new SearchFailureLine(OrderedText.EMPTY, false));
                addSearchFailureLines(lines, Text.literal("Make sure at least one valid structure is selected by:"), wrapWidth, false);
                addSearchFailureLines(lines, Text.literal("• going to the correct dimension of the selected structures"), wrapWidth, false);
                addSearchFailureLines(lines, Text.literal("• selecting structures for the dimension you're currently in"), wrapWidth, false);
                addSearchFailureLines(lines, Text.literal("• selecting structures which aren't disabled"), wrapWidth, false);
            } else {
                addSearchFailureLines(lines, Text.literal("Couldn't locate structure within " + failedRadius + " blocks"), wrapWidth, true);
                lines.add(new SearchFailureLine(OrderedText.EMPTY, false));
                addSearchFailureLines(lines, Text.literal("Possible reasons:"), wrapWidth, false);
                if (searchedExcludeVisited) {
                    addSearchFailureLines(lines, Text.literal("• ").append(Text.literal("Exclude Visited").formatted(Formatting.ITALIC))
                            .append(" is on, and every match within the search radius has already been visited"), wrapWidth, false);
                }
                addSearchFailureLines(lines, Text.literal("• Matching structures are very rare"), wrapWidth, false);
                addSearchFailureLines(lines, Text.literal("• The configured search radius may be too small to reach a match"), wrapWidth, false);
                addSearchFailureLines(lines, Text.literal("• Structure generation is disabled in a way the compass can't detect"), wrapWidth, false);
                if (searchedListed) {
                    lines.add(new SearchFailureLine(OrderedText.EMPTY, false));
                    addSearchFailureLines(lines, Text.literal(listedSelectedCount + " structures selected"), wrapWidth, true);
                    if (listedSkippedCount > 0 || listedDisabledCount > 0) {
                        addSearchFailureLines(lines, wrongDimensionCountText(), wrapWidth, true);
                    }
                }
            }
            searchFailureLines = lines;
            searchFailureWrapWidth = wrapWidth;
        }
        return searchFailureLines;
    }

    private void addSearchFailureLines(List<SearchFailureLine> lines, Text text, int wrapWidth, boolean centered) {
        for (OrderedText line : textRenderer.wrapLines(text, wrapWidth)) lines.add(new SearchFailureLine(line, centered));
    }

    private void addSearchFailureBullet(List<SearchFailureLine> lines, String text, int wrapWidth) {
        int indent = textRenderer.getWidth("• ");
        var wrapped = textRenderer.wrapLines(Text.literal(text), Math.max(1, wrapWidth - indent));
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new SearchFailureLine(wrapped.get(i), false, indent, i == 0));
        }
    }

    private void scrollSearchFailure(double amount) {
        searchFailureScroll = Scrollbar.scrollPixels(searchFailureScroll, amount, textRenderer.fontHeight, searchFailureMaxScroll());
    }

    private Scrollbar searchFailureScrollbar() {
        int top = dialogY() + searchFailureTextOffset();
        int textHeight = searchFailureTextHeight();
        int maxScroll = textHeight > 0 ? searchFailureMaxScroll() : 0;
        return new Scrollbar(dialogX() + dialogWidth() - 12 - SCROLLBAR_WIDTH, top, top + textHeight,
                Scrollbar.pixelThumbHeight(textHeight, maxScroll, 12, 0), searchFailureScroll, maxScroll);
    }

    private int foundActionWidth() {
        int count = showTeleportButton() ? 3 : 2;
        return Math.max(1, Math.min(FOUND_ACTION_BUTTON_WIDTH,
                (dialogWidth() - 12 - FOUND_ACTION_BUTTON_GAP * (count - 1)) / count));
    }

    private int foundTeleportX() {
        int count = showTeleportButton() ? 3 : 2;
        return dialogX() + (dialogWidth() - foundActionWidth() * count - FOUND_ACTION_BUTTON_GAP * (count - 1)) / 2;
    }

    private int foundBackX() { return foundTeleportX() + (showTeleportButton() ? foundActionWidth() + FOUND_ACTION_BUTTON_GAP : 0); }
    private int foundCloseX() { return foundBackX() + foundActionWidth() + FOUND_ACTION_BUTTON_GAP; }
    private int searchActionsY() { return dialogY() + dialogHeight() - 25; }

    private int foundContentHeight() {
        int locationRows = (showFoundDistance ? 1 : 0) + (showFoundCoordinates ? 2 : 0) + (foundVisited != null ? 1 : 0);
        int rows = Math.max(3 + foundBiomes.size(), locationRows);
        return foundRowsHeight(rows);
    }

    private int foundRowsHeight(int rows) {
        return (rows - 1) * FOUND_LINE_STEP + textRenderer.fontHeight;
    }

    private int searchDialogHeight(int contentHeight) {
        return Math.min(Math.max(0, height - 10), Math.max(SEARCH_DIALOG_HEIGHT, FOUND_DETAILS_TOP + contentHeight + 30));
    }

    private int foundViewportHeight() {
        return Math.max(0, dialogHeight() - 30 - FOUND_DETAILS_TOP);
    }

    private int foundMaxScroll() {
        return Math.max(0, foundContentHeight() - foundViewportHeight());
    }

    private Scrollbar foundScrollbar() {
        int top = dialogY() + FOUND_DETAILS_TOP;
        int viewport = foundViewportHeight();
        int maxScroll = viewport > 0 ? foundMaxScroll() : 0;
        return new Scrollbar(dialogX() + dialogWidth() - 1 - SCROLLBAR_WIDTH, top, top + viewport,
                Scrollbar.pixelThumbHeight(viewport, maxScroll, 12, 0), foundScroll, maxScroll);
    }

    private Text wrongDimensionCountText() {
        return Text.literal("Wrong dimension: " + listedSkippedCount + "   Disabled: " + listedDisabledCount);
    }
    private int dialogHeight() {
        return switch (dialog) {
            case NOT_FOUND, INVALID_SEARCH, EXCLUDED -> Math.min(height - 10, searchFailureTextOffset() + searchFailureContentHeight() + 30);
            case WRONG_DIMENSION -> textRenderer.fontHeight + 12;
            case INSUFFICIENT_COST -> 3 * textRenderer.fontHeight + 16;
            case MULTI_COST -> (costExplanation().size() + 4) * textRenderer.fontHeight + 46;
            case SEARCHING -> searchDialogHeight(foundRowsHeight(4));
            case FOUND -> searchDialogHeight(foundContentHeight());
            default -> 80;
        };
    }
    private int dialogX() { return (width - dialogWidth()) / 2; }
    private int dialogY() { return (height - dialogHeight()) / 2; }

    private boolean usesEnergy() {
        return SearchCosts.remote().type() == SearchCosts.Type.ITEMS;
    }

    private String costTitle() {
        return usesEnergy() ? "Not Enough Energy" : "Not Enough Experience";
    }

    private String costLabel() {
        String label = switch (SearchCosts.remote().type()) {
            case ITEMS -> "Energy cost: ";
            case XP -> "XP cost: ";
            case XP_LEVELS -> "Level cost: ";
        };
        return dialog == Dialog.MULTI_COST ? "Highest " + (SearchCosts.remote().type() == SearchCosts.Type.XP
                ? "XP cost: " : label.toLowerCase(Locale.ROOT)) : label;
    }

    private String balanceLabel() {
        return switch (SearchCosts.remote().type()) {
            case ITEMS -> "Current energy: ";
            case XP -> "Current XP: ";
            case XP_LEVELS -> "Current XP Levels: ";
        };
    }

    private String costSuffix(int amount) {
        return SearchCosts.remote().type() == SearchCosts.Type.XP
                ? SearchCosts.equivalentLevels(amount) + " Levels" : "";
    }

    private List<net.minecraft.text.OrderedText> costExplanation() {
        return textRenderer.wrapLines(Text.literal("Your selection includes structures for which you don't have enough " + (usesEnergy() ? "energy." : "experience.")),
                Math.max(1, dialogWidth() - 16));
    }

    int costLineWidth(String label, int amount, String suffix) {
        return textRenderer.getWidth(label) + textRenderer.getWidth(Integer.toString(amount))
                + 8 + textRenderer.getWidth(suffix);
    }

    void renderCostLine(DrawContext context, int centerX, int y, String label, int amount, String suffix) {
        int x = centerX - costLineWidth(label, amount, suffix) / 2;
        context.drawTextWithShadow(textRenderer, label, x, y, 0xffffff);
        x += textRenderer.getWidth(label);
        String number = Integer.toString(amount);
        drawExperienceNumber(context, textRenderer, number, x, y);
        x += textRenderer.getWidth(number) + 3;
        drawEnergyOrb(context, textRenderer, x, y);
        if (!suffix.isEmpty()) context.drawTextWithShadow(textRenderer, suffix, x + 5, y, 0xffffff);
    }

    private static void drawExperienceNumber(DrawContext context, TextRenderer font, String number, int x, int y) {
        context.drawText(font, number, x - 1, y, 0x000000, false);
        context.drawText(font, number, x + 1, y, 0x000000, false);
        context.drawText(font, number, x, y - 1, 0x000000, false);
        context.drawText(font, number, x, y + 1, 0x000000, false);
        context.drawText(font, number, x, y, 0x80ff20, false);
    }

    private static void drawEnergyOrb(DrawContext context, TextRenderer font, int x, int y) {
        Identifier texture = new Identifier(ExplorersCompass.MODID, "textures/gui/experience_orb_00.png");
        context.drawTexture(texture, x, y + (font.fontHeight - 6) / 2, 5, 5, 0, 0, 16, 16, 16, 16);
    }

    private record FoundLine(String label, Text value) {}

    private List<FoundLine> foundInfoLines() {
        Identifier setId = foundStructureId == null ? null : ExplorersCompass.structureIDsToSetIDs.get(foundStructureId);
        return List.of(
                new FoundLine("Name: ", Text.literal(StructureUtils.getStructureName(foundStructureId))),
                new FoundLine("Source: ", Text.literal(StructureUtils.getStructureSource(foundStructureId))),
                new FoundLine("Set: ", Text.literal(StructureUtils.formatIdPath(setId))));
    }

    private List<FoundLine> foundLocationLines() {
        List<FoundLine> lines = new ArrayList<>();
        if (showFoundCoordinates) {
            lines.add(new FoundLine("Coordinates:", Text.empty()));
            lines.add(new FoundLine("", Text.literal(foundX + ", " + foundZ)));
        }
        if (showFoundDistance) {
            int distance = client.player == null ? 0 : StructureUtils.getHorizontalDistanceToLocation(client.player, foundX, foundZ);
            lines.add(new FoundLine("Distance: ", Text.literal(Integer.toString(distance))));
        }
        if (foundVisited != null) {
            lines.add(new FoundLine("Visited: ", Text.literal(foundVisited ? "Yes" : "No")));
        }
        return lines;
    }

    private int widestFoundLine(List<FoundLine> lines) {
        int width = 0;
        for (FoundLine line : lines) {
            width = Math.max(width, textRenderer.getWidth(line.label()) + textRenderer.getWidth(line.value()));
        }
        return width;
    }

    private List<Text> sortedBiomeNames(List<Identifier> biomes, Set<Identifier> validBiomes) {
        record BiomeName(Identifier id, Text name) {}
        Collator collator = Collator.getInstance(Locale.forLanguageTag(client.getLanguageManager().getLanguage().replace('_', '-')));
        return biomes.stream()
                .filter(validBiomes::contains)
                .map(id -> new BiomeName(id, Text.translatableWithFallback(
                        "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                        StructureUtils.getStructureName(id)).formatted(Formatting.GRAY)))
                .sorted(Comparator.comparing((BiomeName biome) -> biome.name().getString(), collator)
                        .thenComparing(BiomeName::id))
                .map(BiomeName::name)
                .toList();
    }

    private void renderFoundDetails(DrawContext context, int x, int y, int width) {
        int viewport = foundViewportHeight();
        if (viewport <= 0) return;
        List<FoundLine> lines = foundInfoLines();
        List<FoundLine> locationLines = foundLocationLines();
        int indent = textRenderer.getWidth(FOUND_BIOMES_LABEL);
        int leftWidth = width - 24;
        int rightWidth = widestFoundLine(locationLines);
        if (!locationLines.isEmpty()) {
            int leftWanted = widestFoundLine(lines);
            for (Text name : foundBiomes) leftWanted = Math.max(leftWanted, indent + textRenderer.getWidth(name));
            int available = Math.max(2, leftWidth - 12);
            int rightLabelWidth = 0;
            for (FoundLine line : locationLines) rightLabelWidth = Math.max(rightLabelWidth, textRenderer.getWidth(line.label()));
            int preferredLeftWidth = textRenderer.getWidth("Source: Repurposed Structures");
            int reservedLeftWidth = Math.min(preferredLeftWidth, Math.max(available / 2, available - rightLabelWidth));
            rightWidth = Math.min(rightWidth, Math.max(rightLabelWidth, available - reservedLeftWidth));
            leftWidth = Math.min(leftWanted, Math.max(1, available - rightWidth));
        }

        int top = y + FOUND_DETAILS_TOP;
        int leftX = x + 12;
        int rightX = x + width - 12 - rightWidth;
        int lineY = top - (int) MathHelper.clamp(foundScroll, 0, foundMaxScroll());
        context.enableScissor(leftX, top, x + width - 12, top + viewport);
        for (int i = 0; i < lines.size(); i++) {
            drawFoundDetail(context, lines.get(i), leftX, lineY + i * FOUND_LINE_STEP, leftWidth);
        }
        for (int i = 0; i < foundBiomes.size(); i++) {
            int biomeY = lineY + (lines.size() + i) * FOUND_LINE_STEP;
            if (i == 0) context.drawTextWithShadow(textRenderer, FOUND_BIOMES_LABEL, leftX, biomeY, 0xffffff);
            RenderUtils.drawTextToFit(context, textRenderer, foundBiomes.get(i), leftX + indent, biomeY, leftWidth - indent, 0xaaaaaa);
        }
        for (int i = 0; i < locationLines.size(); i++) {
            drawFoundDetail(context, locationLines.get(i), rightX, lineY + i * FOUND_LINE_STEP, rightWidth);
        }
        context.disableScissor();
        foundScrollbar().render(context, 0xff242424, 0xff909090);
    }

    private void drawFoundDetail(DrawContext context, FoundLine line, int x, int y, int width) {
        int labelWidth = textRenderer.getWidth(line.label());
        if (labelWidth > 0) context.drawTextWithShadow(textRenderer, line.label(), x, y, 0xffffff);
        RenderUtils.drawTextToFit(context, textRenderer, line.value(), x + labelWidth, y, width - labelWidth, 0xaaaaaa);
    }

    private void drawDialogButton(DrawContext context, String label, int x, int y, int w, int mouseX, int mouseY) {
        boolean hovered = ButtonPress.contains(mouseX, mouseY, x, y, w, 20);
        context.fill(x, y, x + w, y + 20, 0xff909090);
        context.fill(x + 1, y + 1, x + w - 1, y + 19, hovered ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 6, 0xffffff);
    }

    private void drawQuestionTooltip(DrawContext context, String message, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        for (OrderedText line : textRenderer.wrapLines(Text.literal(message), Math.min(220, width - 30))) {
            StringBuilder content = new StringBuilder();
            line.accept((index, style, codePoint) -> { content.appendCodePoint(codePoint); return true; });
            lines.add(Text.literal(content.toString()));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    boolean isUnlockedThroughParty(Identifier id) {
        return id != null && ClientPacketHandlers.partyRequirements.containsKey(id);
    }

    public boolean isStructureLocked(Identifier id) {
		return !creativeBypassActive() && ExplorersCompass.lockedRequirements.containsKey(id);
    }

	private boolean creativeBypassActive() {
		return ExplorersCompassConfig.creativeUnlocksAll && player != null && player.getAbilities().creativeMode;
	}

	private boolean canTeleportToTarget() {
		if (client.player == null) return false;
		ItemStack held = ItemUtils.getHeldItem(client.player, explorersCompass);
		if (explorersCompass.getState(held) != CompassState.FOUND) return false;
		return !ExplorersCompass.lockedRequirements.containsKey(explorersCompass.getStructureID(held))
				|| (client.player.getAbilities().creativeMode && held.getNbt().getBoolean("CreativeBypass"));
	}

	private boolean showTeleportButton() {
		return ExplorersCompass.canTeleport && (!ExplorersCompassConfig.showTeleportOnlyInCreative
				|| (player != null && player.getAbilities().creativeMode));
	}

    private void pauseOpenedIntegration() {
        if (shouldPause() && client.currentScreen != this) {
            pausedIntegrationScreen = client.currentScreen;
        }
    }

    public static boolean shouldPauseIntegration(Screen screen) {
        return screen != null && screen == pausedIntegrationScreen;
    }

    public static void clearIntegrationPause(Screen nextScreen) {
        if (nextScreen != pausedIntegrationScreen) pausedIntegrationScreen = null;
    }

    private void rememberQuestBookReturn() {
        pauseOpenedIntegration();
        refreshAfterQuestBook = true;
        restoreQuestListAfterSync = true;
        questReturnScroll = selectionList.getScrollAmount();
    }

    private record LockedPopup(Identifier id, LockedRequirementsView view) {}
    private final java.util.Deque<LockedPopup> lockedHistory = new java.util.ArrayDeque<>();
    private boolean openingRequirementAdvancement;

    private void showLockedRequirements(Identifier id, RequirementNode requirements) {
        lockedHistory.clear();
        setLockedPopup(id, requirements, null);
    }

    private void setLockedPopup(Identifier id, RequirementNode requirements, String title) {
        lockedRequirementsId = id;
        lockedRequirementsView = new LockedRequirementsView(textRenderer, id, requirements, this::rememberQuestBookReturn,
                id != null && isUnlockedThroughParty(id), !lockedHistory.isEmpty(), title, this::openRequirementLink, this::canOpenRequirementLink);
        dialog = Dialog.LOCKED;
    }

    private void backLockedPopup() {
        if (lockedHistory.isEmpty()) {
            dismissDialog();
            return;
        }
        LockedPopup previous = lockedHistory.pop();
        lockedRequirementsId = previous.id();
        lockedRequirementsView = previous.view();
    }

    private boolean canOpenRequirementLink(RequirementNode node) {
        Identifier structure = node.structureId();
        return node.advancementId() != null || structure != null
                && !structure.equals(lockedRequirementsId) && isStructureLocked(structure);
    }

    private void openRequirementLink(RequirementNode node) {
        Identifier structure = node.structureId();
        if (structure != null && isStructureLocked(structure)) {
            RequirementNode requirements = ExplorersCompass.lockedRequirements.get(structure);
            if (requirements == null) return;
            lockedHistory.push(new LockedPopup(lockedRequirementsId, lockedRequirementsView));
            setLockedPopup(structure, requirements, null);
        } else if (node.advancementId() != null) {
            openRequirementAdvancement(node);
        }
    }

    private void openRequirementAdvancement(RequirementNode node) {
        var manager = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getAdvancementHandler();
        var advancement = manager == null ? null : manager.getManager().get(node.advancementId());
        var tab = advancement != null ? advancement.getRoot()
                : manager == null || node.advancementTab() == null ? null : manager.getManager().get(node.advancementTab());
        boolean canOpenTab = tab != null && tab.getDisplay() != null;
        if (!node.advancementHasDisplay() || advancement == null || advancement.getDisplay() == null
                || advancement.getDisplay().isHidden() || !canOpenTab) {
            boolean offerTab = node.advancementHasDisplay() && canOpenTab;
            String message = !node.advancementHasDisplay()
                    ? "This advancement has no display in the Advancements screen."
                    : "This advancement is hidden until you progress further."
                        + (offerTab ? "\nOpen Advancement tab instead?" : "");
            lockedHistory.push(new LockedPopup(lockedRequirementsId, lockedRequirementsView));
            setLockedPopup(null, new RequirementNode(Text.literal(message), false, List.of()), "Advancement Unavailable");
            if (offerTab) lockedRequirementsView.offerOpenTab(() -> {
                backLockedPopup();
                openAdvancementTab(manager, tab);
            });
            return;
        }
        openAdvancementTab(manager, tab);
    }

    private void openAdvancementTab(net.minecraft.client.network.ClientAdvancementManager manager,
                                    net.minecraft.advancement.Advancement tab) {
        openingRequirementAdvancement = true;
        try {
            client.setScreen(new net.minecraft.client.gui.screen.advancement.AdvancementsScreen(manager) {
                @Override public void close() { client.setScreen(ExplorersCompassScreen.this); }
                @Override public boolean shouldPause() { return ExplorersCompassScreen.this.shouldPause(); }
                @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                    if (client.options.advancementsKey.matchesKey(keyCode, scanCode)) {
                        close();
                        return true;
                    }
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            });
            manager.selectTab(tab, true);
        } finally {
            openingRequirementAdvancement = false;
        }
    }

    private void renderSearchDialog(DrawContext context, int mouseX, int mouseY) {
        if (dialog == Dialog.DISABLED) { disabledView.render(context,width,height,mouseX,mouseY); return; }
        if (dialog == Dialog.LOCKED) {
            lockedRequirementsView.render(context, width, height, mouseX, mouseY);
            return;
        }
        int w = dialogWidth(), x = dialogX(), y = dialogY(), h = dialogHeight();
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.fill(0, 0, width, height, 0x88000000);
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xff909090);
        context.fill(x, y, x + w, y + h, 0xff000000);
        if (dialog == Dialog.SEARCHING) {
            int frame = (int)((System.currentTimeMillis() - dialogStarted) / 31L % 32L);
            Identifier texture = CompassTextures.frame(frame);
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            int centerX = x + w / 2;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Locating..."), centerX, y + SEARCH_DIALOG_TITLE_Y, 0xffffff);
            List<String> lines = new ArrayList<>();
            if (searchedListed) {
                lines.add(listedSelectedCount + " structures selected");
                if (listedSkippedCount > 0 || listedDisabledCount > 0) lines.add(wrongDimensionCountText().getString());
            } else {
                lines.add(StructureUtils.getStructureName(searchedId));
            }
            lines.add("Distance: " + searchRadius);
            int lineSpacing = 12;
            int textTop = y + SEARCH_DIALOG_TITLE_Y + textRenderer.fontHeight;
            int textHeight = (lines.size() - 1) * lineSpacing + textRenderer.fontHeight;
            int actionsY = searchActionsY();
            int lineY = textTop + (actionsY - textTop - textHeight) / 2;
            for (String line : lines) {
                context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(line, w - 12), centerX, lineY, 0xffffff);
                lineY += lineSpacing;
            }
            drawDialogButton(context, "Back", x + (w - FOUND_ACTION_BUTTON_WIDTH) / 2, actionsY, FOUND_ACTION_BUTTON_WIDTH, mouseX, mouseY);
        } else if (dialog == Dialog.FOUND) {
            double yaw = client.player == null ? 0.0D : client.player.getYaw() % 360.0D;
            double targetAngle = client.player == null ? 0.0D : Math.atan2(foundZ - client.player.getZ(), foundX - client.player.getX());
            double angle = MathHelper.floorMod((Math.PI - ((yaw - 90.0D) * (Math.PI / 180.0D) - targetAngle)) / (Math.PI * 2.0D), 1.0D);
            int frame = (16 + (int)Math.floor(angle * 32.0D + 0.5D)) % 32;
            Identifier texture = CompassTextures.frame(frame);
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Structure Located"), x + w / 2, y + SEARCH_DIALOG_TITLE_Y, 0xffffff);
            renderFoundDetails(context, x, y, w);
            drawDialogButton(context, Text.literal("Back").getString(), foundBackX(), searchActionsY(), foundActionWidth(), mouseX, mouseY);
			if (showTeleportButton()) {
				drawDialogButton(context, Text.literal("Teleport").getString(), foundTeleportX(), searchActionsY(), foundActionWidth(), mouseX, mouseY);
			}
            drawDialogButton(context, Text.literal("Close").getString(), foundCloseX(), searchActionsY(), foundActionWidth(), mouseX, mouseY);
        } else if (dialog == Dialog.INSUFFICIENT_COST || dialog == Dialog.MULTI_COST) {
            int center = x + w / 2;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(costTitle()), center, y + 6, 0xffff4444);
            int lineY = y + 10 + textRenderer.fontHeight;
            if (dialog == Dialog.MULTI_COST) {
                for (var line : costExplanation()) {
                    context.drawCenteredTextWithShadow(textRenderer, line, center, lineY, 0xffffff);
                    lineY += textRenderer.fontHeight;
                }
                lineY += textRenderer.fontHeight;
                drawDialogButton(context, "Back", center - 35, y + h - 26, 70, mouseX, mouseY);
            }
            renderCostLine(context, center, lineY, costLabel(), insufficientCost, costSuffix(insufficientCost));
            renderCostLine(context, center, lineY + textRenderer.fontHeight, balanceLabel(), availableEnergy, costSuffix(availableEnergy));
        } else if (isSearchFailureDialog()) {
            renderSearchFailureDialog(context, x, y, w, mouseX, mouseY);
        } else {
            boolean wrongDimension = dialog == Dialog.WRONG_DIMENSION;
            String message = wrongDimension ? "You're In The Wrong Dimension" : "Search could not start";
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(message, w - 12), x + w / 2,
                    wrongDimension ? y + (h - textRenderer.fontHeight) / 2 : y + 14, wrongDimension ? 0xffff4444 : 0xffffff);
            if (dialog == Dialog.REJECTED) {
                drawDialogButton(context, "Back", x + (w - 70) / 2, y + 57, 70, mouseX, mouseY);
            }
        }
        context.getMatrices().pop();
    }

    private void renderSearchFailureDialog(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        if (dialog == Dialog.EXCLUDED) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(excludedByWhitelist ? "Not On Whitelist" : "Blacklisted"), x + w / 2, y + 6, 0xffff5555);
        } else if (dialog == Dialog.INVALID_SEARCH) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Invalid Search"), x + w / 2, y + 6, 0xffffff);
        } else if (height >= 90) {
            long now = System.nanoTime();
            long elapsed = now - notFoundCompassUpdated;
            int ticks = (int)Math.min(5L, elapsed / 50_000_000L);
            if (ticks > 0) {
                notFoundCompassUpdated = now - elapsed % 50_000_000L;
            }
            for (int tick = 0; tick < ticks; tick++) {
                double delta = MathHelper.floorMod(Math.random() - notFoundCompassAngle + 0.5D, 1.0D) - 0.5D;
                notFoundCompassSpeed = (notFoundCompassSpeed + delta * 0.1D) * 0.8D;
                notFoundCompassAngle = MathHelper.floorMod(notFoundCompassAngle + notFoundCompassSpeed, 1.0D);
            }
            int frame = (16 + (int)Math.floor(notFoundCompassAngle * 32.0D + 0.5D)) % 32;
            Identifier texture = CompassTextures.frame(frame);
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
        }

        int textTop = y + searchFailureTextOffset();
        int textHeight = searchFailureTextHeight();
        if (textHeight > 0) {
            int lineY = textTop - (int) MathHelper.clamp(searchFailureScroll, 0, searchFailureMaxScroll());
            context.enableScissor(x + 8, textTop, x + w - 8, textTop + textHeight);
            for (SearchFailureLine line : searchFailureLines()) {
                int lineX = line.centered() ? x + (w - textRenderer.getWidth(line.text())) / 2 : x + 12 + line.indent();
                if (line.bullet()) context.drawTextWithShadow(textRenderer, "•", x + 12, lineY, 0xffffff);
                context.drawTextWithShadow(textRenderer, line.text(), lineX, lineY, 0xffffff);
                lineY += textRenderer.fontHeight;
            }
            context.disableScissor();
            searchFailureScrollbar().render(context, 0xff242424, 0xff909090);
        }

        int actionY = searchFailureActionsY();
        int buttonWidth = searchFailureButtonWidth();
        drawDialogButton(context, "Back", searchFailureBackX(), actionY, buttonWidth, mouseX, mouseY);
        if (showNotFoundHide()) {
            int hideX = searchFailureFirstButtonX();
            boolean hidden = isStructureHidden(searchedId);
            drawDialogButton(context, hidden ? "Undo" : "Hide", hideX, actionY, buttonWidth, mouseX, mouseY);
            if (!hidden && ButtonPress.contains(mouseX, mouseY, hideX, actionY, buttonWidth, 20)) {
                context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(EntryDetailsPopup.HIDE_TOOLTIP),
                        Math.max(1, Math.min(220, width - 30))), mouseX, mouseY);
            }
        }
    }

    private int hiddenVisibleRows() { return Math.max(1, (filterBottom() - filterTop() - 12) / 25); }

	private int hiddenMaxScroll() {
		return Math.max(0, filteredHiddenStructures().size() - hiddenVisibleRows());
	}

    private void renderHiddenStructures(DrawContext context, int mouseX, int mouseY) {
        context.fill(filterLeft(), filterTop(), filterRight(), filterBottom(),
				RenderUtils.blackWithOpacity(RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT));
        List<String> ids = filteredHiddenStructures();
        hiddenScroll = MathHelper.clamp(hiddenScroll, 0, Math.max(0, ids.size() - hiddenVisibleRows()));
        context.enableScissor(filterLeft(), filterTop(), filterRight(), filterBottom());
        if (ids.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(ExplorersCompassConfig.hiddenStructures.isEmpty()
                        ? "No hidden structures" : "No matching hidden structures"),
                width / 2, filterTop() + 20, 0xffffff);
        for (int row = 0; row < hiddenVisibleRows() && hiddenScroll + row < ids.size(); row++) {
            int y = filterTop() + 6 + row * 25;
            String raw = ids.get(hiddenScroll + row);
            Identifier id = Identifier.tryParse(raw);
            String label = id == null || !allowedStructureIDs.contains(id) ? raw : StructureUtils.getStructureName(id) + " (" + raw + ")";
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, Math.max(1, width - 145)), 16, y + 6, 0xffffff);
            drawDialogButton(context, Text.literal("Remove").getString(), width - 95, y, 78, mouseX, mouseY);
        }
        context.disableScissor();
        scrollbarFor(ScrollPanel.HIDDEN).render(context);
    }

	private List<String> filteredHiddenStructures() {
		String query = hiddenSearchText.toLowerCase(Locale.ROOT);
		if (query.isEmpty()) return ExplorersCompassConfig.hiddenStructures;
		List<String> matches = new ArrayList<>();
		for (String raw : ExplorersCompassConfig.hiddenStructures) {
			Identifier id = Identifier.tryParse(raw);
			String name = id == null || !allowedStructureIDs.contains(id) ? raw : StructureUtils.getStructureName(id);
			if (raw.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query)) matches.add(raw);
		}
		return matches;
	}

	public boolean isStructureHidden(Identifier id) {
		return ExplorersCompassConfig.hiddenStructures.contains(id.toString());
	}

	public int rowActionButtonWidth() {
		return textRenderer.getWidth(Text.literal("filters")) + 8;
	}

	public void toggleStructureHidden(Identifier id) {
		String raw = id.toString();
		if (ExplorersCompassConfig.hiddenStructures.remove(raw)) pendingHiddenStructures.remove(id);
		else {
			ExplorersCompassConfig.hiddenStructures.add(raw);
			pendingHiddenStructures.add(id);
		}
		ExplorersCompassConfig.saveHiddenStructures();
	}

	private void finalizePendingHides() {
		if (pendingHiddenStructures.isEmpty()) return;
		if (pendingHiddenStructures.contains(revealedStructure)) clearCurrentStructureReveal();
		if (pendingHiddenStructures.contains(navigationTarget)) navigationTarget = null;
		pendingHiddenStructures.clear();
		double scroll = selectionList.getScrollAmount();
		processSearchTerm();
		selectionList.setScrollAmount(scroll);
	}

	public void teleport() {
		ClientPlayNetworking.send(TeleportPacket.ID, new TeleportPacket());
		client.setScreen(null);
	}

	private boolean passesStructureVisibility(Identifier id) {
		return (!isHiddenFromStructureList(id) || pendingHiddenStructures.contains(id))
				&& (!FilterSelections.excludeLocked() || !isStructureLocked(id));
	}

	public void processSearchTerm() {
		processSearchTerm(false);
	}

	private void processSearchTerm(boolean preserveUnchangedResults) {
		availableFilterOptions = null;
		structureSelection.retain(id -> !isHiddenFromStructureList(id) || pendingHiddenStructures.contains(id));
		if (structureSelection.isEmpty()) showNormalResults();
		if (selectedOnly) {
			structureIDsMatchingSearch = structureSelection.ids();
			selectionList.refreshList(preserveUnchangedResults);
			return;
		}
		structureIDsMatchingSearch = new ArrayList<Identifier>();
		String searchTerm = searchTextField.getText().toLowerCase(Locale.ROOT);
		var queries = StructureSearchQuery.parseAlternatives(searchTerm, ExplorersCompassConfig.matchSearchWordsInAnyOrder);
		if (queries.isEmpty() && navigationTarget == null) {
			selectionList.refreshList(preserveUnchangedResults);
			return;
		}
		for (Identifier id : allowedStructureIDs) {
            if (id.equals(navigationTarget) && searchTerm.equals(StructureUtils.getStructureName(id).toLowerCase(Locale.ROOT))) {
                structureIDsMatchingSearch.add(id);continue;
            }
			if (!passesStructureVisibility(id) || !FilterSelections.matches(id)) {
				continue;
			}
			String name = StructureUtils.getStructureName(id);
			if (queries.stream().anyMatch(query -> query.matches(id, name,
					ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id),
					ExplorersCompass.structureIDsToSetIDs.get(id)))) {
				structureIDsMatchingSearch.add(id);
			}
		}
		selectionList.refreshList(preserveUnchangedResults);
	}

	public List<Identifier> sortStructures() {
		final List<Identifier> structures = new ArrayList<Identifier>(structureIDsMatchingSearch);
		Collator collator = englishCollator();
		structures.sort((first, second) -> {
			for (SortCriterion criterion : sortOrder) {
				int comparison = criterion.compare(first, second, collator);
				if (comparison != 0) {
					return comparison;
				}
			}
			return first.compareTo(second);
		});
		return structures;
	}

	private Collator englishCollator() {
		return Collator.getInstance(Locale.US);
	}

	int sortPriorityLabelY() {
		return sortOrderButton.labelY();
	}

	private TransparentButton createClearFiltersButton(int x, int y) {
		return new TransparentButton(x, y, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE,
				Text.literal("Clear Filters"), button -> clearFilters()) {
			@Override
			protected void renderLabel(DrawContext context, net.minecraft.client.font.TextRenderer renderer) {
				int size = CLEAR_FILTERS_ICON_SIZE;
				int iconX = getX() + (getWidth() - size) / 2;
				int iconY = getY() + (getHeight() - size) / 2;
				float brightness = active ? 1.0f : 144 / 255.0f;
				drawIconWithShadow(context, CLEAR_FILTERS_ICON, iconX, iconY, size, brightness);
			}
		};
	}

	private void setupButtons() {
		searchButton = addDrawableChild(new TransparentButton(SIDEBAR_X, 40, sidebarWidth(), 20, Text.literal("Search"), button -> searchSelected()));
		selectionToggleButton = addDrawableChild(new TransparentButton(
				searchButton.getX() + sidebarWidth() - SMALL_BUTTON_SIZE, searchButton.getY(),
				SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE, Text.empty(), button -> {
			selectedOnly = !selectedOnly;
			processSearchTerm();
		}) {
			@Override
			protected void renderLabel(DrawContext context, net.minecraft.client.font.TextRenderer renderer) {
				if (selectedOnly && !isHovered()) {
					context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xffffffff);
				}
				String label = getMessage().getString();
				float scale = Math.min(1f, (getWidth() - 2f) / renderer.getWidth(label));
				var matrices = context.getMatrices();
				matrices.push();
				matrices.translate(getX() + getWidth() / 2f, getY() + (getHeight() - renderer.fontHeight * scale) / 2f, 0);
				matrices.scale(scale, scale, 1f);
				context.drawCenteredTextWithShadow(renderer, label, 0, 0, 0xffffff);
				matrices.pop();
			}
		});

		sortOrderButton = addDrawableChild(new TransparentButton(SIDEBAR_X, searchButton.getY() + 2 * SIDEBAR_BUTTON_STEP, sidebarWidth(), 20, Text.literal("Sort Priority"), (onPress) -> {
			if (sortMenuOpen) {
				closeSortMenu();
			} else {
				sortMenuOpen = true;
			}
		}));
		filtersButton = addDrawableChild(new TransparentButton(SIDEBAR_X, searchButton.getY() + SIDEBAR_BUTTON_STEP,
				sidebarWidth() - SMALL_BUTTON_SIZE - SEARCH_BUTTON_GAP, 20, Text.literal("Filters"), (onPress) -> {
			setFiltersOpen(!filtersOpen);
		}));
		clearFiltersButton = addDrawableChild(createClearFiltersButton(SIDEBAR_X + sidebarWidth() - SMALL_BUTTON_SIZE, filtersButton.getY()));
		currentStructureButton = addDrawableChild(new TransparentButton(SIDEBAR_X,
				sortOrderButton.getY() + SIDEBAR_BUTTON_STEP, sidebarWidth(), 20,
				Text.literal("Identify"), button -> requestCurrentStructure()));
		settingsButton = addDrawableChild(new TransparentButton(SIDEBAR_X, height - 30, sidebarWidth(), 20, Text.literal("Settings"), (onPress) -> {
			setSettingsOpen(true);
		}));
		int controlX = settingsControlX();
		overlaySideButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(1), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.overlaySide = ExplorersCompassConfig.overlaySide == OverlaySide.LEFT ? OverlaySide.RIGHT : OverlaySide.LEFT;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		offsetDecreaseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_OFFSET_ROW), 20, settingsControlHeight(), Text.literal("−"), (onPress) -> {
			if (ExplorersCompassConfig.overlayLineOffset > 0) {
				ExplorersCompassConfig.overlayLineOffset--;
				ExplorersCompassConfig.saveClient();
			}
		}));
		offsetIncreaseButton = addDrawableChild(new TransparentButton(controlX + SETTINGS_CONTROL_WIDTH - 20, settingsRowY(SETTINGS_OFFSET_ROW), 20, settingsControlHeight(), Text.literal("+"), (onPress) -> {
			if (ExplorersCompassConfig.overlayLineOffset < Integer.MAX_VALUE) {
				ExplorersCompassConfig.overlayLineOffset++;
				ExplorersCompassConfig.saveClient();
			}
		}));
		chatOverlayButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(3), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.displayWithChatOpen = !ExplorersCompassConfig.displayWithChatOpen;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		clearSearchBarOnCloseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_CLEAR_SEARCH_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.clearSearchBarOnClose = !ExplorersCompassConfig.clearSearchBarOnClose;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		clearOnCloseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_CLEAR_FILTERS_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.clearFiltersOnClose = !ExplorersCompassConfig.clearFiltersOnClose;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		checkedFiltersFirstButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(6), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.checkedFiltersFirst = !ExplorersCompassConfig.checkedFiltersFirst;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		}));
		showOnlyMatchingFilterOptionsButton = addDrawableChild(new TransparentButton(controlX,
				settingsRowY(SETTINGS_MATCHING_FILTER_OPTIONS_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.showOnlyMatchingFilterOptions = !ExplorersCompassConfig.showOnlyMatchingFilterOptions;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		}));
        hideSearchCostButton = addDrawableChild(new TransparentButton(controlX,
                settingsRowY(SETTINGS_HIDE_SEARCH_COST_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
            ExplorersCompassConfig.hideSearchCost = !ExplorersCompassConfig.hideSearchCost;
            ExplorersCompassConfig.saveClient();
            updateSettingsButtonLabels();
        }));
		matchSearchWordsButton = addDrawableChild(new TransparentButton(controlX,
				settingsRowY(SETTINGS_MATCH_SEARCH_WORDS_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.matchSearchWordsInAnyOrder = !ExplorersCompassConfig.matchSearchWordsInAnyOrder;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
			processSearchTerm();
		}));
		adjustRowsToFitViewButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(5), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.adjustRowsToFitView = !ExplorersCompassConfig.adjustRowsToFitView;
			ExplorersCompassConfig.saveClient();
			remove(selectionList);
			selectionList = new StructureSearchList(this, client, structureSelection, structureListX(), 40, width - structureListX() - StructureSearchList.SCROLLBAR_WIDTH, height - 50, 50);
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
			updateSettingsButtonLabels();
		}));
		closeOnFoundButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_CLOSE_ON_FOUND_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.closeOnFound = !ExplorersCompassConfig.closeOnFound;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		pauseGameWhenOpenButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_PAUSE_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.pauseGameWhenOpen = !ExplorersCompassConfig.pauseGameWhenOpen;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		creativeUnlocksAllButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_CREATIVE_UNLOCK_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.creativeUnlocksAll = !ExplorersCompassConfig.creativeUnlocksAll;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		showTeleportOnlyInCreativeButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_TELEPORT_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.showTeleportOnlyInCreative = !ExplorersCompassConfig.showTeleportOnlyInCreative;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		updateSettingsButtonLabels();
        hiddenStructuresButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_HIDDEN_ROW),
                SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.literal("Open"), button -> openHiddenStructures(null)));

		settingResetButtons.clear();
		addSettingResetButton(1, ClientSetting.OVERLAY_SIDE);
		addSettingResetButton(SETTINGS_OFFSET_ROW, ClientSetting.OVERLAY_LINE_OFFSET);
		addSettingResetButton(3, ClientSetting.DISPLAY_WITH_CHAT_OPEN);
		addSettingResetButton(5, ClientSetting.ADJUST_ROWS_TO_FIT_VIEW);
		addSettingResetButton(6, ClientSetting.CHECKED_FILTERS_FIRST);
		addSettingResetButton(SETTINGS_HIDE_SEARCH_COST_ROW, ClientSetting.HIDE_SEARCH_COST);
		addSettingResetButton(SETTINGS_CLOSE_ON_FOUND_ROW, ClientSetting.CLOSE_ON_FOUND);
		addSettingResetButton(SETTINGS_CLEAR_SEARCH_ROW, ClientSetting.CLEAR_SEARCH_BAR_ON_CLOSE);
		addSettingResetButton(SETTINGS_CLEAR_FILTERS_ROW, ClientSetting.CLEAR_FILTERS_ON_CLOSE);
		addSettingResetButton(SETTINGS_MATCH_SEARCH_WORDS_ROW, ClientSetting.MATCH_SEARCH_WORDS_IN_ANY_ORDER);
		addSettingResetButton(SETTINGS_MATCHING_FILTER_OPTIONS_ROW, ClientSetting.SHOW_ONLY_MATCHING_FILTER_OPTIONS);
		addSettingResetButton(SETTINGS_CREATIVE_UNLOCK_ROW, ClientSetting.CREATIVE_UNLOCKS_ALL);
		addSettingResetButton(SETTINGS_TELEPORT_ROW, ClientSetting.SHOW_TELEPORT_ONLY_IN_CREATIVE);
		addSettingResetButton(SETTINGS_PAUSE_ROW, ClientSetting.PAUSE_GAME_WHEN_OPEN);

		cancelButton = addDrawableChild(new TransparentButton(width - 30, 10, 20, 20, Text.literal(""), (onPress) -> {
			client.setScreen(null);
		}));

		teleportButton = addDrawableChild(new TransparentButton(SIDEBAR_X, currentStructureButton.getY() + SIDEBAR_BUTTON_STEP, sidebarWidth(), 20, Text.literal("Teleport"), (onPress) -> {
			teleport();
		}));
		teleportButton.active = false;
		clearFiltersTopButton = addDrawableChild(createClearFiltersButton(10, 10));
		TransparentButton excludeButton = addDrawableChild(new TransparentButton(10, 10,
				excludeButtonWidth("Exclude Visited"), 20, Text.literal("Exclude Visited"), button -> {
			FilterSelections.toggleExcludeVisited();
			if (revealedStructure != null) {
				clearCurrentStructureReveal();
				processSearchTerm();
			}
		}));
		excludeButton.setRightInset(FILTER_CHECKBOX_SIZE + EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP);
		excludeVisitedButton = excludeButton;
		TransparentButton lockedButton = addDrawableChild(new TransparentButton(10, 10,
				excludeButtonWidth("Exclude Locked"), 20, Text.literal("Exclude Locked"), button -> {
			FilterSelections.toggleExcludeLocked();
			clearCurrentStructureReveal();
			showNormalResults();
			processSearchTerm();
		}));
		lockedButton.setRightInset(FILTER_CHECKBOX_SIZE + EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP);
		excludeLockedButton = lockedButton;
        TransparentButton newlyButton = addDrawableChild(new TransparentButton(10, 40,
                excludeButtonWidth("Newly Unlocked"), 20, Text.literal("Newly Unlocked"), button -> {
            FilterSelections.toggleNewlyUnlocked();
            clearCurrentStructureReveal();
            showNormalResults();
            processSearchTerm();
        }));
        newlyButton.setRightInset(FILTER_CHECKBOX_SIZE + EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP);
        newlyUnlockedButton = newlyButton;
		backButton = addDrawableChild(new TransparentButton(10, 10, buttonWidthForLabel("Back"), 20, Text.literal("Back"), (onPress) -> {
            if (hiddenStructuresOpen) {
                leaveHiddenStructures();
            } else if (settingsOpen) {
				setSettingsOpen(false);
			} else {
				setFiltersOpen(false);
			}
		}));
		restoreDefaultsButton = addDrawableChild(new TransparentButton(10, 10, restoreDefaultsButtonWidth(), 20,
				Text.literal("Restore Defaults"), ignored -> restoreSettings(ExplorersCompassConfig::restoreClientDefaults)) {
			@Override
			protected void renderLabel(DrawContext context, net.minecraft.client.font.TextRenderer renderer) {
				int iconOffset = renderer.getWidth(SETTINGS_RESET_ALL_LABEL) + SETTINGS_RESET_LABEL_GAP;
				int contentWidth = iconOffset + SETTINGS_RESET_ICON_SIZE;
				int x = getX() + (getWidth() - contentWidth) / 2;
				context.drawTextWithShadow(renderer, SETTINGS_RESET_ALL_LABEL, x,
						getY() + (getHeight() - 8) / 2, 0xffffff);
				drawSettingsResetIcon(context, x + iconOffset, getY(), getHeight());
			}
		});
		restoreDefaultsButton.setTooltip(Tooltip.of(restoreDefaultsButton.getMessage()));
		positionTopButtons();
		updateViewButtons();
	}

	private void setupTextFields() {
		int searchX = selectionList.getRowLeft();

		int availableWidth = Math.max(1, cancelButton.getX() - searchX - HEADER_GAP);
		searchTextField = new TransparentTextField(textRenderer, searchX, 10, availableWidth, 20, Text.literal("Search"));
		searchTextField.setText(sessionSearchText);
		lastSearchFieldText = searchTextField.getText();
		searchTextField.setChangedListener(value -> {
			if (value.equals(lastSearchFieldText)) return;
			lastSearchFieldText = value;
			clearCurrentStructureReveal();
			showNormalResults();
            if (navigationTarget != null && !value.equals(StructureUtils.getStructureName(navigationTarget))) navigationTarget = null;
			processSearchTerm(true);
		});
		addDrawableChild(searchTextField);
		searchTextField.setVisible(isStructureView());
		int filterSearchX = filterSearchX();
		filterSearchTextField = new TransparentTextField(textRenderer, filterSearchX, 10,
				filterSearchWidth(filterSearchX), 20, Text.literal("Search Filters"));
		filterSearchTextField.setChangedListener(value -> {
			filterScroll = 0;
		});
		addDrawableChild(filterSearchTextField);
		filterSearchTextField.setVisible(filtersOpen);
		String settingsQuery = settingsSearchTextField == null ? "" : settingsSearchTextField.getText();
		int settingsSearchX = settingsSearchX();
		settingsSearchTextField = new TransparentTextField(textRenderer, settingsSearchX, 10,
				settingsSearchWidth(settingsSearchX), 20, Text.literal("Search Settings"));
		settingsSearchTextField.setMaxLength(256);
		settingsSearchTextField.setText(settingsQuery);
		settingsSearchTextField.setChangedListener(value -> {
			settingsFirstRow = 0;
			if (draggedScrollbar == ScrollPanel.SETTINGS) draggedScrollbar = null;
			updateViewButtons();
		});
		addDrawableChild(settingsSearchTextField);
		settingsSearchTextField.setVisible(settingsOpen);
		int headerEnd = backButton.getX() - HEADER_GAP;
		int hiddenSearchX = Math.min(10 + textRenderer.getWidth(HIDDEN_STRUCTURES_TITLE) + 10,
				Math.max(10, headerEnd - 80));
		hiddenSearchTextField = new TransparentTextField(textRenderer, hiddenSearchX, 10,
				Math.max(1, headerEnd - hiddenSearchX),
				20, Text.literal("Search hidden structures"));
		hiddenSearchTextField.setMaxLength(256);
		hiddenSearchTextField.setText(hiddenSearchText);
		hiddenSearchTextField.setChangedListener(value -> {
			hiddenSearchText = value;
			hiddenScroll = 0;
		});
		addDrawableChild(hiddenSearchTextField);
		hiddenSearchTextField.setVisible(hiddenStructuresOpen);
	}

	private boolean returnToSettingsFromHidden;

	private void openHiddenStructures(Identifier target) {
		returnToSettingsFromHidden = settingsOpen;
		if (isStructureView()) remove(selectionList);
		settingsOpen = false;
		hiddenStructuresOpen = true;
		searchTextField.setVisible(false);
		if (target != null) hiddenSearchTextField.setText(target.toString());
		hiddenScroll = 0;
		updateViewButtons();
		focusActiveSearchField();
	}

	private void leaveHiddenStructures() {
		hiddenStructuresOpen = false;
		draggedScrollbar = null;
		settingsOpen = returnToSettingsFromHidden;
		hiddenSearchText = "";
		hiddenSearchTextField.setText("");
		hiddenSearchTextField.setFocused(false);
		hiddenScroll = 0;
		if (isStructureView()) addDrawableChild(selectionList);
		searchTextField.setVisible(isStructureView());
		updateViewButtons();
		focusActiveSearchField();
	}

	private void positionFilterButtons() {
		filtersButton.setY(searchButton.getY() + SIDEBAR_BUTTON_STEP);
		currentStructureButton.setY(sortOrderButton.getY() + SIDEBAR_BUTTON_STEP);
		clearFiltersButton.setY(filtersButton.getY());
	}

	private int buttonWidthForLabel(String label) {
		return textRenderer.getWidth(label) + HEADER_BUTTON_TEXT_PADDING;
	}

	private int restoreDefaultsButtonWidth() {
		return buttonWidthForLabel(SETTINGS_RESET_ALL_LABEL) + SETTINGS_RESET_ICON_SIZE + SETTINGS_RESET_LABEL_GAP;
	}

	private int sidebarWidth() {
		return textRenderer.getWidth(title) + 2 * (HEADER_TITLE_MARGIN - SIDEBAR_X);
	}

	private int structureListX() {
		return SIDEBAR_X + sidebarWidth() + HEADER_GAP;
	}

	private int settingsSearchX() {
		return textRenderer.getWidth("Settings") + 2 * HEADER_TITLE_MARGIN;
	}

	private int settingsSearchWidth(int x) {
		return Math.max(1, restoreDefaultsButton.getX() - HEADER_GAP - x);
	}

	private int excludeButtonWidth(String label) {
		return textRenderer.getWidth(label) + FILTER_CHECKBOX_SIZE
				+ EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP + EXCLUDE_BUTTON_CONTENT_PADDING * 2;
	}

	private int filterSearchX() {
		return textRenderer.getWidth("Filters") + 2 * HEADER_TITLE_MARGIN;
	}

	private int filterSearchWidth(int searchX) {
		return Math.max(1, backButton.getX() - searchX - HEADER_GAP);
	}

	private void positionFilterSearchField() {
		int searchX = filterSearchX();
		filterSearchTextField.setX(searchX);
		filterSearchTextField.setY(10);
		filterSearchTextField.setWidth(filterSearchWidth(searchX));
	}

	private void positionTopButtons() {
		cancelButton.setX(width - 30);
		settingsButton.setY(height - 30);
        hiddenStructuresButton.setX(settingsControlX());
        hiddenStructuresButton.setY(settingsRowY(SETTINGS_HIDDEN_ROW));
		teleportButton.setY(currentStructureButton.getY() + SIDEBAR_BUTTON_STEP);
		backButton.setX(cancelButton.getX() - backButton.getWidth() - 5);
		int settingsSearchX = settingsSearchX();
		int restoreRight = backButton.getX() - 5;
		restoreDefaultsButton.setWidth(Math.max(1, Math.min(restoreDefaultsButtonWidth(), restoreRight - settingsSearchX)));
		restoreDefaultsButton.setX(restoreRight - restoreDefaultsButton.getWidth());
		if (settingsSearchTextField != null) settingsSearchTextField.setWidth(settingsSearchWidth(settingsSearchX));
		clearFiltersTopButton.setX(width - 10 - clearFiltersTopButton.getWidth());
		clearFiltersTopButton.setY(40);
		excludeVisitedButton.setX(filterLeft());
		excludeVisitedButton.setY(40);
		excludeLockedButton.setY(40);
        newlyUnlockedButton.setY(40);
		if (searchTextField != null) {

			searchTextField.setWidth(Math.max(1, cancelButton.getX() - searchTextField.getX() - HEADER_GAP));
		}
	}

	private void updateViewButtons() {
        String query = settingsSearchTextField == null ? "" : settingsSearchTextField.getText();
        boolean showCosts = SearchCosts.remote().showsSearchCosts();
        settingsRows = SettingsSearch.matchingRows(SETTINGS_LABELS, this::isSettingsDivider, query,
                row -> row != SETTINGS_HIDE_SEARCH_COST_ROW || showCosts);
        settingsFirstRow = Math.min(settingsFirstRow, settingsMaxFirstRow());
		settingsFirstRow = MathHelper.clamp(settingsFirstRow, 0, settingsMaxFirstRow());
		searchButton.visible = isStructureView();
		updateSearchButtons();
		sortOrderButton.visible = isStructureView();
		filtersButton.visible = isStructureView();
		currentStructureButton.visible = isStructureView();
		currentStructureButton.active = ClientPlayNetworking.canSend(CurrentStructurePacket.ID);
		clearFiltersButton.visible = isStructureView();
		settingsButton.visible = isStructureView();
        hiddenStructuresButton.visible = settingsOpen;
		closeOnFoundButton.visible = settingsOpen;
		chatOverlayButton.visible = settingsOpen;
		offsetDecreaseButton.visible = settingsOpen;
		offsetIncreaseButton.visible = settingsOpen;
		overlaySideButton.visible = settingsOpen;
		clearOnCloseButton.visible = settingsOpen;
		checkedFiltersFirstButton.visible = settingsOpen;
		adjustRowsToFitViewButton.visible = settingsOpen;
		pauseGameWhenOpenButton.visible = settingsOpen;
		creativeUnlocksAllButton.visible = settingsOpen;
		showTeleportOnlyInCreativeButton.visible = settingsOpen;
		cancelButton.visible = true;
		teleportButton.visible = isStructureView() && showTeleportButton();
		clearFiltersTopButton.visible = filtersOpen;
		excludeVisitedButton.visible = filtersOpen && ExplorersCompass.canExcludeVisited;
		excludeLockedButton.visible = filtersOpen && !ExplorersCompass.lockedRequirements.isEmpty();
		int excludeLockedX = filterLeft() + (excludeVisitedButton.visible ? excludeVisitedButton.getWidth() + 5 : 0);
		excludeLockedButton.setX(excludeLockedX);
		newlyUnlockedButton.setX(excludeLockedX + (excludeLockedButton.visible ? excludeLockedButton.getWidth() + 5 : 0));
        newlyUnlockedButton.visible = filtersOpen && io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers.showNewlyUnlockedFilter;
		backButton.visible = filtersOpen || settingsOpen || hiddenStructuresOpen;
		restoreDefaultsButton.visible = settingsOpen;
		if (settingsSearchTextField != null) settingsSearchTextField.setVisible(settingsOpen);
		if (hiddenSearchTextField != null) hiddenSearchTextField.setVisible(hiddenStructuresOpen);
		placeSetting(overlaySideButton, 1);
		placeSetting(offsetDecreaseButton, SETTINGS_OFFSET_ROW);
		placeSetting(offsetIncreaseButton, SETTINGS_OFFSET_ROW);
		placeSetting(chatOverlayButton, 3);
		placeSetting(adjustRowsToFitViewButton, 5);
		placeSetting(checkedFiltersFirstButton, 6);
		placeSetting(hideSearchCostButton, SETTINGS_HIDE_SEARCH_COST_ROW);
		placeSetting(matchSearchWordsButton, SETTINGS_MATCH_SEARCH_WORDS_ROW);
		placeSetting(showOnlyMatchingFilterOptionsButton, SETTINGS_MATCHING_FILTER_OPTIONS_ROW);
		placeSetting(closeOnFoundButton, SETTINGS_CLOSE_ON_FOUND_ROW);
		placeSetting(clearOnCloseButton, SETTINGS_CLEAR_FILTERS_ROW);
		placeSetting(clearSearchBarOnCloseButton, SETTINGS_CLEAR_SEARCH_ROW);
		placeSetting(pauseGameWhenOpenButton, SETTINGS_PAUSE_ROW);
		placeSetting(creativeUnlocksAllButton, SETTINGS_CREATIVE_UNLOCK_ROW);
		placeSetting(showTeleportOnlyInCreativeButton, SETTINGS_TELEPORT_ROW);
		placeSetting(hiddenStructuresButton, SETTINGS_HIDDEN_ROW);
		settingResetButtons.forEach((row, button) -> placeSetting(button, row));
	}

	private void placeSetting(ButtonWidget button, int row) {
		button.setY(settingsRowY(row) + (SETTINGS_ROW_HEIGHT - settingsControlHeight()) / 2);
		button.visible = settingsOpen && isSettingsRowVisible(row);
	}

}
