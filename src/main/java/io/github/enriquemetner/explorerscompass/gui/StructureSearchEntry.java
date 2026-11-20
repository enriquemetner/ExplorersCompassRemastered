package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.compat.jes.JesThumbnails;
import io.github.enriquemetner.explorerscompass.util.RenderUtils;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class StructureSearchEntry extends AlwaysSelectedEntryListWidget.Entry<StructureSearchEntry> {
	private static final Identifier LOCK_TEXTURE = new Identifier(ExplorersCompass.MODID, "textures/gui/lock.png");


	private final MinecraftClient client;
	private final ExplorersCompassScreen parentScreen;
	private final Identifier structureID;
	private final StructureSearchList structuresList;

	public StructureSearchEntry(StructureSearchList structuresList, Identifier structureID) {
		this.structuresList = structuresList;
		this.structureID = structureID;
		parentScreen = structuresList.getParentScreen();
		client = MinecraftClient.getInstance();
	}

	@Override
	public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        boolean locked = parentScreen.isStructureLocked(structureID);
        int nameColor = !locked ? 0xffffff : 0xc0c0c0;
        int infoColor = !locked ? 0x808080 : 0x707070;
        int buttonWidth = structuresList.rowActionWidth();
        int buttonX = structuresList.rowActionX();
        boolean hasPreview = structuresList.hasPreviews();
        if (hasPreview) {
            int x = structuresList.previewX();
            int y = structuresList.previewY(top, height);
            int size = JesThumbnails.SIZE;
            boolean previewHovered = hovered && ButtonPress.contains(mouseX, mouseY, x, y, size, size);
            context.fill(x, y, x + size, y + size, actionBackground(previewHovered));
            var previews = parentScreen.jesThumbnails();
            if (!previews.draw(context, structureID, x, y)) {
                String label = previews.thumbnailUnavailable(structureID) ? "N/A" : "...";
                context.drawCenteredTextWithShadow(client.textRenderer, label, x + size / 2,
                        y + (size - client.textRenderer.fontHeight) / 2, 0xaaaaaa);
            }
            context.drawBorder(x, y, size, size, RenderUtils.ROW_BUTTON_BORDER);
        }
        int buttonHeight = structuresList.rowActionHeight();
        int labelOffset = (buttonHeight - 8) / 2;
        int actionsHeight = StructureSearchList.ACTION_BUTTONS_HEIGHT;
        int buttonY = top + (height - actionsHeight) / 2;
        int controlsX = structuresList.rowControlsX();
        int cost = SearchCosts.remote().cost(structureID);
        int costWidth = parentScreen.costLineWidth("", cost, "");
        boolean showCost = SearchCosts.remote().showsSearchCosts() && !ExplorersCompassConfig.hideSearchCost;
        int costX = controlsX - 5 - costWidth;
        int textRight = showCost ? costX : controlsX;
        int textWidth = Math.max(1, textRight - left - 10);
        if (showCost) {
            parentScreen.renderCostLine(context, costX + costWidth / 2,
                    top + (height - client.textRenderer.fontHeight) / 2, "", cost, "");
        }
        String readableName = StructureUtils.getStructureName(structureID);
        boolean showSource = parentScreen.showsStructureSource();
        int lineHeight = client.textRenderer.fontHeight + StructureSearchList.TEXT_LINE_GAP;
        int lineCount = showSource ? 4 : 3;
        int nameY = top + (height - lineCount * lineHeight) / 2;
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(readableName, textWidth), left + 5, nameY, nameColor, true);
        int detailY = nameY + lineHeight;
        if (showSource) {
            drawDetail(context, "Source: ", StructureUtils.getStructureSource(structureID),
                    left + 5, detailY, textWidth, infoColor);
            detailY += lineHeight;
        }
        drawDetail(context, "Set: ", StructureUtils.formatIdPath(getSetID()),
                left + 5, detailY, textWidth, infoColor);
        drawDetail(context, "Dimension: ",
                StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureID)),
                left + 5, detailY + lineHeight, textWidth, infoColor);
        boolean filterHovered = structuresList.isFilterButtonClick(this, mouseX, mouseY);
        buttonY = structuresList.rowActionY(top, height, true);
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, RenderUtils.ROW_BUTTON_BORDER);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, actionBackground(filterHovered));
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Filters"), buttonX + buttonWidth / 2, buttonY + labelOffset, 0xffffff);
        buttonY = structuresList.rowActionY(top, height, false);
        boolean buttonHovered = structuresList.isActionButtonClick(this, mouseX, mouseY);
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, RenderUtils.ROW_BUTTON_BORDER);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, actionBackground(buttonHovered));
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("More"), buttonX + buttonWidth / 2, buttonY + labelOffset, 0xffffff);
		if (locked) {
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.enableBlend();
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 10);
			context.drawTexture(LOCK_TEXTURE, left + (width - 11) / 2, top + (height - 14) / 2,
					11, 14, 0, 0, 11, 14, 11, 14);
			context.getMatrices().pop();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

    private void drawDetail(DrawContext context, String label, String value, int x, int y, int width, int color) {
        int labelWidth = client.textRenderer.getWidth(label);
        context.drawTextWithShadow(client.textRenderer, label, x, y, color);
        RenderUtils.drawTextToFit(context, client.textRenderer, Text.literal(value), x + labelWidth, y, width - labelWidth, color);
    }

    private static int actionBackground(boolean hovered) {
        return hovered ? 0xff424242 : 0xff242424;
    }

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isEnabled()) {
            if (parentScreen.isHiddenFromStructureList(structureID)) {
                structuresList.resetDoubleClick();
                return true;
            }
            if (structuresList.selectClickedRow(structureID)) searchForStructure();
            return true;
		}
		return false;
	}

	public void searchForStructure() {
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		parentScreen.searchForStructure(structureID);
	}

	public boolean isEnabled() {
		return true;
	}

	public Identifier getStructureID() {
		return structureID;
	}

	public Identifier getSetID() {
		return ExplorersCompass.structureIDsToSetIDs.get(structureID);
	}

	@Override
	public Text getNarration() {
		return Text.literal(StructureUtils.getStructureName(structureID)).append(parentScreen.isStructureLocked(structureID)
                ? Text.literal(". Locked") : Text.empty());
	}

}
