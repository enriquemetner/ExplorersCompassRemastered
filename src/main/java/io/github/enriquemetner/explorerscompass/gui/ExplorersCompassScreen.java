package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.client.CompassTextures;
import io.github.enriquemetner.explorerscompass.compat.jes.JesThumbnails;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.gui.dialog.CurrentStructureDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.DisabledStructureDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.EntryDetailsDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.EntryFiltersDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.FoundStructureDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.LockedRequirementsDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.SearchFailureDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.SearchStatusDialog;
import io.github.enriquemetner.explorerscompass.gui.dialog.SortPriorityDialog;
import io.github.enriquemetner.explorerscompass.gui.widgets.CostRendering;
import io.github.enriquemetner.explorerscompass.gui.widgets.IconButton;
import io.github.enriquemetner.explorerscompass.gui.widgets.Scrollbar;
import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentButton;
import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentTextField;
import io.github.enriquemetner.explorerscompass.items.CompassState;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.mixins.client.DrawContextAccessor;
import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import io.github.enriquemetner.explorerscompass.network.compass.RefreshCompassPacket;
import io.github.enriquemetner.explorerscompass.network.compass.TeleportPacket;
import io.github.enriquemetner.explorerscompass.network.search.CancelSearchPacket;
import io.github.enriquemetner.explorerscompass.network.search.CurrentStructurePacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchAvailabilityPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchListedPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchResultPacket;
import io.github.enriquemetner.explorerscompass.network.unlocking.StructureRequirementsPacket;
import io.github.enriquemetner.explorerscompass.sorting.SortCriterion;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.github.enriquemetner.explorerscompass.util.ItemUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ExplorersCompassScreen extends Screen {
	private static final int SIDEBAR_X = 10;
	private static final int SMALL_BUTTON_SIZE = 20;
	private static final int SEARCH_BUTTON_GAP = 5;
	private static final String SELECTION_TOGGLE_TOOLTIP = "List all selected structures, regardless of searched or filtered results.\n"
			+ "Editing the search bar or filters returns to normal results.";

	private static final int HEADER_GAP = 10;

	private static final int HEADER_TITLE_MARGIN = 16;

	private static final Identifier CLEAR_FILTERS_ICON = new Identifier(ExplorersCompass.MODID, "textures/gui/clear_filters.png");
	private static final int CLEAR_FILTERS_ICON_SIZE = 11;
	private static final int HEADER_BUTTON_TEXT_PADDING = 20;
	private static final int EXCLUDE_BUTTON_CONTENT_PADDING = HEADER_BUTTON_TEXT_PADDING / 2;
	private static final int EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP = 5;

	private static final int SIDEBAR_BUTTON_STEP = 25;
	private static String sessionSearchText = "";
	private String lastSearchFieldText;

	private World world;
	private PlayerEntity player;
	private List<Identifier> allowedStructureIDs;
	private List<Identifier> structureIDsMatchingSearch;
	private Identifier foundStructureId;
	private ItemStack stack;
	private ExplorersCompassItem explorersCompass;

	private ButtonWidget searchButton;
	private ButtonWidget selectionToggleButton;
	private boolean selectedOnly;
	private TransparentButton sortOrderButton;
	private ButtonWidget filtersButton;
	private ButtonWidget currentStructureButton;

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

	private TransparentTextField hiddenSearchTextField;
	private final StructureSelection structureSelection = new StructureSelection();
	private StructureSearchList selectionList;
	private boolean awaitingOpeningSync = true;
	private EntryDetailsDialog entryDetails;
	private EntryDetailsDialog requirementsReturnDetails;
	private int requirementsRequestId;
	private CurrentStructureDialog currentStructureDialog;
	private UUID currentStructureRequest;
	private Identifier revealedStructure;
	private final Set<Identifier> pendingHiddenStructures = new HashSet<>();
	private final HiddenStructuresList hiddenStructures = new HiddenStructuresList();
	private boolean filtersOpen;
	private boolean openingSettings;
	private final SortPriorityDialog sortPriority;
	private final FilterPanel filterPanel = new FilterPanel(this::passesStructureVisibility, this::clearCurrentStructureReveal,
			this::refreshAfterFilterChange, this::showNormalResults);

    private boolean hiddenStructuresOpen;
    public void preparePartyRefresh() {
        questReturnScroll = selectionList.getScrollAmount();
        restoreQuestListAfterSync = true;
    }
    private DisabledStructureDialog disabledDialog;

    private Identifier navigationTarget;
    private Identifier selectionAfterSync;
    private enum Dialog { NONE, LOCKED, DISABLED, WRONG_DIMENSION, INSUFFICIENT_COST, MULTI_COST, SEARCHING, FOUND, NOT_FOUND, INVALID_SEARCH, EXCLUDED, REJECTED }
    private Dialog dialog = Dialog.NONE;
    private LockedRequirementsDialog lockedRequirementsDialog;
    private Identifier lockedRequirementsId;
    private boolean refreshAfterQuestBook;
    private boolean restoreQuestListAfterSync;
    private double questReturnScroll;

    private SearchRequest searchRequest;
    private SearchStatusDialog searchStatusDialog;
    private FoundStructureDialog foundStructureDialog;
    private SearchFailureDialog searchFailureDialog;

    private boolean successfulClose;

	private boolean lastCreativeBypass;
	private enum ScrollPanel { FILTERS, HIDDEN, SEARCH_FAILURE }
	private ScrollPanel draggedScrollbar;
	private boolean draggingStructureScrollbar;
	private double scrollbarGrabOffset;

	private EntryFiltersDialog entryFilters;

	public ExplorersCompassScreen(World world, PlayerEntity player, ItemStack stack, ExplorersCompassItem explorersCompass, List<Identifier> allowedStructureIDs) {
		super(Text.literal("Select Structure"));
		this.world = world;
		this.player = player;
		this.stack = stack;
		this.explorersCompass = explorersCompass;
		lastCreativeBypass = creativeBypassActive();
		FilterSelections.openedInDimension(world.getRegistryKey().getValue());

		setAllowedStructureIDs(allowedStructureIDs);
		structureIDsMatchingSearch = new ArrayList<>(allowedStructureIDs);
		sortPriority = new SortPriorityDialog(ExplorersCompassConfig.sortOrder, this::saveSortOrder, this::playClickSound);

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
        if (openingRequirementAdvancement || openingSettings) return;
		currentStructureRequest = null;
		currentStructureDialog = null;
		clearCurrentStructureReveal();
        if (openingJesPreview) return;
        if (jesThumbnails != null) jesThumbnails.close();
        var handler = client.getNetworkHandler();
        boolean connected = handler != null && handler.getConnection().isOpen();
        if (dialog == Dialog.SEARCHING && !successfulClose && connected) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(searchRequest.id));
        }
		if (ExplorersCompassConfig.clearFiltersOnClose && !FilterSelections.isEmpty()) {
			FilterSelections.clear();
			showNormalResults();
		}
		if (connected) {
			sessionSearchText = ExplorersCompassConfig.clearSearchBarOnClose ? "" : searchTextField.getText();
		}
		pendingHiddenStructures.clear();
		hiddenStructures.setQuery("");
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double scroll1, double scroll2, double scroll3) {
        switch (inputLayer(OverlayInput.Event.POINTER)) {
            case DISABLED -> disabledDialog.scroll(scroll3);
            case CURRENT_STRUCTURE -> currentStructureDialog.scroll(scroll3, width, height);
            case DETAILS -> entryDetails.scroll(scroll3, width, height);
            case ENTRY_FILTERS -> entryFilters.scroll(scroll3, width, height);
            case LOCKED -> lockedRequirementsDialog.scroll(scroll3);
            case SEARCH -> {
                if (isSearchFailureDialog()) scrollSearchFailure(scroll3);
            }
            default -> {
                return scrollPage(scroll1, scroll2, scroll3);
            }
        }
        return true;
    }

    private int hiddenListTop() {
        return filtersOpen ? 70 : 40;
    }

    private boolean scrollPage(double scroll1, double scroll2, double scroll3) {
        if (hiddenStructuresOpen) {
            hiddenStructures.scroll(scroll3, hiddenListTop(), height - 10);
            return true;
        }
		if (filtersOpen) {
			if (filterPanel.scroll(scroll1, scroll2, scroll3)) return true;
			return super.mouseScrolled(scroll1, scroll2, scroll3);
		}
		if (sortPriority.isOpen()) return true;
		return selectionList.mouseScrolled(scroll1, scroll2, scroll3);
	}

	@Override
	protected void init() {
		boolean firstOpen = selectionList == null;
		double scroll = firstOpen ? 0 : selectionList.getScrollAmount();
		if (searchTextField != null) sessionSearchText = searchTextField.getText();
		clearChildren();
		sortPriority.init(textRenderer, width, height);
		filterPanel.resize(textRenderer, width, height);
		setupButtons();
		selectionList = new StructureSearchList(this, client, structureSelection, structureListX(), 40, width - structureListX() - Scrollbar.WIDTH, height - 50, 50);
		if (isStructureView()) {
			addDrawableChild(selectionList);
		}
		entryFilters = null;
		setupTextFields();
		filterPanel.rebuild();
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
        if ((dialog == Dialog.WRONG_DIMENSION || dialog == Dialog.INSUFFICIENT_COST)
                && searchStatusDialog.expired(System.currentTimeMillis())) {
			searchStatusDialog = null;
			dialog = Dialog.NONE;
			focusActiveSearchField();
		}
		searchTextField.tick();
		filterSearchTextField.tick();

		hiddenSearchTextField.tick();
		if (lastCreativeBypass != creativeBypassActive()) {
			lastCreativeBypass = creativeBypassActive();
			if (lastCreativeBypass && dialog == Dialog.LOCKED && requirementsReturnDetails == null
					&& !isUnlockedThroughParty(lockedRequirementsId)) {
				dialog = Dialog.NONE;
				lockedRequirementsDialog = null;
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
			if (dialog == Dialog.LOCKED && lockedRequirementsId != null && requirementsReturnDetails == null) {
				var partyRequirements = ClientPacketHandlers.partyRequirements.get(lockedRequirementsId);
				var updated = partyRequirements != null ? partyRequirements : ExplorersCompass.lockedRequirements.get(lockedRequirementsId);
				if (updated == null) {
                    backLockedDialog();
                } else if (!lockedRequirementsDialog.shows(updated)) {
                    double popupScroll = lockedRequirementsDialog.scrollPosition();
                    setLockedDialog(lockedRequirementsId, updated, null);
                    lockedRequirementsDialog.restoreScroll(popupScroll);
                }
			}
			entryFilters = null;
			boolean focusTrackedStructure = awaitingOpeningSync && !restoreQuestListAfterSync;
			awaitingOpeningSync = false;
			foundStructureId = ClientPacketHandlers.trackedStructureId;
			double scroll = restoreQuestListAfterSync ? questReturnScroll : selectionList.getScrollAmount();
			remove(selectionList);
			setAllowedStructureIDs(ExplorersCompass.allowedStructureIDs);
			structureSelection.retain(new HashSet<>(allowedStructureIDs)::contains);
			structureIDsMatchingSearch = new ArrayList<>(allowedStructureIDs);
			selectionList = new StructureSearchList(this, client, structureSelection, structureListX(), 40, width - structureListX() - Scrollbar.WIDTH, height - 50, 50);
			if (isStructureView()) {
				addDrawableChild(selectionList);
			}

			teleportButton.visible = isStructureView() && showTeleportButton();
			positionFilterButtons();
			positionTopButtons();
			positionFilterSearchField();
			updateViewButtons();
			filterPanel.rebuild();
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
        boolean dialogExposed = entryDetails == null && currentStructureDialog == null;
        boolean filtersExposed = dialogExposed && dialog == Dialog.NONE;
        boolean sortExposed = filtersExposed && entryFilters == null;
        boolean pageExposed = sortExposed && !sortPriority.isOpen();
        renderPage(context, pageExposed ? mouseX : offscreen, pageExposed ? mouseY : offscreen, partialTicks);
        if (sortPriority.isOpen()) sortPriority.render(context, sortExposed ? mouseX : offscreen, sortExposed ? mouseY : offscreen);
        if (entryFilters != null) entryFilters.render(context, width, height, filtersExposed ? mouseX : offscreen, filtersExposed ? mouseY : offscreen);
        if (dialog != Dialog.NONE && (dialog != Dialog.SEARCHING || searchRequest.confirmed())) {
            renderSearchDialog(context, dialogExposed ? mouseX : offscreen, dialogExposed ? mouseY : offscreen);
        }
        if (entryDetails != null) entryDetails.render(context, width, height, mouseX, mouseY);
        if (currentStructureDialog != null) currentStructureDialog.render(context, width, height, mouseX, mouseY);
    }

    private void renderPage(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        renderBackground(context);
		if (filtersOpen) {
			filterPanel.render(context, mouseX, mouseY);
        } else if (hiddenStructuresOpen) {
            hiddenStructures.render(context, textRenderer, width, hiddenListTop(), height - 10, mouseX, mouseY);
		}
        SearchCosts costs = SearchCosts.remote();
        boolean showBalance = isStructureView() && costs.showsSearchCosts();
        String headerLabel = switch (costs.type()) {
            case ITEMS -> "Energy: ";
            case XP -> "Experience: ";
            case XP_LEVELS -> "XP Levels: ";
        };
        int balance = showBalance ? mainViewBalance(costs) : 0;
        int balanceWidth = showBalance ? CostRendering.width(textRenderer, headerLabel, balance, "") : 0;
        int balanceX = cancelButton.getX() - HEADER_GAP - balanceWidth;
        if (isStructureView()) {
            searchTextField.setWidth(Math.max(1, balanceX - searchTextField.getX() - (showBalance ? HEADER_GAP : 0)));
        }
		super.render(context, mouseX, mouseY, partialTicks);
        if (showBalance) {
            int balanceY = cancelButton.getY() + (cancelButton.getHeight() - textRenderer.fontHeight) / 2;
            CostRendering.draw(context, textRenderer, balanceX + balanceWidth / 2, balanceY, headerLabel, balance, "");
            if (costs.type() == SearchCosts.Type.ITEMS && mouseX >= balanceX && mouseX < balanceX + balanceWidth
                    && mouseY >= balanceY && mouseY < balanceY + textRenderer.fontHeight) {
                renderEnergyTooltip(context, mouseX, mouseY);
            }
        }
		renderExcludeButton(context, excludeVisitedButton, FilterSelections.excludeVisited());
		renderExcludeButton(context, excludeLockedButton, FilterSelections.excludeLocked());
        renderExcludeButton(context, newlyUnlockedButton, FilterSelections.newlyUnlocked());
		if (excludeVisitedButton.visible && excludeVisitedButton.isMouseOver(mouseX, mouseY)) {
			drawQuestionTooltip(context, "Skip locations you’ve already visited when locating a structure.", mouseX, mouseY);
		}
		if (excludeLockedButton.visible && excludeLockedButton.isMouseOver(mouseX, mouseY)) {
			drawQuestionTooltip(context, "Hide structures you haven’t unlocked from the structure list.", mouseX, mouseY);
		}
        if (newlyUnlockedButton.visible && newlyUnlockedButton.isMouseOver(mouseX, mouseY))
            drawQuestionTooltip(context, "List only structures from your most recent unlock event", mouseX, mouseY);
		if (filtersOpen) {
			context.drawTextWithShadow(textRenderer, Text.literal("Filters"), HEADER_TITLE_MARGIN, 15, 0xffffff);
        } else if (hiddenStructuresOpen) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(HiddenStructuresList.TITLE,
                    Math.max(1, hiddenSearchTextField.getX() - 20)), 10, 15, 0xffffff);
		} else {
			context.drawTextWithShadow(textRenderer, title, HEADER_TITLE_MARGIN, 15, 0xffffff);
		}
		if (selectionToggleButton.visible && selectionToggleButton.isMouseOver(mouseX, mouseY)) {
			drawQuestionTooltip(context, SELECTION_TOGGLE_TOOLTIP, mouseX, mouseY);
		}
	}

    private void renderEnergyTooltip(DrawContext context, int mouseX, int mouseY) {
        Text instructions = Text.literal(
                "In your inventory, pick up a valid energy item and hold it over the compass.\n"
                + "Then Middle-Click to consume the whole stack, or scroll to consume one item at a time.");
        List<TooltipComponent> rows = new ArrayList<>();
        textRenderer.wrapLines(instructions, Math.max(1, Math.min(220, width - 30)))
                .forEach(line -> rows.add(TooltipComponent.of(line)));
        rows.add(TooltipComponent.of(Text.empty().asOrderedText()));
        rows.add(TooltipComponent.of(Text.literal("Compass Charging Rates:")
                .formatted(net.minecraft.util.Formatting.AQUA).asOrderedText()));
        SearchCosts.remote().items().forEach((id, rate) -> {
            ItemStack item = Registries.ITEM.get(id).getDefaultStack();
            String name = item.isEmpty() ? id.toString() : item.getName().getString();
            rows.add(new ChargingRateRow(item, name + ": ", rate.toString(), textRenderer.fontHeight));
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
            return iconSize + 2 + font.getWidth(label) + font.getWidth(rate) + 8;
        }

        @Override
        public void drawItems(TextRenderer font, int x, int y, DrawContext context) {
            int textY = y + (getHeight() - font.fontHeight) / 2;
            if (item.isOf(ExplorersCompass.EXPLORERS_COMPASS_ITEM)) {
                CompassTextures.drawStillIcon(context, x, textY, iconSize);
            } else {
                float scale = iconSize / 16.0f;
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(x, textY, 0);
                matrices.scale(scale, scale, 1);
                context.drawItem(item, 0, 0);
                matrices.pop();
            }
            int labelX = x + iconSize + 2;
            context.drawTextWithShadow(font, label, labelX, textY, 0xffffff);
            int rateX = labelX + font.getWidth(label);
            CostRendering.drawExperienceNumber(context, font, rate, rateX, textY);
            CostRendering.drawEnergyOrb(context, font, rateX + font.getWidth(rate) + 3, textY);
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

	private Scrollbar scrollbarFor(ScrollPanel panel) {
		return switch (panel) {
			case FILTERS -> filterPanel.scrollbar();
			case HIDDEN -> hiddenStructures.scrollbar(width, hiddenListTop(), height - 10);
			case SEARCH_FAILURE -> searchFailureDialog.scrollbar(width, height);
		};
	}

	boolean showsStructureSource() {
		return filterPanel.showsSource();
	}

	private void setAllowedStructureIDs(List<Identifier> ids) {
		allowedStructureIDs = new ArrayList<>(ids);
		filterPanel.setStructures(allowedStructureIDs, world.getRegistryKey().getValue());
	}

	boolean isSelectedOnly() {
		return selectedOnly;
	}

	public void showNormalResults() {
		selectedOnly = false;
	}

	private void refreshAfterFilterChange() {
		showNormalResults();
		processSearchTerm();
	}

	private void clearFilters() {
		clearCurrentStructureReveal();
		FilterSelections.clear();
		showNormalResults();
		processSearchTerm();
		filterPanel.clampScroll();
		updateSearchButtons();
	}

	public void openEntryDetails(Identifier id) {
		if (sortPriority.isOpen()) sortPriority.close();
		draggingStructureScrollbar = false;
		setDragging(false);
		setFocused(null);
		searchTextField.setFocused(false);
		entryDetails = new EntryDetailsDialog(client, id, () -> isStructureHidden(id),
				() -> toggleStructureHidden(id), () -> ClientPacketHandlers.showStructureRequirements,
				() -> openEntryRequirements(id), this::closeEntryDetails);
	}

	private void openEntryRequirements(Identifier id) {
		requirementsReturnDetails = entryDetails;
		entryDetails = null;
		lockedHistory.clear();
		boolean canRequest = ClientPlayNetworking.canSend(StructureRequirementsPacket.ID);
		RequirementNode fallback = ClientPacketHandlers.partyRequirements.get(id);
		if (fallback == null) fallback = ExplorersCompass.lockedRequirements.get(id);
		setLockedDialog(id, canRequest ? requirementsMessage("Loading requirements…")
				: fallback != null ? fallback : requirementsMessage("Requirements are unavailable"), "Requirements");
		if (canRequest) {
			requirementsRequestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
			ClientPlayNetworking.send(StructureRequirementsPacket.ID, StructureRequirementsPacket.request(requirementsRequestId, id));
		}
	}

	public void onStructureRequirements(int requestId, RequirementNode requirements) {
		if (requirementsReturnDetails == null || dialog != Dialog.LOCKED || requirementsRequestId != requestId) return;
		requirementsRequestId = 0;
		setLockedDialog(lockedRequirementsId, requirements != null ? requirements
				: requirementsMessage("Requirements are unavailable"), "Requirements");
	}

	private static RequirementNode requirementsMessage(String message) {
		return new RequirementNode(Text.literal(message), false, List.of());
	}

	private void closeEntryDetails() {
		entryDetails = null;
		finalizePendingHides();
		focusActiveSearchField();
	}

	public void openEntryFilters(Identifier id) {
		if (sortPriority.isOpen()) sortPriority.close();
		entryFilters = new EntryFiltersDialog(textRenderer, () -> entryFilterRows(id), () -> entryFilters = null,
				() -> selectionList.containsStructure(id), this::playClickSound,
				FilterPanel::drawFilterCheckbox);
	}

	private List<EntryFiltersDialog.Row> entryFilterRows(Identifier id) {
		List<EntryFiltersDialog.Row> rows = new ArrayList<>();
		if (filterPanel.showsSource()) {
			rows.add(entryFilterRow("Source:", StructureUtils.getStructureSource(id), 0, new FilterPanel.FilterOption("", id.getNamespace(), null, false)));
		}
		for (Identifier dimension : ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id)) {
			rows.add(entryFilterRow("Dimension:", StructureUtils.getDimensionName(dimension), 1, new FilterPanel.FilterOption("", null, dimension, false)));
		}
		Identifier set = ExplorersCompass.structureIDsToSetIDs.get(id);
		if (set != null) {
			rows.add(entryFilterRow("Set:", StructureUtils.formatIdPath(set), 2, new FilterPanel.FilterOption("", null, set, false)));
		}
		return rows;
	}

	private EntryFiltersDialog.Row entryFilterRow(String category, String value, int column, FilterPanel.FilterOption option) {
		return new EntryFiltersDialog.Row(category, value, () -> FilterPanel.checked(column, option), () -> {
			double scroll = selectionList.getScrollAmount();
			filterPanel.toggle(column, option);
			selectionList.setScrollAmount(scroll);
		});
	}

	private void renderExcludeButton(DrawContext context, ButtonWidget button, boolean checked) {
		if (!button.visible) return;
		FilterPanel.drawFilterCheckbox(context,
				button.getX() + button.getWidth() - FilterPanel.FILTER_CHECKBOX_SIZE - EXCLUDE_BUTTON_CONTENT_PADDING,
				button.getY() + (button.getHeight() - FilterPanel.FILTER_CHECKBOX_SIZE) / 2, checked);
	}

    private OverlayInput.Layer inputLayer(OverlayInput.Event event) {
        OverlayInput.Layer search = switch (dialog) {
            case NONE -> OverlayInput.Layer.PAGE;
            case LOCKED -> OverlayInput.Layer.LOCKED;
            case DISABLED -> OverlayInput.Layer.DISABLED;
            default -> OverlayInput.Layer.SEARCH;
        };
        return OverlayInput.layer(event, currentStructureDialog != null, entryDetails != null,
                entryFilters != null, sortPriority.isOpen(), search);
    }

    private Runnable actionAt(double mouseX, double mouseY) {
        switch (inputLayer(OverlayInput.Event.ACTION)) {
            case CURRENT_STRUCTURE: return currentStructureDialog.actionAt(mouseX, mouseY, width, height);
            case DETAILS: return entryDetails.actionAt(mouseX, mouseY, width, height);
            case DISABLED: return disabledDialog.actionAt(mouseX, mouseY, width, height);
            case ENTRY_FILTERS: return entryFilters.actionAt(mouseX, mouseY, width, height);
            case SORT: return sortPriority.actionAt(mouseX, mouseY);
            case LOCKED: return lockedRequirementsDialog.actionAt(mouseX, mouseY, width, height);
            case SEARCH: return dialogButtonAt(mouseX, mouseY);
            case PAGE: break;
        }
        for (var child : children()) {
            if (child instanceof TransparentButton widget && widget.active && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                return widget::onPress;
            }
        }
        if (hiddenStructuresOpen) {
            return hiddenStructures.removeButtonAt(mouseX, mouseY, textRenderer, width, hiddenListTop(), height - 10,
                    this::processSearchTerm);
        }
        return isStructureView() ? selectionList.actionAt(mouseX, mouseY) : null;
    }

	private Runnable dialogButtonAt(double mouseX, double mouseY) {
        if (dialog == Dialog.FOUND) {
            return foundStructureDialog.actionAt(width, height, mouseX, mouseY,
                    showTeleportButton() ? this::teleport : null, this::dismissDialog, () -> client.setScreen(null));
        }
        if (isSearchFailureDialog()) {
            return searchFailureDialog.actionAt(width, height, mouseX, mouseY, this::dismissDialog,
                    showNotFoundHide() ? () -> toggleStructureHidden(searchRequest.structure) : null, () -> isStructureHidden(searchRequest.structure));
        }
        return dialog == Dialog.SEARCHING && !searchRequest.confirmed()
                ? null : searchStatusDialog.actionAt(width, height, mouseX, mouseY, this::dismissDialog);
	}

	void playClickSound() {
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			Runnable hit = actionAt(mouseX, mouseY);
			if (hit != null) {
				selectionList.resetDoubleClick();
				playClickSound();
				hit.run();
				if (client.currentScreen == this) {
					refreshSelectedOnlyRows();
					updateSearchButtons();
					if (dialog == Dialog.NONE && entryFilters == null && !sortPriority.isOpen()) focusActiveSearchField();
				}
				return true;
			}
		}
		if (selectionList != null && (button != 0 || !isStructureView() || dialog != Dialog.NONE
				|| entryDetails != null || entryFilters != null || sortPriority.isOpen() || !selectionList.isMouseOver(mouseX, mouseY))) {
			selectionList.resetDoubleClick();
		}
		draggingStructureScrollbar = currentStructureDialog == null && entryDetails == null && button == 0 && dialog == Dialog.NONE
				&& entryFilters == null && !filtersOpen && !hiddenStructuresOpen && !sortPriority.isOpen()
				&& selectionList != null && selectionList.isScrollbarAt(mouseX, mouseY);
		try {
			return handleMouseClicked(mouseX, mouseY, button);
		} finally {
			refreshSelectedOnlyRows();
			updateSearchButtons();
			// Minecraft routes drag events to the focused child. Keep the list focused
			// after grabbing its scrollbar instead of returning focus to search.
			if (client.currentScreen == this && entryDetails == null && dialog == Dialog.NONE && entryFilters == null && !sortPriority.isOpen() && !draggingStructureScrollbar) focusActiveSearchField();
		}
	}

	private boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (currentStructureDialog != null) {
            if (currentStructureDialog.click(mouseX, mouseY, button, width, height)) playClickSound();
            return true;
        }
        switch (inputLayer(OverlayInput.Event.POINTER)) {
            case DISABLED:
                if (disabledDialog.click(mouseX, mouseY, button, width, height)) playClickSound();
                return true;
            case DETAILS:
                if (entryDetails.click(mouseX, mouseY, button, width, height)) playClickSound();
                return true;
            case ENTRY_FILTERS:
                return entryFilters.click(mouseX, mouseY, button, width, height);
            default: break;
        }
        if (sortPriority.isOpen()) return sortPriority.click(mouseX, mouseY, button);
        if (dialog == Dialog.LOCKED) {
            if (lockedRequirementsDialog.click(mouseX, mouseY, button, width, height)) playClickSound();
            return true;
        }
        if (dialog == Dialog.SEARCHING && !searchRequest.confirmed()) return true;
        if (dialog != Dialog.NONE) {
            if (isSearchFailureDialog() && button == 0 && beginScrollbarDrag(ScrollPanel.SEARCH_FAILURE, mouseX, mouseY)) return true;
            if (outsideSearchDialog(mouseX, mouseY)) {
                playClickSound();
                dismissDialog();
                return true;
            }
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
		if (filtersOpen && button == 0) {
			if (beginScrollbarDrag(ScrollPanel.FILTERS, mouseX, mouseY)) return true;
			if (filterPanel.click(mouseX, mouseY, this::playClickSound)) return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private TransparentTextField searchFieldAt(double mouseX, double mouseY) {
		for (TransparentTextField field : new TransparentTextField[] {searchTextField, filterSearchTextField, hiddenSearchTextField}) {
			if (field != null && field.isVisible() && mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
					&& mouseY >= field.getY() && mouseY < field.getY() + field.getHeight()) return field;
		}
		return null;
	}

	private TransparentTextField activeSearchField() {
		if (hiddenStructuresOpen) return hiddenSearchTextField;
		if (filtersOpen) return filterSearchTextField;
		return searchTextField;
	}

	private void focusActiveSearchField() {
		if (currentStructureDialog != null || entryDetails != null) return;
		TransparentTextField active = activeSearchField();
		for (TransparentTextField field : new TransparentTextField[] {searchTextField, filterSearchTextField, hiddenSearchTextField}) {
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
        OverlayInput.Layer layer = inputLayer(OverlayInput.Event.POINTER);
        switch (layer) {
            case DISABLED: disabledDialog.drag(mouseY, width, height); return true;
            case CURRENT_STRUCTURE: currentStructureDialog.drag(mouseY, width, height); return true;
            case DETAILS: entryDetails.drag(mouseY, width, height); return true;
            case ENTRY_FILTERS: return true;
            default: break;
        }
        if (layer == OverlayInput.Layer.LOCKED) { lockedRequirementsDialog.drag(mouseY); return true; }
		if (draggedScrollbar != null && button == 0) {
			moveScrollbar(mouseY);
			return true;
		}
		if (sortPriority.drag(mouseX, mouseY, button)) return true;
		if (sortPriority.isOpen()) return true;
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
        OverlayInput.Layer layer = inputLayer(OverlayInput.Event.POINTER);
        switch (layer) {
            case DISABLED: disabledDialog.release(); return true;
            case CURRENT_STRUCTURE: currentStructureDialog.release(); return true;
            case DETAILS: entryDetails.release(); return true;
            case ENTRY_FILTERS: return true;
            default: break;
        }
        if (draggingStructureScrollbar && button == 0) {
            draggingStructureScrollbar = false;
            super.mouseReleased(mouseX, mouseY, button);
            if (client.currentScreen == this && dialog == Dialog.NONE) focusActiveSearchField();
            return true;
        }
        if (layer == OverlayInput.Layer.LOCKED) { lockedRequirementsDialog.release(); return true; }
		if (draggedScrollbar != null && button == 0) {
			draggedScrollbar = null;
			return true;
		}
		if (sortPriority.release(button)) return true;
		if (sortPriority.isOpen()) return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int par1, int par2, int par3) {
        OverlayInput.Layer layer = inputLayer(OverlayInput.Event.KEY);
        if (layer != OverlayInput.Layer.PAGE) {
            handleOverlayKey(layer, par1);
            return true;
        }
		if (par1 == GLFW.GLFW_KEY_S && par3 == GLFW.GLFW_MOD_CONTROL) {
			openingSettings = true;
			try {
				client.setScreen(new CompassSettingsScreen(this));
			} finally {
				openingSettings = false;
			}
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
		if (filtersOpen && par1 == 256) {
			setFiltersOpen(false);
			return true;
		}
		boolean ret = super.keyPressed(par1, par2, par3);
		focusActiveSearchField();
		return ret;
	}

    private void handleOverlayKey(OverlayInput.Layer layer, int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            switch (layer) {
                case CURRENT_STRUCTURE -> closeCurrentStructureDialog();
                case DETAILS -> closeEntryDetails();
                case ENTRY_FILTERS -> entryFilters = null;
                case SORT -> sortPriority.close();
                case LOCKED -> {
                    if (!lockedRequirementsDialog.closeQuestPopup()) backLockedDialog();
                }
                default -> dismissDialog();
            }
        } else if (layer == OverlayInput.Layer.LOCKED) {
            lockedRequirementsDialog.keyPressed(key);
        } else {
            int amount = switch (key) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> 1;
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> -1;
                default -> 0;
            };
            if (amount == 0) return;
            if (layer == OverlayInput.Layer.DISABLED) disabledDialog.scroll(amount);
            if (layer == OverlayInput.Layer.SEARCH && isSearchFailureDialog()) scrollSearchFailure(amount);
        }
    }

	@Override
	public boolean charTyped(char typedChar, int keyCode) {
		if (inputLayer(OverlayInput.Event.KEY) != OverlayInput.Layer.PAGE) return true;
		boolean ret = super.charTyped(typedChar, keyCode);
		focusActiveSearchField();
		return ret;
	}

	private boolean beginScrollbarDrag(ScrollPanel panel, double mouseX, double mouseY) {
		Scrollbar scrollbar = scrollbarFor(panel);
		if (!scrollbar.contains(mouseX, mouseY)) return false;
		int padding = Scrollbar.GRAB_PADDING;
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
			case FILTERS -> filterPanel.setScroll(offset);
			case HIDDEN -> hiddenStructures.setScroll(offset);
			case SEARCH_FAILURE -> searchFailureDialog.setScroll(offset);
		}
	}

	private void setFiltersOpen(boolean open) {
		if (filtersOpen == open) {
			return;
		}
		if (sortPriority.isOpen()) {
			sortPriority.close();
		}
		entryFilters = null;
		filtersOpen = open;
		filterPanel.setOpen(open);
		if (open) {
			pendingHiddenStructures.clear();
			filterPanel.invalidate();
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

	private void saveSortOrder(List<String> order) {
		ExplorersCompassConfig.sortOrder = new ArrayList<>(order);
		ExplorersCompassConfig.saveClient();
		selectionList.refreshList();
	}

	void resumeFromSettings() {
		sortPriority.load(ExplorersCompassConfig.sortOrder);
		draggedScrollbar = null;
		clearAndInit();
	}

	private boolean isStructureView() {
		return !filtersOpen && !hiddenStructuresOpen;
	}

    private void beginSearch(Identifier id, int selectedCount) {
        searchRequest = new SearchRequest(ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE), id,
                selectedCount, ExplorersCompass.canExcludeVisited && FilterSelections.excludeVisited());
        searchStatusDialog = SearchStatusDialog.searching(textRenderer, this::searchProgressLines, searchRequest::confirmedAt);
        dialog = Dialog.SEARCHING;
    }

    private List<String> searchProgressLines() {
        List<String> lines = new ArrayList<>();
        if (searchRequest.listed()) {
            lines.add(searchRequest.selectedCount + " structures selected");
            if (searchRequest.hasUnavailable()) lines.add(searchRequest.unavailableCounts());
        } else {
            lines.add(StructureUtils.getStructureName(searchRequest.structure));
        }
        lines.add("Distance: " + searchRequest.radius());
        return lines;
    }

    public void searchForStructure(Identifier structureID) {
		if (isHiddenFromStructureList(structureID)) return;
        boolean started = dialog == Dialog.NONE && structureID != null;
        if (started) beginSearch(structureID, 1);
		finalizePendingHides();
        if (started) ClientPlayNetworking.send(SearchPacket.ID, new SearchPacket(structureID, false, searchRequest.excludeVisited, ExplorersCompassConfig.creativeUnlocksAll, searchRequest.id));
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
        beginSearch(null, selected.size());
        ClientPlayNetworking.send(SearchListedPacket.ID, new SearchListedPacket(selected, searchRequest.excludeVisited, ExplorersCompassConfig.creativeUnlocksAll, searchRequest.id));
    }

    public void onSearchAvailability(SearchAvailabilityPacket.Response response) {
        if (dialog != Dialog.SEARCHING || !response.appliesTo(searchRequest.id, true)) return;
        searchRequest.updateAvailability(response.wrongDimension(), response.disabled().size());
        if (searchRequest.listed() && response.isInvalidSelection(searchRequest.selectedCount)) {

            searchFailureDialog = SearchFailureDialog.invalidSearch(textRenderer);
            dialog = Dialog.INVALID_SEARCH;
            searchStatusDialog = null;
        } else if (response.eligible() == 0 && !response.disabled().isEmpty()) {
            dialog = Dialog.DISABLED;
            searchStatusDialog = null;
            disabledDialog = new DisabledStructureDialog(textRenderer, response.disabled(), !searchRequest.listed(),
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
                || entryDetails != null || entryFilters != null || sortPriority.isOpen()) return;
        List<Identifier> available = matches.stream().filter(allowedStructureIDs::contains).toList();
        if (available.size() == 1) selectCurrentStructure(available.get(0));
        else currentStructureDialog = new CurrentStructureDialog(textRenderer, available, currentStructureEmptyHeight(),
                this::selectCurrentStructure, this::closeCurrentStructureDialog);
    }

    private void closeCurrentStructureDialog() {
        currentStructureDialog = null;
        focusActiveSearchField();
    }

    private void selectCurrentStructure(Identifier id) {
        closeCurrentStructureDialog();
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
        if (dialog == Dialog.SEARCHING && searchRequest.accepts(requestId)) {
            searchRequest.progress(radius, System.currentTimeMillis());
        }
    }

    public void onSearchResult(int requestId, int result, int radius, Identifier resultId, int foundX, int foundZ, boolean displayCoordinates, boolean displayDistance, List<Identifier> biomes, Set<Identifier> validBiomes, Boolean visited, RequirementNode requirements, int balance) {
        if (dialog != Dialog.SEARCHING || !searchRequest.accepts(requestId)) return;
        searchStatusDialog = null;
        if (result == SearchResultPacket.FOUND) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 1.0F));
            if (ExplorersCompassConfig.closeOnFound) {
                successfulClose = true;
                client.setScreen(null);
            } else {
                foundStructureId = resultId;
                foundStructureDialog = new FoundStructureDialog(textRenderer, foundX, foundZ, displayCoordinates, displayDistance,
                        biomes, validBiomes, client.getLanguageManager().getLanguage(), visited);
				double scrollAmount = selectionList.getScrollAmount();
				selectionList.refreshList();
				selectionList.setScrollAmount(scrollAmount);
                dialog = Dialog.FOUND;
            }
        } else if (result == SearchResultPacket.NOT_FOUND) {

            Text unavailableCounts = searchRequest.hasUnavailable() ? Text.literal(searchRequest.unavailableCounts()) : null;
            searchFailureDialog = SearchFailureDialog.notFound(textRenderer, radius, searchRequest.excludeVisited,
                    searchRequest.listed() ? searchRequest.selectedCount : 0, unavailableCounts);

            dialog = Dialog.NOT_FOUND;
        } else if (result == SearchResultPacket.BLACKLISTED || result == SearchResultPacket.NOT_WHITELISTED) {
            showExcluded(resultId, result == SearchResultPacket.NOT_WHITELISTED);
        } else if (result == SearchResultPacket.LOCKED) {
            showLockedRequirements(searchRequest.structure, requirements);
        } else if (result == SearchResultPacket.WRONG_DIMENSION) {
            showWrongDimension();
        } else if (result == SearchResultPacket.INSUFFICIENT_COST || result == SearchResultPacket.MULTI_COST) {
            showInsufficientCost(radius, balance, result == SearchResultPacket.MULTI_COST);
        } else {
            dialog = Dialog.REJECTED;
            searchStatusDialog = SearchStatusDialog.notice(textRenderer, SearchStatusDialog.Kind.REJECTED, 0, 0);
        }
    }

    private void showInsufficientCost(int cost, int balance, boolean multiple) {
        dialog = multiple ? Dialog.MULTI_COST : Dialog.INSUFFICIENT_COST;
        searchStatusDialog = SearchStatusDialog.notice(textRenderer, multiple ? SearchStatusDialog.Kind.MULTI_COST
                : SearchStatusDialog.Kind.INSUFFICIENT_COST, cost, balance);
    }

    private void showWrongDimension() {
        dialog = Dialog.WRONG_DIMENSION;
        searchStatusDialog = SearchStatusDialog.notice(textRenderer, SearchStatusDialog.Kind.WRONG_DIMENSION, 0, 0);
    }

	private void dismissDialog() {
        lockedHistory.clear();
        if (dialog == Dialog.DISABLED || dialog == Dialog.NOT_FOUND) {
            finalizePendingHides();
            disabledDialog = null;
        }
        if (draggedScrollbar == ScrollPanel.SEARCH_FAILURE) draggedScrollbar = null;
        if (dialog == Dialog.SEARCHING && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(searchRequest.id));
        }
		dialog = Dialog.NONE;
		searchFailureDialog = null;
		searchStatusDialog = null;
		searchRequest = null;
		foundStructureDialog = null;
		lockedRequirementsDialog = null;
		lockedRequirementsId = null;
		if (requirementsReturnDetails != null) {
			entryDetails = requirementsReturnDetails;
			requirementsReturnDetails = null;
			requirementsRequestId = 0;
		}
		focusActiveSearchField();
    }

    private void showExcluded(Identifier id, boolean whitelistActive) {

        searchFailureDialog = SearchFailureDialog.excluded(textRenderer, id, whitelistActive);
        dialog = Dialog.EXCLUDED;
    }

    private boolean isSearchFailureDialog() {
        return dialog == Dialog.NOT_FOUND || dialog == Dialog.INVALID_SEARCH || dialog == Dialog.EXCLUDED;
    }

    private boolean showNotFoundHide() { return dialog == Dialog.NOT_FOUND && searchRequest.structure != null; }
    private void scrollSearchFailure(double amount) {
        searchFailureDialog.scroll(width, height, amount);
    }

    private int currentStructureEmptyHeight() {
        return FoundStructureDialog.dialogHeight(height, FoundStructureDialog.rowsHeight(4, textRenderer.fontHeight));
    }

    private boolean outsideSearchDialog(double mouseX, double mouseY) {
        if (dialog == Dialog.FOUND) return foundStructureDialog.outside(width, height, mouseX, mouseY);
        if (isSearchFailureDialog()) return searchFailureDialog.outside(width, height, mouseX, mouseY);
        return searchStatusDialog.dismissOnClick(width, height, mouseX, mouseY);
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

    private record LockedDialog(Identifier id, LockedRequirementsDialog view) {}
    private final java.util.Deque<LockedDialog> lockedHistory = new java.util.ArrayDeque<>();
    private boolean openingRequirementAdvancement;

    private void showLockedRequirements(Identifier id, RequirementNode requirements) {
        lockedHistory.clear();
        setLockedDialog(id, requirements, null);
    }

    private void setLockedDialog(Identifier id, RequirementNode requirements, String title) {
        lockedRequirementsId = id;
        lockedRequirementsDialog = new LockedRequirementsDialog(textRenderer, id, requirements, this::rememberQuestBookReturn,
                id != null && isUnlockedThroughParty(id), !lockedHistory.isEmpty(), title, this::openRequirementLink, this::canOpenRequirementLink,
                this::backLockedDialog, this::dismissDialog);
        dialog = Dialog.LOCKED;
    }

    private void backLockedDialog() {
        if (lockedHistory.isEmpty()) {
            dismissDialog();
            return;
        }
        LockedDialog previous = lockedHistory.pop();
        lockedRequirementsId = previous.id();
        lockedRequirementsDialog = previous.view();
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
            lockedHistory.push(new LockedDialog(lockedRequirementsId, lockedRequirementsDialog));
            setLockedDialog(structure, requirements, null);
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
            lockedHistory.push(new LockedDialog(lockedRequirementsId, lockedRequirementsDialog));
            setLockedDialog(null, new RequirementNode(Text.literal(message), false, List.of()), "Advancement Unavailable");
            if (offerTab) lockedRequirementsDialog.offerOpenTab(() -> {
                backLockedDialog();
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
        if (dialog == Dialog.DISABLED) { disabledDialog.render(context,width,height,mouseX,mouseY); return; }
        if (dialog == Dialog.LOCKED) {
            lockedRequirementsDialog.render(context, width, height, mouseX, mouseY);
            return;
        }
        if (dialog == Dialog.FOUND) {
            foundStructureDialog.render(context, width, height, mouseX, mouseY, foundStructureId, client.player,
                    showTeleportButton() ? this::teleport : null, this::dismissDialog, () -> client.setScreen(null));
            return;
        }
        if (isSearchFailureDialog()) {
            searchFailureDialog.render(context, width, height, mouseX, mouseY, this::dismissDialog,
                    showNotFoundHide() ? () -> toggleStructureHidden(searchRequest.structure) : null, () -> isStructureHidden(searchRequest.structure));
            return;
        }
        searchStatusDialog.render(context, width, height, mouseX, mouseY, this::dismissDialog);
    }

	public boolean isStructureHidden(Identifier id) {
		return ExplorersCompassConfig.hiddenStructures.contains(id.toString());
	}

	public int rowActionButtonWidth() {
		return textRenderer.getWidth("filters") + StructureSearchList.ACTION_BUTTON_HORIZONTAL_PADDING;
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
		filterPanel.invalidate();
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
		List<Identifier> structures = new ArrayList<>(structureIDsMatchingSearch);
		List<SortCriterion> order = sortPriority.order();
		Collator collator = englishCollator();
		structures.sort((first, second) -> {
			for (SortCriterion criterion : order) {
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
				IconButton.drawWithShadow(context, CLEAR_FILTERS_ICON, iconX, iconY, size, brightness);
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
			if (sortPriority.isOpen()) {
				sortPriority.close();
			} else {
				sortPriority.open();
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
		cancelButton = addDrawableChild(IconButton.close(width - 30, () -> client.setScreen(null)));

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
		excludeButton.setRightInset(FilterPanel.FILTER_CHECKBOX_SIZE + EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP);
		excludeVisitedButton = excludeButton;
		TransparentButton lockedButton = addDrawableChild(new TransparentButton(10, 10,
				excludeButtonWidth("Exclude Locked"), 20, Text.literal("Exclude Locked"), button -> {
			FilterSelections.toggleExcludeLocked();
			clearCurrentStructureReveal();
			showNormalResults();
			processSearchTerm();
		}));
		lockedButton.setRightInset(FilterPanel.FILTER_CHECKBOX_SIZE + EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP);
		excludeLockedButton = lockedButton;
        TransparentButton newlyButton = addDrawableChild(new TransparentButton(10, 40,
                excludeButtonWidth("Newly Unlocked"), 20, Text.literal("Newly Unlocked"), button -> {
            FilterSelections.toggleNewlyUnlocked();
            clearCurrentStructureReveal();
            showNormalResults();
            processSearchTerm();
        }));
        newlyButton.setRightInset(FilterPanel.FILTER_CHECKBOX_SIZE + EXCLUDE_BUTTON_TEXT_CHECKBOX_GAP);
        newlyUnlockedButton = newlyButton;
		backButton = addDrawableChild(new TransparentButton(10, 10, buttonWidthForLabel("Back"), 20, Text.literal("Back"), (onPress) -> {
			if (hiddenStructuresOpen) {
				leaveHiddenStructures();
			} else {
				setFiltersOpen(false);
			}
		}));
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
		String filterQuery = filterSearchTextField == null ? "" : filterSearchTextField.getText();
		int filterSearchX = filterSearchX();
		filterSearchTextField = new TransparentTextField(textRenderer, filterSearchX, 10,
				filterSearchWidth(filterSearchX), 20, Text.literal("Search Filters"));
		filterSearchTextField.setText(filterQuery);
		filterSearchTextField.setChangedListener(filterPanel::queryChanged);
		addDrawableChild(filterSearchTextField);
		filterSearchTextField.setVisible(filtersOpen);
		int headerEnd = backButton.getX() - HEADER_GAP;
		int hiddenSearchX = Math.min(10 + textRenderer.getWidth(HiddenStructuresList.TITLE) + 10,
				Math.max(10, headerEnd - 80));
		hiddenSearchTextField = new TransparentTextField(textRenderer, hiddenSearchX, 10,
				Math.max(1, headerEnd - hiddenSearchX),
				20, Text.literal("Search hidden structures"));
		hiddenSearchTextField.setMaxLength(256);
		hiddenSearchTextField.setText(hiddenStructures.query());
		hiddenSearchTextField.setChangedListener(hiddenStructures::setQuery);
		addDrawableChild(hiddenSearchTextField);
		hiddenSearchTextField.setVisible(hiddenStructuresOpen);
	}

	private void openHiddenStructures(Identifier target) {
		if (isStructureView()) remove(selectionList);
		hiddenStructuresOpen = true;
		searchTextField.setVisible(false);
		if (target != null) hiddenSearchTextField.setText(target.toString());
		hiddenStructures.setScroll(0);
		updateViewButtons();
		focusActiveSearchField();
	}

	private void leaveHiddenStructures() {
		hiddenStructuresOpen = false;
		draggedScrollbar = null;
		hiddenSearchTextField.setText("");
		hiddenSearchTextField.setFocused(false);
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

	private int sidebarWidth() {
		return textRenderer.getWidth(title) + 2 * (HEADER_TITLE_MARGIN - SIDEBAR_X);
	}

	private int structureListX() {
		return SIDEBAR_X + sidebarWidth() + HEADER_GAP;
	}

	private int excludeButtonWidth(String label) {
		return textRenderer.getWidth(label) + FilterPanel.FILTER_CHECKBOX_SIZE
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
		teleportButton.setY(currentStructureButton.getY() + SIDEBAR_BUTTON_STEP);
		backButton.setX(cancelButton.getX() - backButton.getWidth() - 5);
		clearFiltersTopButton.setX(width - 10 - clearFiltersTopButton.getWidth());
		clearFiltersTopButton.setY(40);
		excludeVisitedButton.setX(Scrollbar.WIDTH);
		excludeVisitedButton.setY(40);
		excludeLockedButton.setY(40);
        newlyUnlockedButton.setY(40);
		if (searchTextField != null) {

			searchTextField.setWidth(Math.max(1, cancelButton.getX() - searchTextField.getX() - HEADER_GAP));
		}
	}

	private void updateViewButtons() {
		searchButton.visible = isStructureView();
		updateSearchButtons();
		sortOrderButton.visible = isStructureView();
		filtersButton.visible = isStructureView();
		currentStructureButton.visible = isStructureView();
		currentStructureButton.active = ClientPlayNetworking.canSend(CurrentStructurePacket.ID);
		clearFiltersButton.visible = isStructureView();
		cancelButton.visible = true;
		teleportButton.visible = isStructureView() && showTeleportButton();
		clearFiltersTopButton.visible = filtersOpen;
		excludeVisitedButton.visible = filtersOpen && ExplorersCompass.canExcludeVisited;
		excludeLockedButton.visible = filtersOpen && !ExplorersCompass.lockedRequirements.isEmpty();
		int excludeLockedX = Scrollbar.WIDTH + (excludeVisitedButton.visible ? excludeVisitedButton.getWidth() + 5 : 0);
		excludeLockedButton.setX(excludeLockedX);
		newlyUnlockedButton.setX(excludeLockedX + (excludeLockedButton.visible ? excludeLockedButton.getWidth() + 5 : 0));
        newlyUnlockedButton.visible = filtersOpen && io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers.showNewlyUnlockedFilter;
		backButton.visible = filtersOpen || hiddenStructuresOpen;
		if (hiddenSearchTextField != null) hiddenSearchTextField.setVisible(hiddenStructuresOpen);
	}

}
