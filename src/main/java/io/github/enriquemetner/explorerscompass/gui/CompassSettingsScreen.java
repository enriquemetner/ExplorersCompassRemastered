package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentButton;
import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentTextField;
import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.ClientSetting;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.config.OverlaySide;
import io.github.enriquemetner.explorerscompass.client.RenderUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class CompassSettingsScreen extends Screen {
	private static final int SMALL_BUTTON_SIZE = 20;
	private static final int SEARCH_BUTTON_GAP = 5;
	private static final int HEADER_GAP = 10;
	private static final int HEADER_TITLE_MARGIN = 16;
	private static final int HEADER_BUTTON_TEXT_PADDING = 20;
	private static final int SETTINGS_ROW_HEIGHT = 25;
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
	private static final int SETTINGS_CONFIG_DIRECTORY_ROW = 20;
	private static final int SETTINGS_PAUSE_ROW = 17;
	private static final int SETTINGS_CREATIVE_UNLOCK_ROW = 15;
	private static final int SETTINGS_TELEPORT_ROW = 16;
	private static final int SETTINGS_DIVIDER_COLOR = 0xff909090;
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
		HiddenStructuresList.TITLE,
		"Config Directory",
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
		"Open the Explorer's Compass config folder for this Minecraft instance.",
	};
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

	private final Screen parent;
	private TransparentTextField searchField;
	private ButtonWidget hiddenStructuresButton;
	private ButtonWidget configDirectoryButton;
	private int settingsFirstRow;
	private List<Integer> settingsRows = List.of();
	private boolean draggingScrollbar;
	private double grabOffset;

	public CompassSettingsScreen(Screen parent) {
		super(Text.literal("Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		String query = searchField == null ? "" : searchField.getText();
		int backWidth = buttonWidthForLabel("Back");
		int closeX = width - 30;
		int backX = closeX - SEARCH_BUTTON_GAP - backWidth;
		int restoreRight = backX - SEARCH_BUTTON_GAP;
		int titleEnd = textRenderer.getWidth(title) + 2 * HEADER_TITLE_MARGIN;
		int restoreWidth = buttonWidthForLabel(SETTINGS_RESET_ALL_LABEL) + SETTINGS_RESET_ICON_SIZE + SETTINGS_RESET_LABEL_GAP;
		int restoreX = restoreRight - restoreWidth;
		int searchX = Math.min(titleEnd, Math.max(HEADER_TITLE_MARGIN, restoreX - HEADER_GAP - 48));

		addDrawableChild(new TransparentButton(backX, 10, backWidth, 20, Text.literal("Back"), button -> close()));
		addDrawableChild(ExplorersCompassScreen.createCloseButton(closeX, this::closeSettingsFlow));
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
		}));
		showOnlyMatchingFilterOptionsButton = addDrawableChild(new TransparentButton(controlX,
				settingsRowY(SETTINGS_MATCHING_FILTER_OPTIONS_ROW), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.showOnlyMatchingFilterOptions = !ExplorersCompassConfig.showOnlyMatchingFilterOptions;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
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
		}));
		adjustRowsToFitViewButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(5), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.adjustRowsToFitView = !ExplorersCompassConfig.adjustRowsToFitView;
			ExplorersCompassConfig.saveClient();
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
				SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.literal("Open"),
				button -> client.setScreen(new HiddenStructuresScreen(this))));
		configDirectoryButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(SETTINGS_CONFIG_DIRECTORY_ROW),
				SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.literal("Open"),
				button -> Util.getOperatingSystem().open(ExplorersCompassConfig.configDirectory().toFile())));

		settingResetButtons.clear();
		draggingScrollbar = false;
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

		var restoreDefaultsButton = addDrawableChild(new TransparentButton(restoreX, 10, restoreWidth, 20,
				Text.literal("Restore Defaults"), ignored -> restoreSettings(ExplorersCompassConfig::restoreClientDefaults)) {
			@Override
			protected void renderLabel(DrawContext context, TextRenderer renderer) {
				int iconOffset = renderer.getWidth(SETTINGS_RESET_ALL_LABEL) + SETTINGS_RESET_LABEL_GAP;
				int contentWidth = iconOffset + SETTINGS_RESET_ICON_SIZE;
				int x = getX() + (getWidth() - contentWidth) / 2;
				context.drawTextWithShadow(renderer, SETTINGS_RESET_ALL_LABEL, x,
						getY() + (getHeight() - 8) / 2, 0xffffff);
				drawSettingsResetIcon(context, x + iconOffset, getY(), getHeight());
			}
		});
		restoreDefaultsButton.setTooltip(Tooltip.of(restoreDefaultsButton.getMessage()));

		searchField = addDrawableChild(new TransparentTextField(textRenderer, searchX, 10,
				Math.max(1, restoreX - HEADER_GAP - searchX), 20, Text.literal("Search Settings")));
		searchField.setMaxLength(256);
		searchField.setText(query);
		searchField.setChangedListener(value -> {
			settingsFirstRow = 0;
			draggingScrollbar = false;
			updateRows();
		});
		updateRows();
		setInitialFocus(searchField);
	}

	@Override
	public void close() {
		client.setScreen(parent);
		if (parent instanceof ExplorersCompassScreen compass) compass.resumeFromSettings();
	}

	void closeSettingsFlow() {
		client.setScreen(parent);
		if (parent instanceof ExplorersCompassScreen compass) compass.close();
	}

	@Override
	public boolean shouldPause() {
		return parent != null && parent.shouldPause();
	}

	@Override
	public void tick() { searchField.tick(); }

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		renderSettingsPanel(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(title.getString(),
				Math.max(1, searchField.getX() - HEADER_TITLE_MARGIN - 4)), HEADER_TITLE_MARGIN, 15, 0xffffff);
		renderSettingsTooltip(context, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Scrollbar bar = scrollbar();
		if (button == 0 && bar.contains(mouseX, mouseY)) {
			draggingScrollbar = true;
			grabOffset = bar.grabOffset(mouseY, 0);
			if (bar.outsideThumb(mouseY, 0)) moveScrollbar(mouseY);
			return true;
		}
		if (button == 1 && searchField.isMouseOver(mouseX, mouseY)) {
			searchField.setText("");
			setFocused(searchField);
			return true;
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (handled && client.currentScreen == this) setFocused(searchField);
		return handled;
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		if (button == 0 && draggingScrollbar) {
			moveScrollbar(y);
			return true;
		}
		return super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if (button == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		settingsFirstRow = Scrollbar.scrollRows(settingsFirstRow, amount, 1, settingsMaxFirstRow());
		updateRows();
		return true;
	}

	private void moveScrollbar(double y) {
		settingsFirstRow = (int) Math.round(scrollbar().offsetForMouse(y, grabOffset));
		updateRows();
	}

	private int filterLeft() { return Scrollbar.WIDTH; }
	private int filterRight() { return width - Scrollbar.WIDTH; }
	private int filterTop() { return 40; }
	private int filterBottom() { return height - 10; }

	private int buttonWidthForLabel(String label) {
		return textRenderer.getWidth(label) + HEADER_BUTTON_TEXT_PADDING;
	}

	private Scrollbar scrollbar() {
		return new Scrollbar(filterRight(), filterTop(), filterBottom(),
				Scrollbar.rowThumbHeight(filterBottom() - filterTop(), settingsVisibleRows(), settingsRows.size()),
				settingsFirstRow, settingsMaxFirstRow());
	}

	private void restoreSettings(Runnable reset) {
		reset.run();
		ExplorersCompassConfig.saveClient();
		updateSettingsButtonLabels();
		updateRows();
	}

	private void updateRows() {
		boolean showCosts = client.world == null || SearchCosts.remote().showsSearchCosts();
		settingsRows = SettingsSearch.matchingRows(SETTINGS_LABELS, this::isSettingsDivider, searchField.getText(),
				row -> row != SETTINGS_HIDE_SEARCH_COST_ROW || showCosts);
		settingsFirstRow = MathHelper.clamp(settingsFirstRow, 0, settingsMaxFirstRow());
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
		placeSetting(configDirectoryButton, SETTINGS_CONFIG_DIRECTORY_ROW);
		settingResetButtons.forEach((row, button) -> placeSetting(button, row));
	}

	private void placeSetting(ButtonWidget button, int row) {
		button.setY(settingsRowY(row) + (SETTINGS_ROW_HEIGHT - settingsControlHeight()) / 2);
		button.visible = isSettingsRowVisible(row);
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
		scrollbar().render(context);
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
			protected void renderLabel(DrawContext context, TextRenderer renderer) {
				int x = getX() + (getWidth() - SETTINGS_RESET_ICON_SIZE) / 2;
				drawSettingsResetIcon(context, x, getY(), getHeight());
			}
		};
		button.setTooltip(Tooltip.of(label));
		settingResetButtons.put(row, addDrawableChild(button));
	}

	private static void drawSettingsResetIcon(DrawContext context, int x, int top, int height) {
		int y = top + (height - SETTINGS_RESET_ICON_SIZE) / 2;
		ExplorersCompassScreen.drawIconWithShadow(context, SETTINGS_RESET_ICON, x, y, SETTINGS_RESET_ICON_SIZE, 1.0f);
	}

	private static Text onOff(boolean enabled) {
		return Text.literal(enabled ? "On" : "Off");
	}
}
