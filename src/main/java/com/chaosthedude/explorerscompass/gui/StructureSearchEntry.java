package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.compat.jes.JesThumbnails;
import com.chaosthedude.explorerscompass.util.RenderUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
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

	private static final String[] PARTY_LABEL = {"Unlocked", "through", "party"};
	private static final int ACTION_BORDER = 0xff909090;
	private static final int HELD_ACTION_BORDER = 0xff505050;

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
        int buttonWidth = parentScreen.rowActionButtonWidth();
        int buttonX = structuresList.rowActionX();
        if (structuresList.hasPreviews()) {
            int x = structuresList.previewX();
            int y = structuresList.previewY(top, height);
            int size = JesThumbnails.SIZE;
            context.fill(x, y, x + size, y + size, 0xff242424);
            var previews = parentScreen.jesThumbnails();
            if (!previews.draw(context, structureID, x, y)) {
                String label = previews.thumbnailUnavailable(structureID) ? "N/A" : "...";
                context.drawCenteredTextWithShadow(client.textRenderer, label, x + size / 2,
                        y + (size - client.textRenderer.fontHeight) / 2, 0xaaaaaa);
            }
        }
        int buttonHeight = StructureSearchList.ACTION_BUTTON_HEIGHT;
        int actionsHeight = StructureSearchList.ACTION_BUTTONS_HEIGHT;
        int buttonY = top + (height - actionsHeight) / 2;
        boolean partyUnlocked = parentScreen.isUnlockedThroughParty(structureID);
        int textRight = partyUnlocked ? partyLabelX(buttonX) : buttonX;
        int textWidth = Math.max(1, textRight - left - 10);
        if (partyUnlocked) {
            int panelWidth = partyLabelWidth();
            boolean held = parentScreen.isButtonHeld(this, "partyRequirements");
            context.fill(textRight, buttonY, textRight + panelWidth, buttonY + actionsHeight,
                    RenderUtils.blackWithOpacity(RenderUtils.LOCKED_ENTRY_BACKGROUND_OPACITY_PERCENT));
            int x = textRight + panelWidth / 2;
            int y = buttonY + (actionsHeight - PARTY_LABEL.length * client.textRenderer.fontHeight) / 2;
            int color = held ? 0x808080 : 0xffffff;
            for (String line : PARTY_LABEL) {
                context.drawCenteredTextWithShadow(client.textRenderer, line, x, y, color);
                y += client.textRenderer.fontHeight;
            }
        }
        String readableName = StructureUtils.getStructureName(structureID);
        int nameY = top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 2);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(readableName, textWidth), left + 5, nameY, nameColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.literal("Source").getString() + ": " + StructureUtils.getStructureSource(structureID), textWidth), left + 5, top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 1), infoColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.literal("Set").getString() + ": " + StructureUtils.getStructureName(ExplorersCompass.structureIDsToSetIDs.get(structureID)), textWidth), left + 5, top + (height / 2) - ((client.textRenderer.fontHeight + 2) * 0), infoColor, true);
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(Text.literal("Dimension").getString() + ": " + StructureUtils.structureDimensionsToString(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structureID)), textWidth), left + 5, top + (height / 2) + ((client.textRenderer.fontHeight + 2) * 1), infoColor, true);
        boolean filterHovered = structuresList.isFilterButtonClick(this, mouseX, mouseY);
        boolean filterHeld = parentScreen.isButtonHeld(this, "filters");
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, filterHeld ? HELD_ACTION_BORDER : ACTION_BORDER);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, filterHovered && !filterHeld ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("filters"), buttonX + buttonWidth / 2, buttonY + 4, filterHeld ? 0x808080 : 0xffffff);
        buttonY += buttonHeight + StructureSearchList.ACTION_BUTTON_GAP;
        boolean buttonHovered = structuresList.isActionButtonClick(this, mouseX, mouseY);
        boolean moreHeld = parentScreen.isButtonHeld(this, "more");
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, moreHeld ? HELD_ACTION_BORDER : ACTION_BORDER);
        context.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, buttonHovered && !moreHeld ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("more"), buttonX + buttonWidth / 2, buttonY + 4, moreHeld ? 0x808080 : 0xffffff);
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

    int partyLabelWidth() {
        return client.textRenderer.getWidth(PARTY_LABEL[0]) + 8;
    }

    int partyLabelX(int buttonX) {
        return buttonX - 5 - partyLabelWidth();
    }

    boolean isPartyLabelAt(double mouseX, double mouseY, int buttonX, int top, int height) {
        int actionsHeight = StructureSearchList.ACTION_BUTTONS_HEIGHT;
        return parentScreen.isUnlockedThroughParty(structureID)
                && ButtonPress.contains(mouseX, mouseY, partyLabelX(buttonX), top + (height - actionsHeight) / 2,
                        partyLabelWidth(), actionsHeight);
    }

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isEnabled()) {
            if (parentScreen.isStructureHidden(structureID)) {
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
                ? Text.literal(". Locked") : parentScreen.isUnlockedThroughParty(structureID)
                ? Text.literal(". Unlocked through party") : Text.empty());
	}

}
