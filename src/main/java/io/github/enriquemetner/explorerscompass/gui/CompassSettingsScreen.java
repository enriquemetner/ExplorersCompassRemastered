package io.github.enriquemetner.explorerscompass.gui;

import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.adjustRowsToFitView;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.checkedFiltersFirst;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.clearFiltersOnClose;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.clearSearchBarOnClose;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.closeOnFound;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.creativeUnlocksAll;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.displayWithChatOpen;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.hideSearchCost;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.matchSearchWordsInAnyOrder;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.overlayLineOffset;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.overlaySide;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.pauseGameWhenOpen;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.saveClient;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.showOnlyMatchingFilterOptions;
import static io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.showTeleportOnlyInCreative;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.client.RenderUtils;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig.ClientSetting;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.config.OverlaySide;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.gui.widgets.IconButton;
import io.github.enriquemetner.explorerscompass.gui.widgets.Scrollbar;
import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentButton;
import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentTextField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
	private static final int SETTINGS_DIVIDER_COLOR = 0xff909090;

	record SettingRow(String label, String tooltip, ClientSetting reset, int tabOrder,
					  Supplier<Text> value, Runnable activate) {
		static SettingRow section(String label) {
			return new SettingRow(label, null, null, -1, null, null);
		}

		boolean divider() { return tooltip == null; }
		boolean offset() { return reset == ClientSetting.OVERLAY_LINE_OFFSET; }
		boolean available(boolean showCosts) { return reset != ClientSetting.HIDE_SEARCH_COST || showCosts; }
	}

	private final Map<SettingRow, List<ButtonWidget>> controls = new LinkedHashMap<>();
	private final Map<SettingRow, ButtonWidget> resetButtons = new LinkedHashMap<>();
	private final List<SettingRow> rows = createRows();
	private final Screen parent;
	private TransparentTextField searchField;
	private int settingsFirstRow;
	private List<SettingRow> settingsRows = List.of();
	private boolean draggingScrollbar;
	private double grabOffset;

	public CompassSettingsScreen(Screen parent) {
		super(Text.literal("Settings"));
		this.parent = parent;
	}

	List<SettingRow> matchingRows(String query, boolean showCosts) {
		return SettingsSearch.matchingRows(rows, SettingRow::label, SettingRow::divider, query, row -> row.available(showCosts));
	}

	private List<SettingRow> createRows() {
		return List.of(
			SettingRow.section("HUD"),
			new SettingRow("HUD Side", "Choose the position of the compass pointing information on the screen.", ClientSetting.OVERLAY_SIDE, 0,
					() -> Text.literal(overlaySide == OverlaySide.LEFT ? "Left" : "Right"),
					() -> { overlaySide = overlaySide == OverlaySide.LEFT ? OverlaySide.RIGHT : OverlaySide.LEFT; saveClient(); }),
			new SettingRow("Vertical HUD offset",
					"Move the compass pointing information vertically.\nHigher values move it down;\nLower values move it up.", ClientSetting.OVERLAY_LINE_OFFSET, 1, null, null),
			toggle("Show While Chat Is Open",
					"Keep compass pointing information visible while the chat is shown.",
					ClientSetting.DISPLAY_WITH_CHAT_OPEN, 2,
					() -> displayWithChatOpen, value -> displayWithChatOpen = value),
			SettingRow.section("View"),
			toggle("Adjust Rows to Fit View",
					"Adjusts the spacing of rows, showing less content but only fully visible list entries.",
					ClientSetting.ADJUST_ROWS_TO_FIT_VIEW, 9,
					() -> adjustRowsToFitView, value -> adjustRowsToFitView = value),
			toggle("Checked Filters On Top",
					"Show checked filter options separately above unchecked ones.",
					ClientSetting.CHECKED_FILTERS_FIRST, 5,
					() -> checkedFiltersFirst, value -> checkedFiltersFirst = value),
			toggle("Hide Search Costs",
					"Hide all structure search costs from the list.\nReasonable when all structures have the same search cost.",
					ClientSetting.HIDE_SEARCH_COST, 7,
					() -> hideSearchCost, value -> hideSearchCost = value),
			SettingRow.section("Behavior"),
			toggle("Close UI When located",
					"Immediately close the compass screen when the compass finds a structure.",
					ClientSetting.CLOSE_ON_FOUND, 10,
					() -> closeOnFound, value -> closeOnFound = value),
			toggle("Clear Search Bar on Close",
					"Clear the search bar when you close the screen.",
					ClientSetting.CLEAR_SEARCH_BAR_ON_CLOSE, 3,
					() -> clearSearchBarOnClose, value -> clearSearchBarOnClose = value),
			toggle("Clear Filters on Close",
					"Clear selected filters when you close the compass screen.",
					ClientSetting.CLEAR_FILTERS_ON_CLOSE, 4,
					() -> clearFiltersOnClose, value -> clearFiltersOnClose = value),
			toggle("Flexible Search",
					"Match every typed word fragment contained in a structure name, in any order.\nExample: \"vil pla\" can match \"Village Plains\".",
					ClientSetting.MATCH_SEARCH_WORDS_IN_ANY_ORDER, 8,
					() -> matchSearchWordsInAnyOrder, value -> matchSearchWordsInAnyOrder = value),
			toggle("Show Only Matching Filter Options",
					"Hide filter options which would produce an empty list if checked.\nExample: Checking \"The Nether\" would hide the \"Villages\" set.",
					ClientSetting.SHOW_ONLY_MATCHING_FILTER_OPTIONS, 6,
					() -> showOnlyMatchingFilterOptions, value -> showOnlyMatchingFilterOptions = value),
			SettingRow.section("Creative & Singleplayer"),
			toggle("Unlock All Structures in Creative",
					"Unlock all structures while in Creative mode.\nChanging the mode back relocks the right structures.",
					ClientSetting.CREATIVE_UNLOCKS_ALL, 12,
					() -> creativeUnlocksAll, value -> creativeUnlocksAll = value),
			toggle("Show Teleport Only in Creative",
					"Hide the teleport button in any game mode other than Creative.",
					ClientSetting.SHOW_TELEPORT_ONLY_IN_CREATIVE, 13,
					() -> showTeleportOnlyInCreative, value -> showTeleportOnlyInCreative = value),
			toggle("Pause Game While Open",
					"Pauses your Singleplayer world while the compass screen is open.",
					ClientSetting.PAUSE_GAME_WHEN_OPEN, 11,
					() -> pauseGameWhenOpen, value -> pauseGameWhenOpen = value),
			SettingRow.section("Other"),
			new SettingRow(HiddenStructuresList.TITLE, "View and restore structures hidden on this client.", null, 14,
					() -> Text.literal("Open"), () -> client.setScreen(new HiddenStructuresScreen(this))),
			new SettingRow("Config Directory", "Open the Explorer's Compass config folder for this Minecraft instance.", null, 15,
					() -> Text.literal("Open"), () -> Util.getOperatingSystem().open(ExplorersCompassConfig.configDirectory().toFile()))
		);
	}

	private static SettingRow toggle(String label, String tooltip, ClientSetting reset, int tabOrder,
									 BooleanSupplier value, Consumer<Boolean> change) {
		return new SettingRow(label, tooltip, reset, tabOrder, () -> onOff(value.getAsBoolean()), () -> {
			change.accept(!value.getAsBoolean());
			saveClient();
		});
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
		addDrawableChild(IconButton.close(closeX, this::closeSettingsFlow));
		controls.clear();
		resetButtons.clear();
		draggingScrollbar = false;
		// Preserve the existing tab traversal independently of visual row order.
		rows.stream().filter(row -> !row.divider()).sorted(Comparator.comparingInt(SettingRow::tabOrder))
				.forEach(row -> controls.put(row, createControls(row)));
		for (SettingRow row : rows) {
			if (row.reset() != null) resetButtons.put(row, addSettingResetButton(row));
		}

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

	private List<ButtonWidget> createControls(SettingRow row) {
		int x = settingsControlX(), y = settingsRowY(row);
		if (row.offset()) {
			var decrease = addDrawableChild(new TransparentButton(x, y, 20, settingsControlHeight(), Text.literal("−"), button -> {
				if (overlayLineOffset > 0) {
					overlayLineOffset--;
					saveClient();
				}
			}));
			var increase = addDrawableChild(new TransparentButton(x + SETTINGS_CONTROL_WIDTH - 20, y, 20,
					settingsControlHeight(), Text.literal("+"), button -> {
				if (overlayLineOffset < Integer.MAX_VALUE) {
					overlayLineOffset++;
					saveClient();
				}
			}));
			return List.of(decrease, increase);
		}
		return List.of(addDrawableChild(new TransparentButton(x, y, SETTINGS_CONTROL_WIDTH, settingsControlHeight(),
				row.value().get(), button -> {
			row.activate().run();
			updateSettingsButtonLabels();
		})));
	}

	private void updateRows() {
		boolean showCosts = client.world == null || SearchCosts.remote().showsSearchCosts();
		settingsRows = matchingRows(searchField.getText(), showCosts);
		settingsFirstRow = MathHelper.clamp(settingsFirstRow, 0, settingsMaxFirstRow());
		controls.forEach((row, buttons) -> buttons.forEach(button -> placeSetting(button, row)));
		resetButtons.forEach((row, button) -> placeSetting(button, row));
	}

	private void placeSetting(ButtonWidget button, SettingRow row) {
		button.setY(settingsRowY(row) + (SETTINGS_ROW_HEIGHT - settingsControlHeight()) / 2);
		button.visible = isSettingsRowVisible(row);
	}

	private int settingsRowY(SettingRow row) {
		return filterTop() + (settingsRows.indexOf(row) - settingsFirstRow) * SETTINGS_ROW_HEIGHT;
	}

	private int settingsVisibleRows() {
		return Math.max(1, (filterBottom() - filterTop()) / SETTINGS_ROW_HEIGHT);
	}

	private int settingsMaxFirstRow() {
		return Math.max(0, settingsRows.size() - settingsVisibleRows());
	}

	private boolean isSettingsRowVisible(SettingRow row) {
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

	private void renderSettingsDivider(DrawContext context, SettingRow row) {
		String title = row.label();
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
			SettingRow row = settingsRows.get(index);
			if (row.divider()) {
				renderSettingsDivider(context, row);
				continue;
			}
			String label = row.label();
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
		SettingRow offsetRow = rows.stream().filter(SettingRow::offset).findFirst().orElseThrow();
		if (isSettingsRowVisible(offsetRow)) {
			context.getMatrices().push();
			context.getMatrices().translate(settingsControlX() + SETTINGS_CONTROL_WIDTH / 2.0,
					settingsRowY(offsetRow) + (SETTINGS_ROW_HEIGHT - textRenderer.fontHeight * offsetScale) / 2.0, 0);
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
		SettingRow row = settingsRows.get(rowIndex);
		if (row.divider()) return;
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		String label = row.label();
		int textWidth = textRenderer.getWidth(label);
		float scale = Math.min(1.0f, (float) labelWidth / Math.max(1, textWidth));
		double textTop = settingsRowY(row) + (SETTINGS_ROW_HEIGHT - textRenderer.fontHeight) / 2.0f
				+ textRenderer.fontHeight * (1.0f - scale) / 2.0f;
		if (mouseX < labelX || mouseX >= labelX + textWidth * scale
				|| mouseY < textTop || mouseY >= textTop + textRenderer.fontHeight * scale) {
			return;
		}
		List<Text> lines = new ArrayList<>();
		for (OrderedText line : textRenderer.wrapLines(Text.literal(row.tooltip()), Math.min(240, width - 30))) {
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
		controls.forEach((row, buttons) -> {
			if (row.value() != null) buttons.get(0).setMessage(row.value().get());
		});
	}

	private ButtonWidget addSettingResetButton(SettingRow row) {
		Text label = Text.literal("Restore default for " + row.label());
		var button = new TransparentButton(settingsResetX(), settingsRowY(row), SMALL_BUTTON_SIZE,
				settingsControlHeight(), label, ignored -> restoreSettings(row.reset()::restoreDefault)) {
			@Override
			protected void renderLabel(DrawContext context, TextRenderer renderer) {
				int x = getX() + (getWidth() - SETTINGS_RESET_ICON_SIZE) / 2;
				drawSettingsResetIcon(context, x, getY(), getHeight());
			}
		};
		button.setTooltip(Tooltip.of(label));
		return addDrawableChild(button);
	}

	private static void drawSettingsResetIcon(DrawContext context, int x, int top, int height) {
		int y = top + (height - SETTINGS_RESET_ICON_SIZE) / 2;
		IconButton.drawWithShadow(context, SETTINGS_RESET_ICON, x, y, SETTINGS_RESET_ICON_SIZE, 1.0f);
	}

	private static Text onOff(boolean enabled) {
		return Text.literal(enabled ? "On" : "Off");
	}
}
