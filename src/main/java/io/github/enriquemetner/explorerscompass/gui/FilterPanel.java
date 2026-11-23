package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.client.RenderUtils;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.gui.widgets.Scrollbar;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

final class FilterPanel {
	private static final int PREFERRED_FILTER_ROW_HEIGHT = 20;
	private static final int FILTER_HEADER_HEIGHT = 20;
	private static final int FILTER_HEADER_GAP = 1;
	private static final int FILTER_COLUMN_GAP = 1;
	private static final int FILTER_SECTION_GAP = 1;
	static final int FILTER_CHECKBOX_SIZE = 9;
	private static final Identifier CHECKBOX_CHECKED = new Identifier(ExplorersCompass.MODID, "textures/gui/checkbox_checked.png");
	private static final Identifier CHECKBOX_UNCHECKED = new Identifier(ExplorersCompass.MODID, "textures/gui/checkbox_unchecked.png");

	private final List<List<FilterOption>> filterOptions = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	private final Predicate<Identifier> passesStructureVisibility;
	private final Runnable beforeToggle;
	private final Runnable afterToggle;
	private final Runnable clearSelectedOnly;
	private FilterSelections.AvailableOptions availableFilterOptions;
	private List<Identifier> allowedStructureIDs;
	private Identifier dimensionId;
	private TextRenderer textRenderer;
	private int width;
	private int height;
	private boolean open;
	private boolean showSourceColumn;
	private double filterScroll;
	private String query = "";

	FilterPanel(Predicate<Identifier> passesStructureVisibility, Runnable beforeToggle,
				Runnable afterToggle, Runnable clearSelectedOnly) {
		this.passesStructureVisibility = passesStructureVisibility;
		this.beforeToggle = beforeToggle;
		this.afterToggle = afterToggle;
		this.clearSelectedOnly = clearSelectedOnly;
	}

	void resize(TextRenderer font, int width, int height) {
		this.textRenderer = font;
		this.width = width;
		this.height = height;
	}

	void setStructures(List<Identifier> ids, Identifier dimension) {
		allowedStructureIDs = ids;
		dimensionId = dimension;
		showSourceColumn = ids.isEmpty() || ids.stream().anyMatch(id -> !"minecraft".equals(id.getNamespace()));
	}

	boolean showsSource() { return showSourceColumn; }

	void setOpen(boolean open) { this.open = open; }

	void invalidate() { availableFilterOptions = null; }

	void queryChanged(String value) {
		query = value.toLowerCase(Locale.ROOT);
		filterScroll = 0;
	}

	void setScroll(double offset) { filterScroll = offset; }

	void clampScroll() {
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	Scrollbar scrollbar() {
		int maxScroll = filterMaxScroll();
		int trackHeight = filterBottom() - filterTop();
		return new Scrollbar(filterRight(), filterTop(), filterBottom(),
				Scrollbar.pixelThumbHeight(trackHeight, maxScroll, 32, 8), filterScroll, maxScroll);
	}

	boolean scroll(double mouseX, double mouseY, double amount) {
		if (mouseX < filterLeft() || mouseX >= filterScrollbarRight() || mouseY < filterTop() || mouseY >= filterBottom()) return false;
		filterScroll = Scrollbar.scrollPixels(filterScroll, amount, filterRowHeight(), filterMaxScroll());
		return true;
	}

	boolean click(double mouseX, double mouseY, Runnable clickSound) {
		int column = filterColumnAt(mouseX, mouseY);
		if (column < 0 || mouseY < filterRowsTop()) return false;
		List<FilterOption> options = visibleFilterOptions(column);
		int index = filterOptionIndexAt(mouseY, options, filterSectionStart(column, options));
		if (index >= 0) {
			clickSound.run();
			toggle(column, options.get(index));
		}
		return true;
	}

	record FilterOption(String label, String source, Identifier id, boolean current) {}

	private int filterLeft() {
		return Scrollbar.WIDTH;
	}

	private int filterRight() {
		return width - Scrollbar.WIDTH;
	}

	private int filterScrollbarRight() {
		return filterRight() + Scrollbar.WIDTH;
	}

	private int filterTop() {
		return open ? 70 : 40;
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
		List<FilterOption> visible = filterOptions.get(column);
		if (ExplorersCompassConfig.showOnlyMatchingFilterOptions) {
			if (availableFilterOptions == null) {
				availableFilterOptions = FilterSelections.availableOptions(allowedStructureIDs,
						ExplorersCompass.structureIDsToSetIDs, ExplorersCompass.allowedStructureIDsToDimensionIDs,
						passesStructureVisibility);
			}
			visible = visible.stream().filter(option -> checked(column, option) || switch (column) {
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
			if (checked(column, option)) {
				ordered.add(option);
			}
		}
		for (FilterOption option : visible) {
			if (!checked(column, option)) {
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
			if (checked(column, option)) {
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

	void rebuild() {
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
		if (!showSourceColumn && !FilterSelections.sources.isEmpty()) {
			FilterSelections.sources.clear();
			clearSelectedOnly.run();
		}
		dimensions.add(dimensionId);
		filterOptions.get(0).addAll(sources.values());
		for (Identifier dimension : dimensions) {
			filterOptions.get(1).add(new FilterOption(StructureUtils.getDimensionName(dimension), null, dimension, false));
		}
		for (Identifier set : sets) {
			filterOptions.get(2).add(new FilterOption(StructureUtils.formatIdPath(set), null, set, false));
		}
		Collator collator = Collator.getInstance(Locale.US);
		Comparator<FilterOption> alphabetical = Comparator.comparing((FilterOption option) -> option.label, collator)
				.thenComparing(option -> option.source != null ? option.source : option.id.toString());
		for (List<FilterOption> options : filterOptions) {
			options.sort(alphabetical);
		}
		filterOptions.get(0).sort(Comparator.comparing((FilterOption option) -> !"minecraft".equals(option.source)).thenComparing(alphabetical));
		filterOptions.get(1).add(0, new FilterOption(Text.literal("Current").getString(), null, dimensionId, true));
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	static boolean checked(int column, FilterOption option) {
		if (option.current) {
			return FilterSelections.isCurrentSelected();
		}
		return switch (column) {
			case 0 -> FilterSelections.sources.contains(option.source);
			case 1 -> FilterSelections.dimensions.contains(option.id);
			default -> FilterSelections.sets.contains(option.id);
		};
	}

	void toggle(int column, FilterOption option) {
		beforeToggle.run();
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
		afterToggle.run();
		clampScroll();
	}

	private static <T> void toggleSet(Set<T> values, T value) {
		if (!values.add(value)) {
			values.remove(value);
		}
	}

	static void drawFilterCheckbox(DrawContext context, int x, int y, boolean checked) {
		context.drawTexture(checked ? CHECKBOX_CHECKED : CHECKBOX_UNCHECKED, x, y, 0, 0,
				FILTER_CHECKBOX_SIZE, FILTER_CHECKBOX_SIZE, FILTER_CHECKBOX_SIZE, FILTER_CHECKBOX_SIZE);
	}

	void render(DrawContext context, int mouseX, int mouseY) {
		clampScroll();
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
				drawFilterCheckbox(context, checkboxX, rowY + (rowHeight - FILTER_CHECKBOX_SIZE) / 2, checked(column, option));
			}
			context.disableScissor();
		}
		scrollbar().render(context);
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

}
