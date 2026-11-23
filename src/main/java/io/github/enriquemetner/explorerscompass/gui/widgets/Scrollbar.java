package io.github.enriquemetner.explorerscompass.gui.widgets;

import io.github.enriquemetner.explorerscompass.client.RenderUtils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class Scrollbar {
	public static final int WIDTH = 10;
	public static final int GRAB_PADDING = 2;
	private final int x;
	private final int width;
	private final int top;
	private final int bottom;
	private final int thumbHeight;
	private final double offset;
	private final double maxOffset;

	public Scrollbar(int x, int top, int bottom, int thumbHeight, double offset, double maxOffset) {
		this(x, WIDTH, top, bottom, thumbHeight, offset, maxOffset);
	}

	public Scrollbar(int x, int width, int top, int bottom, int thumbHeight, double offset, double maxOffset) {
		this.x = x;
		this.width = width;
		this.top = top;
		this.bottom = bottom;
		this.thumbHeight = MathHelper.clamp(thumbHeight, 1, Math.max(1, bottom - top));
		this.maxOffset = Math.max(0, maxOffset);
		this.offset = MathHelper.clamp(offset, 0, this.maxOffset);
	}

	public static int rowThumbHeight(int trackHeight, int visibleRows, int totalRows) {
		return Math.min(trackHeight, Math.max(12, trackHeight * visibleRows / Math.max(1, totalRows)));
	}

	public static int pixelThumbHeight(int trackHeight, double maxOffset, int minimum, int bottomMargin) {
		int upper = Math.max(1, trackHeight - bottomMargin);
		return MathHelper.clamp((int) (trackHeight * trackHeight / (trackHeight + Math.max(0, maxOffset))), Math.min(minimum, upper), upper);
	}

	public static double scrollPixels(double offset, double wheelAmount, int rowHeight, double maxOffset) {
		double target = offset - wheelAmount * rowHeight;
		if (wheelAmount != 0 && Math.rint(wheelAmount) == wheelAmount) {
			double row = offset / rowHeight - wheelAmount;
			target = (wheelAmount < 0 ? Math.floor(row) : Math.ceil(row)) * rowHeight;
		}
		return MathHelper.clamp(target, 0, maxOffset);
	}

	public static int scrollRows(int offset, double wheelAmount, int rowsPerTick, int maxOffset) {
		return MathHelper.clamp(offset - (int) Math.signum(wheelAmount) * rowsPerTick, 0, maxOffset);
	}

	private boolean isVisible() {
		return maxOffset > 0;
	}

	public boolean contains(double mouseX, double mouseY) {
		return isVisible() && mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < bottom;
	}

	private int thumbTop() {
		return top + (int) (offset * (bottom - top - thumbHeight) / maxOffset);
	}

	public boolean outsideThumb(double mouseY, int padding) {
		return mouseY < thumbTop() - padding || mouseY >= thumbTop() + thumbHeight + padding;
	}

	public double grabOffset(double mouseY, int padding) {
		return outsideThumb(mouseY, padding) ? thumbHeight / 2.0 : MathHelper.clamp(mouseY - thumbTop(), 0, thumbHeight);
	}

	public double offsetForMouse(double mouseY, double grabOffset) {
		int travel = Math.max(1, bottom - top - thumbHeight);
		return MathHelper.clamp((mouseY - top - grabOffset) * maxOffset / travel, 0, maxOffset);
	}

	public void render(DrawContext context) {
		if (!isVisible()) return;
		RenderUtils.drawScrollbarThumb(context, x, thumbTop(), width, thumbHeight,
				RenderUtils.blackWithOpacity(RenderUtils.SCROLLBAR_THUMB_OPACITY_PERCENT));
	}

	public void render(DrawContext context, int trackColor, int thumbColor) {
		if (!isVisible()) return;
		context.fill(x, top, x + width, bottom, trackColor);
		RenderUtils.drawScrollbarThumb(context, x, thumbTop(), width, thumbHeight, thumbColor);
	}
}
