package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.compat.jes.JesThumbnails;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.util.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class StructureSearchList extends AlwaysSelectedEntryListWidget<StructureSearchEntry> {
	static final int SCROLLBAR_WIDTH = Scrollbar.WIDTH;
	static final int ACTION_BUTTON_HEIGHT = 16;
	static final int ACTION_BUTTON_GAP = 2;
	static final int ACTION_BUTTONS_HEIGHT = ACTION_BUTTON_HEIGHT * 2 + ACTION_BUTTON_GAP;
	private static final long DOUBLE_CLICK_INTERVAL_MS = 250L;

	private final ExplorersCompassScreen parentScreen;
	private final StructureSelection selection;
	private Identifier lastPlainClick;
	private long lastPlainClickTime;
	private boolean draggingScrollbar;
	private double scrollbarGrabOffset;

	public StructureSearchList(ExplorersCompassScreen parentScreen, MinecraftClient client, StructureSelection selection, int x, int y, int width, int height, int slotHeight) {
		super(client, width, height, y, y + height, ExplorersCompassConfig.adjustRowsToFitView ? fittedRowHeight(height, slotHeight) : slotHeight);
		this.parentScreen = parentScreen;
		this.selection = selection;
		// Cancel the list widget's default 4px top padding so rows meet the scrollbar track.
		headerHeight = -4;
        setLeftPos(x);
		refreshList();
	}

	private static int fittedRowHeight(int viewportHeight, int preferredRowHeight) {
		int visibleRows = Math.max(1, viewportHeight / preferredRowHeight);
		return Math.max(preferredRowHeight, viewportHeight / visibleRows);
	}

	private int fullRowsBottom() {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return bottom;
		}
		return Math.min(bottom, top + Math.max(1, (bottom - top) / itemHeight) * itemHeight);
	}

	// Give the last visible row the spare pixels left by whole-number row heights.
	private int displayedRowBottom(int index) {
		int end = getRowBottom(index);
		int fittedBottom = fullRowsBottom();
		return fittedBottom < bottom && getRowTop(index) < fittedBottom && end >= fittedBottom ? Math.max(bottom, end) : end;
	}

	@Override
	public int getMaxScroll() {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return super.getMaxScroll();
		}
		return Math.max(0, getEntryCount() * itemHeight - (fullRowsBottom() - top));
	}

	@Override
	protected int getScrollbarPositionX() {
		return left + width;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY)
				|| getMaxScroll() > 0 && mouseX >= getScrollbarPositionX() && mouseX < getScrollbarPositionX() + SCROLLBAR_WIDTH
						&& mouseY >= top && mouseY <= bottom;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		setScrollAmount(Scrollbar.scrollPixels(getScrollAmount(), amount, itemHeight, getMaxScroll()));
		return true;
	}

	private Scrollbar scrollbar() {
		int viewportHeight = bottom - top;
		int thumbHeight = Scrollbar.pixelThumbHeight(viewportHeight, getMaxPosition() - viewportHeight, 32, 8);
		return new Scrollbar(getScrollbarPositionX(), SCROLLBAR_WIDTH, top, bottom, thumbHeight, getScrollAmount(), getMaxScroll());
	}

	public boolean isScrollbarAt(double mouseX, double mouseY) {
		return scrollbar().contains(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX < getScrollbarPositionX() && mouseY >= fullRowsBottom() && mouseY < bottom) {
			// Vanilla resolves clicks using uniform row heights. Route the extension to its row.
			StructureSearchEntry row = getEntryAtPosition(mouseX, fullRowsBottom() - 1);
			if (row == null) {
				resetDoubleClick();
				return false;
			}
			if (!row.mouseClicked(mouseX, mouseY, button)) return false;
			setFocused(row);
			setDragging(button == 0);
			return true;
		}
		Scrollbar scrollbar = scrollbar();
		if (button == 0 && scrollbar.contains(mouseX, mouseY)) {
			resetDoubleClick();
			draggingScrollbar = true;
			scrollbarGrabOffset = scrollbar.grabOffset(mouseY, Scrollbar.GRAB_PADDING);
			if (scrollbar.outsideThumb(mouseY, Scrollbar.GRAB_PADDING)) {
				setScrollAmount(scrollbar.offsetForMouse(mouseY, scrollbarGrabOffset));
			}
			return true;
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (!handled) resetDoubleClick();
		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingScrollbar && button == 0) {
			setScrollAmount(scrollbar().offsetForMouse(mouseY, scrollbarGrabOffset));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (draggingScrollbar && button == 0) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public int getRowLeft() {
		return left;
	}

	@Override
	public int getRowWidth() {
		return width;
	}

	@Override
	protected boolean isSelectedEntry(int slotIndex) {
		return slotIndex >= 0 && slotIndex < getEntryCount() && selection.contains(getEntry(slotIndex).getStructureID());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
		int rowsBottom = fullRowsBottom();
        if (hasPreviews()) {
            int first = 0;
            while (first < getEntryCount() && getRowBottom(first) <= top) first++;
            int end = first;
            while (end < getEntryCount() && getRowTop(end) < rowsBottom) end++;
            parentScreen.jesThumbnails().prepare(getEntryCount(), first, end,
                    index -> getEntry(index).getStructureID());
        }
		context.enableScissor(left, top, right, bottom);
		if (getEntryCount() == 0) {
			context.drawCenteredTextWithShadow(client.textRenderer, "No matching results",
					left + width / 2, parentScreen.sortPriorityLabelY(), 0xffffff);
		}
        // Render backgrounds
        for (int i = 0; i < getEntryCount(); ++i) {
            if (getRowBottom(i) >= top && getRowTop(i) < rowsBottom) {
                StructureSearchEntry entry = getEntry(i);
				int opacity = parentScreen.isStructureLocked(entry.getStructureID())
						? RenderUtils.LOCKED_ENTRY_BACKGROUND_OPACITY_PERCENT
						: RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT;
                int fillColor = RenderUtils.blackWithOpacity(opacity);
                int rowLeft = getRowLeft();
                int rowTop = getRowTop(i);
                int rowBottom = displayedRowBottom(i);
                if (parentScreen.isUnlockedThroughParty(entry.getStructureID())) {
                    int labelX = entry.partyLabelX(rowControlsX());
                    int labelLeft = MathHelper.clamp(labelX, rowLeft, right);
                    int labelRight = MathHelper.clamp(labelX + entry.partyLabelWidth(), rowLeft, right);
                    int labelTop = rowTop + (rowBottom - rowTop - ACTION_BUTTONS_HEIGHT) / 2;
                    int labelBottom = labelTop + ACTION_BUTTONS_HEIGHT;
                    // Leave the panel area clear so its fill has the same opacity as a locked row.
                    context.fill(rowLeft, rowTop, right, labelTop, fillColor);
                    context.fill(rowLeft, labelBottom, right, rowBottom, fillColor);
                    context.fill(rowLeft, labelTop, labelLeft, labelBottom, fillColor);
                    context.fill(labelRight, labelTop, right, labelBottom, fillColor);
                } else {
                    context.fill(rowLeft, rowTop, right, rowBottom, fillColor);
                }
            }
        }
        // Render entries
        for (int i = 0; i < getEntryCount(); ++i) {
            int entryTop = getRowTop(i);
            int entryBottom = displayedRowBottom(i);
            if (entryBottom >= top && entryTop < rowsBottom) {
                StructureSearchEntry entry = getEntry(i);
                boolean isHovering = isMouseOver(mouseX, mouseY) && mouseY >= entryTop && mouseY < entryBottom;
				entry.render(context, i, entryTop, getRowLeft(), getRowWidth(), entryBottom - entryTop, mouseX, mouseY, isHovering, partialTicks);
				if (isSelectedEntry(i)) {
					context.fill(getRowLeft(), entryTop, getRowLeft() + 1, entryBottom, 0xffffffff);
				}
            }
        }
        context.disableScissor();
        // Render scrollbar
		scrollbar().render(context);
	}

    public void refreshList() {
        clearEntries();
        lastPlainClick = null;
        for (Identifier id : parentScreen.sortStructures()) addEntry(new StructureSearchEntry(this, id));
        setScrollAmount(0);
    }

    public void focusStructure(Identifier id) {
        if (id == null || parentScreen.isHiddenFromStructureList(id)) return;
        for (int index = 0; index < getEntryCount(); index++) {
            if (id.equals(getEntry(index).getStructureID())) {
                resetDoubleClick();
                selection.click(id, List.of(), false, false, false);
                setScrollAmount(getScrollAmount() + getRowTop(index) - top);
                return;
            }
        }
    }

    private List<Identifier> selectableRows() {
        boolean selectedOnly = parentScreen.isSelectedOnly();
        return children().stream().map(StructureSearchEntry::getStructureID)
                .filter(id -> !parentScreen.isHiddenFromStructureList(id) && (!selectedOnly || selection.contains(id))).toList();
    }

    void selectAllMatches() {
        lastPlainClick = null;
        selection.addAll(selectableRows());
    }

    void resetDoubleClick() {
        lastPlainClick = null;
    }

    boolean awaitingPlainDoubleClick() {
        return lastPlainClick != null && Util.getMeasuringTimeMs() - lastPlainClickTime < DOUBLE_CLICK_INTERVAL_MS;
    }

    /** Applies the click and returns whether it completes a plain double-click. */
    boolean selectClickedRow(Identifier id) {
        boolean control = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        boolean selectedOnly = parentScreen.isSelectedOnly();
        if (selectedOnly && (control || shift) && !selection.contains(id)) {
            resetDoubleClick();
            return false;
        }
        long now = Util.getMeasuringTimeMs();
        boolean doubleClick = !control && !shift && id.equals(lastPlainClick) && now - lastPlainClickTime < DOUBLE_CLICK_INTERVAL_MS;
        lastPlainClick = control || shift || doubleClick ? null : id;
        lastPlainClickTime = now;
        selection.click(id, shift && !selectedOnly ? selectableRows() : List.of(), control, shift, selectedOnly);
        return doubleClick;
    }

    public void selectRelative(int direction) {
        List<Identifier> rows = selectableRows();
        if (rows.isEmpty()) return;
        int current = selection.lead() == null ? -1 : rows.indexOf(selection.lead());
        int next = current < 0 ? 0 : MathHelper.clamp(current + direction, 0, rows.size() - 1);
        Identifier id = rows.get(next);
        lastPlainClick = null;
        selection.click(id, List.of(), false, false, false);
        for (int index = 0; index < getEntryCount(); index++) {
            if (!id.equals(getEntry(index).getStructureID())) continue;
            if (getRowTop(index) < top) {
                setScrollAmount(getScrollAmount() - (top - getRowTop(index)));
            } else if (getRowBottom(index) > fullRowsBottom()) {
                setScrollAmount(getScrollAmount() + getRowBottom(index) - fullRowsBottom());
            }
            break;
        }
    }

	public StructureSearchEntry soleSearchableEntry() {
		StructureSearchEntry only = null;
		for (StructureSearchEntry entry : children()) {
			if (parentScreen.isHiddenFromStructureList(entry.getStructureID())) continue;
			if (only != null) return null;
			only = entry;
		}
		return only;
	}

    private StructureSearchEntry actionRowAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY) || mouseX >= getScrollbarPositionX() || mouseY >= bottom) return null;
        double rowY = mouseY >= fullRowsBottom() ? fullRowsBottom() - 1 : mouseY;
        return getEntryAtPosition(mouseX, rowY);
    }

    boolean isPartyLabelHovered(double mouseX, double mouseY) {
        return isPartyLabelAt(actionRowAt(mouseX, mouseY), mouseX, mouseY);
    }

    private boolean isPartyLabelAt(StructureSearchEntry row, double mouseX, double mouseY) {
        if (row == null) return false;
        int index = children().indexOf(row);
        int top = getRowTop(index);
        return row.isPartyLabelAt(mouseX, mouseY, rowControlsX(), top, displayedRowBottom(index) - top);
    }

    ButtonPress buttonAt(double mouseX, double mouseY) {
        StructureSearchEntry row = actionRowAt(mouseX, mouseY);
        if (row == null || !row.isEnabled()) return null;
        if (hasPreviews()) {
            int index = children().indexOf(row);
            int y = previewY(getRowTop(index), displayedRowBottom(index) - getRowTop(index));
            if (parentScreen.jesThumbnails().canPreview(row.getStructureID())
                    && ButtonPress.contains(mouseX, mouseY, previewX(), y, JesThumbnails.SIZE, JesThumbnails.SIZE)) {
                return new ButtonPress(row, "jesPreview", () -> parentScreen.openJesPreview(row.getStructureID()));
            }
        }
        if (isPartyLabelAt(row, mouseX, mouseY)) {
            return new ButtonPress(row, "partyRequirements", () -> parentScreen.showPartyRequirements(row.getStructureID()));
        }
        if (isFilterButtonClick(row, mouseX, mouseY)) {
            return new ButtonPress(row, "filters", () -> parentScreen.openEntryFilters(row.getStructureID()));
        }
        if (isActionButtonClick(row, mouseX, mouseY)) {
            return new ButtonPress(row, "more", () -> parentScreen.openEntryDetails(row.getStructureID()));
        }
        return null;
    }

	public boolean isActionButtonClick(StructureSearchEntry entry, double mouseX, double mouseY) {
		return isRowButtonClick(entry, mouseX, mouseY, false);
	}

	public boolean isFilterButtonClick(StructureSearchEntry entry, double mouseX, double mouseY) {
		return isRowButtonClick(entry, mouseX, mouseY, true);
	}

    boolean hasPreviews() {
        var previews = parentScreen.jesThumbnails();
        return previews != null && previews.available() && getRowWidth() >= 2 * JesThumbnails.SIZE + 42;
    }

    int previewX() {
        return rowActionX() - 5 - JesThumbnails.SIZE;
    }

    int previewY(int top, int height) {
        return top + (height - JesThumbnails.SIZE) / 2;
    }

	int rowActionX() {
		return getRowLeft() + getRowWidth() - 7 - rowActionWidth();
	}

    int rowControlsX() {
        return hasPreviews() ? previewX() : rowActionX();
    }

    int rowActionWidth() {
        return hasPreviews() ? JesThumbnails.SIZE : parentScreen.rowActionButtonWidth();
    }

    int rowActionHeight() {
        return hasPreviews() ? (JesThumbnails.SIZE - ACTION_BUTTON_GAP) / 2 - 1 : ACTION_BUTTON_HEIGHT;
    }

	int rowActionY(int top, int height, boolean filter) {
		int stackHeight = hasPreviews() ? JesThumbnails.SIZE : ACTION_BUTTONS_HEIGHT;
		return top + (height - stackHeight) / 2 + (filter ? 0 : stackHeight - rowActionHeight());
	}

	private boolean isRowButtonClick(StructureSearchEntry entry, double mouseX, double mouseY, boolean filter) {
		int index = children().indexOf(entry);
		if (index < 0) return false;
		int buttonX = rowActionX();
		int rowHeight = displayedRowBottom(index) - getRowTop(index);
		int buttonY = rowActionY(getRowTop(index), rowHeight, filter);
		return mouseX >= buttonX && mouseX < buttonX + rowActionWidth()
				&& mouseY >= Math.max(buttonY, top) && mouseY < Math.min(buttonY + rowActionHeight(), bottom);
	}

	public boolean containsStructure(Identifier id) {
		for (StructureSearchEntry entry : children()) {
			if (entry.getStructureID().equals(id)) return true;
		}
		return false;
	}

	public ExplorersCompassScreen getParentScreen() {
		return parentScreen;
	}

}
