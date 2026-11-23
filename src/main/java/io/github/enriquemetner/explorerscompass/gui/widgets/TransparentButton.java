package io.github.enriquemetner.explorerscompass.gui.widgets;

import io.github.enriquemetner.explorerscompass.client.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class TransparentButton extends ButtonWidget {
	private int rightInset;

	public TransparentButton(int x, int y, int width, int height, Text label, PressAction onPress) {
		super(x, y, width, height, label, onPress, DEFAULT_NARRATION_SUPPLIER);
	}

	public void setRightInset(int rightInset) {
		this.rightInset = rightInset;
	}

	@Override
	public void renderButton(DrawContext context, int mouseX, int mouseY, float partialTicks) {
		if (visible) {
			MinecraftClient mc = MinecraftClient.getInstance();
			int opacity = active ? RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT : RenderUtils.DISABLED_BUTTON_OPACITY_PERCENT;
			context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), RenderUtils.blackWithOpacity(opacity));
			if (active && isHovered()) {
				context.drawBorder(getX(), getY(), getWidth(), getHeight(),
						RenderUtils.ROW_BUTTON_BORDER);
			}
			renderLabel(context, mc.textRenderer);
		}
	}

	protected void renderLabel(DrawContext context, TextRenderer renderer) {
		String label = getMessage().getString();
		int availableWidth = Math.max(1, getWidth() - rightInset - 8);
		if (renderer.getWidth(label) > availableWidth) {
			label = renderer.trimToWidth(label, Math.max(1, availableWidth - renderer.getWidth("..."))) + "...";
		}
		context.drawCenteredTextWithShadow(renderer, label, getX() + (getWidth() - rightInset) / 2, labelY(), 0xffffff);
	}

	public int labelY() {
		return getY() + (getHeight() - 8) / 2;
	}

}
