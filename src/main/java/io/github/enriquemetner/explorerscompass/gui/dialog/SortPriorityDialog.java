package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.sorting.SortCriterion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class SortPriorityDialog {
	private static final int SORT_ROW_HEIGHT = 20;
	private static final int SORT_LABEL_GAP = 11;
	private static final int SORT_HIGHLIGHT_PADDING = 4;
	private final List<SortCriterion> sortOrder = new ArrayList<>();
	private final Consumer<List<String>> saveOrder;
	private final Runnable clickSound;
	private TextRenderer textRenderer;
	private int width;
	private int height;
	private boolean open;
	private int draggedSortRow = -1;
	private boolean sortOrderChangedDuringDrag;

	public SortPriorityDialog(List<String> order, Consumer<List<String>> saveOrder, Runnable clickSound) {
		this.saveOrder = saveOrder;
		this.clickSound = clickSound;
		load(order);
	}

	public void init(TextRenderer font, int screenWidth, int screenHeight) {
		textRenderer = font;
		width = screenWidth;
		height = screenHeight;
	}

	public void load(List<String> order) {
		sortOrder.clear();
		for (String name : order) sortOrder.add(SortCriterion.valueOf(name));
	}

	public List<SortCriterion> order() {
		return List.copyOf(sortOrder);
	}

	public boolean isOpen() {
		return open;
	}

	public void open() {
		open = true;
	}

	private int sortMenuWidth() {
		return DialogFrame.standardWidth(width);
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

	public void close() {
		commitSortOrder();
		open = false;
		draggedSortRow = -1;
	}

	private void commitSortOrder() {
		if (sortOrderChangedDuringDrag) {
			saveOrder.accept(sortOrder.stream().map(Enum::name).toList());
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

	public void render(DrawContext context, int mouseX, int mouseY) {
		int x = sortMenuX();
		int y = sortMenuY();
		int menuWidth = sortMenuWidth();
		DialogFrame.begin(context, width, height, 300, x, y, menuWidth, sortMenuHeight());
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
		backButton().render(context, textRenderer, mouseX, mouseY);
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

	public boolean drag(double mouseX, double mouseY, int button) {
		if (draggedSortRow >= 0 && button == 0) {
			if (mouseX >= sortMenuX() && mouseX < sortMenuX() + sortMenuWidth()) {
				int targetRow = MathHelper.clamp((int) Math.floor((mouseY - sortRowsY()) / SORT_ROW_HEIGHT), 0, sortOrder.size() - 1);
				moveTo(targetRow);
			}
			return true;
		}
		return false;
	}

	public boolean release(int button) {
		if (draggedSortRow >= 0 && button == 0) {
			commitSortOrder();
			draggedSortRow = -1;
			return true;
		}
		return false;
	}

	void moveTo(int targetRow) {
		if (targetRow == draggedSortRow) return;
		SortCriterion criterion = sortOrder.remove(draggedSortRow);
		sortOrder.add(targetRow, criterion);
		draggedSortRow = targetRow;
		sortOrderChangedDuringDrag = true;
	}

	void grab(int row) {
		draggedSortRow = row;
	}

	private DialogButton backButton() {
		return new DialogButton(sortMenuX() + (sortMenuWidth() - 70) / 2, sortBackY(), 70, "Back", this::close);
	}

	public Runnable actionAt(double x, double y) {
		DialogButton back = backButton();
		return back.contains(x, y) ? back.action() : null;
	}

	public boolean click(double mouseX, double mouseY, int button) {
		int x = sortMenuX(), y = sortMenuY(), w = sortMenuWidth();
		if (mouseX < x - 1 || mouseX >= x + w + 1 || mouseY < y - 1 || mouseY >= y + sortMenuHeight() + 1) {
			clickSound.run();
			close();
			return true;
		}
		if (button != 0) return true;
		int row = sortRowAt(mouseX, mouseY);
		if (row >= 0) {
			clickSound.run();
			grab(row);
		}
		return true;
	}

}
