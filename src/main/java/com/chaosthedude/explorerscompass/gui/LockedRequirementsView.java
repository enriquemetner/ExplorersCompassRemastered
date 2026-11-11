package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import com.chaosthedude.explorerscompass.compat.ftb.FtbQuestNavigation;
import com.chaosthedude.explorerscompass.unlocking.RequirementNode;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Modal requirements panel using the same frame and dim backdrop as the search dialog. */
final class LockedRequirementsView {
    static final int WINDOW_WIDTH = 260;
    private static final int BUTTON_BACKGROUND = 0xff242424;
    private static final int BUTTON_BORDER = 0xff909090;
    private final TextRenderer renderer;
    private final RequirementNode requirements;
    private final Runnable onQuestBookOpened;
    private record Row(OrderedText text, int indent, Boolean ticked, boolean heading, boolean bullet,
                       Text tooltipTitle, Text tooltipDescription, Long questId) {}
    private record QuestPopup(long questId, Text title, boolean invisibleUntilCompleted) {}
    private record PopupLayout(int x, int y, int width, int height, int buttonY, int openX, int cancelX,
                               List<OrderedText> title, List<OrderedText> message) {}
    private List<Row> lines = List.of();
    private int screenWidth = -1, screenHeight = -1;
    private int x, y, width, bodyHeight, lineHeight;
    private double scroll, grabOffset;
    private boolean dragging;
    private QuestPopup questPopup;
    private final boolean partyUnlocked;

    LockedRequirementsView(TextRenderer renderer, RequirementNode requirements, Runnable onQuestBookOpened, boolean partyUnlocked) {
        this.renderer = renderer;
        this.requirements = requirements;
        this.partyUnlocked = partyUnlocked;
        this.onQuestBookOpened = onQuestBookOpened;
        this.lineHeight = renderer.fontHeight + 3;
    }

    private void layout(int screenWidth, int screenHeight) {
        if (this.screenWidth == screenWidth && this.screenHeight == screenHeight) return;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        width = Math.max(40, Math.min(WINDOW_WIDTH, screenWidth - 24));
        lineHeight = renderer.fontHeight + 3;
        lines = new ArrayList<>();
        addNode(requirements, 0, false, false);
        // Include the title, Back button, spacing, and border in the half-screen limit.
        int maxBodyHeight = Math.max(1, screenHeight / 2 - 67);
        bodyHeight = Math.max(1, Math.min(maxBodyHeight, lines.size() * lineHeight));
        x = (screenWidth - width) / 2;
        y = (screenHeight - bodyHeight - 65) / 2;
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }

    private void addNode(RequirementNode node, int depth, boolean inLogicalGroup, boolean bullet) {
        boolean grouped = inLogicalGroup || node.logicalGroup();
        int indent = Math.min(Math.max(0, depth - 1) * 14, Math.max(0, width - 80));
        FtbQuestNavigation.QuestInfo questInfo = node.questId() == null ? null : FtbQuestNavigation.info(node.questId());
        Text label = questInfo != null && questInfo.hiddenTitle()
                ? Text.translatable("gui.explorerscompass.hidden_quest_title") : node.label();
        String[] paragraphs = label.getString().split("\n", -1);
        boolean first = true;
        for (String paragraph : paragraphs) {
            Text text = paragraphs.length == 1 ? label : Text.literal(paragraph).setStyle(label.getStyle());
            List<OrderedText> wrapped = renderer.wrapLines(text,
                    Math.max(1, width - 44 - indent));
            if (wrapped.isEmpty()) wrapped = List.of(Text.empty().asOrderedText());
            for (OrderedText line : wrapped) {
                lines.add(new Row(line, indent, first && depth > 0 && grouped ? node.checked() : null,
                        depth == 0, first && bullet,
                        node.tooltipDescription() == null ? null : label, node.tooltipDescription(), node.questId()));
                first = false;
            }
        }
        for (RequirementNode child : node.children()) {
            addNode(child, depth + 1, grouped, child.children().isEmpty());
        }
    }

    private int bodyTop() { return y + 30; }
    private int backTop() { return bodyTop() + bodyHeight + 10; }
    private int maxScroll() { return Math.max(0, lines.size() * lineHeight - bodyHeight); }
    private Scrollbar scrollbar() {
        return new Scrollbar(x + width - 12, bodyTop(), bodyTop() + bodyHeight,
                Scrollbar.pixelThumbHeight(bodyHeight, maxScroll(), 12, 0), scroll, maxScroll());
    }

    void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY, boolean held) {
        layout(screenWidth, screenHeight);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        context.fill(x - 1, y - 1, x + width + 1, backTop() + 26, 0xff909090);
        context.fill(x, y, x + width, backTop() + 25, 0xff000000);
        context.drawCenteredTextWithShadow(renderer, Text.literal(partyUnlocked ? "Personal Requirements" : "Locked"),
                x + width / 2, y + 10, partyUnlocked ? 0xffffffff : 0xffff5555);
        context.enableScissor(x + 10, bodyTop(), x + width - 18, bodyTop() + bodyHeight);
        int first = Math.max(0, (int) scroll / lineHeight);
        int last = Math.min(lines.size(), first + bodyHeight / lineHeight + 2);
        Row hoveredAdvancement = null;
        Row hoveredQuest = null;
        for (int i = first; i < last; i++) {
            Row row = lines.get(i);
            int rowY = bodyTop() + i * lineHeight - (int) scroll;
            if (row.ticked != null) context.drawText(renderer, row.ticked ? "✔" : "•",
                    x + 12 + row.indent, rowY, row.ticked ? 0xffffffff : 0xffc0c0c0, true);
            else if (row.bullet) context.drawText(renderer, "•", x + 12 + row.indent, rowY, 0xffc0c0c0, true);
            int textX = x + (row.heading ? 12 : 23) + row.indent;
            context.drawText(renderer, row.text, textX, rowY, 0xffffff, true);
            if (mouseX >= textX && mouseX < textX + renderer.getWidth(row.text)
                    && mouseY >= Math.max(rowY, bodyTop())
                    && mouseY < Math.min(rowY + renderer.fontHeight, bodyTop() + bodyHeight)) {
                if (row.questId != null) hoveredQuest = row;
                else if (row.tooltipDescription != null) hoveredAdvancement = row;
            }
        }
        context.disableScissor();
        // The thumb's transparent grip dots reveal the track color.
        scrollbar().render(context, BUTTON_BORDER, BUTTON_BACKGROUND);
        int buttonX = x + (width - 70) / 2;
        renderPopupButton(context, buttonX, backTop(), Text.literal("Back"), mouseX, mouseY, held && questPopup == null);
        context.getMatrices().pop();
        if (questPopup != null) {
            renderQuestPopup(context, screenWidth, screenHeight, mouseX, mouseY, held);
        } else if (hoveredQuest != null) {
            context.drawTooltip(renderer, Text.translatable("gui.explorerscompass.open_quest_tooltip"), mouseX, mouseY);
        } else if (hoveredAdvancement != null) {
            int tooltipWidth = Math.max(20, Math.min(240, screenWidth - 40));
            List<OrderedText> tooltip = new ArrayList<>(renderer.wrapLines(hoveredAdvancement.tooltipTitle, tooltipWidth));
            tooltip.addAll(renderer.wrapLines(hoveredAdvancement.tooltipDescription, tooltipWidth));
            context.drawTooltip(renderer, tooltip, HoveredTooltipPositioner.INSTANCE, mouseX, mouseY);
        }
    }

    Object buttonLayer() { return questPopup == null ? this : questPopup; }

    ButtonPress buttonAt(double mouseX, double mouseY, int screenWidth, int screenHeight, Runnable back) {
        layout(screenWidth, screenHeight);
        if (questPopup == null) {
            return ButtonPress.at(mouseX, mouseY, x + (width - 70) / 2, backTop(), 70, 20, this, "back", back);
        }
        PopupLayout p = popupLayout(screenWidth, screenHeight);
        if (ButtonPress.contains(mouseX, mouseY, p.openX, p.buttonY, 70, 20)) {
            return new ButtonPress(questPopup, "open", () -> {
                long questId = questPopup.questId;
                closeQuestPopup();
                if (FtbQuestNavigation.openChapter(questId)) onQuestBookOpened.run();
            });
        }
        return ButtonPress.at(mouseX, mouseY, p.cancelX, p.buttonY, 70, 20, questPopup, "cancel", this::closeQuestPopup);
    }

    /** Returns true when the area outside the panel was clicked. */
    boolean click(double mouseX, double mouseY, int button, int width, int height) {
        layout(width, height);
        if (questPopup != null) {
            clickQuestPopup(mouseX, mouseY, width, height);
            return false;
        }
        if (mouseX < x - 1 || mouseX >= x + this.width + 1
                || mouseY < y - 1 || mouseY >= backTop() + 26) return true;
        if (button != 0) return false;
        Scrollbar bar = scrollbar();
        if (bar.contains(mouseX, mouseY)) {
            grabOffset = bar.grabOffset(mouseY, 0);
            scroll = bar.offsetForMouse(mouseY, grabOffset);
            dragging = true;
            return false;
        }
        Row questRow = questAt(mouseX, mouseY);
        if (questRow != null) {
            FtbQuestNavigation.QuestInfo info = FtbQuestNavigation.info(questRow.questId);
            if (info != null) {
                if (info.openable()) {
                    if (FtbQuestNavigation.openQuest(questRow.questId)) onQuestBookOpened.run();
                }
                else questPopup = new QuestPopup(questRow.questId, info.title(), info.invisibleUntilCompleted());
            }
            return false;
        }
        return false;
    }

    void scroll(double amount) {
        if (questPopup == null) scroll = Scrollbar.scrollPixels(scroll, amount, lineHeight * 3, maxScroll());
    }
    void drag(double mouseY) { if (questPopup == null && dragging) scroll = scrollbar().offsetForMouse(mouseY, grabOffset); }
    void release() { dragging = false; }
    boolean closeQuestPopup() {
        if (questPopup == null) return false;
        questPopup = null;
        return true;
    }

    private Row questAt(double mouseX, double mouseY) {
        if (mouseY < bodyTop() || mouseY >= bodyTop() + bodyHeight) return null;
        int index = (int) ((mouseY - bodyTop() + (int) scroll) / lineHeight);
        if (index < 0 || index >= lines.size()) return null;
        Row row = lines.get(index);
        int rowY = bodyTop() + index * lineHeight - (int) scroll;
        int textX = x + (row.heading ? 12 : 23) + row.indent;
        return row.questId != null && mouseX >= textX && mouseX < textX + renderer.getWidth(row.text)
                && mouseY >= rowY && mouseY < rowY + renderer.fontHeight ? row : null;
    }

    private PopupLayout popupLayout(int screenWidth, int screenHeight) {
        int popupWidth = Math.max(40, Math.min(WINDOW_WIDTH, screenWidth - 24));
        int textWidth = Math.max(1, popupWidth - 24);
        List<OrderedText> title = renderer.wrapLines(questPopup.title, textWidth);
        Text first = Text.translatable(questPopup.invisibleUntilCompleted
                ? "gui.explorerscompass.quest_hidden_until_complete"
                : "gui.explorerscompass.quest_hidden_progress");
        List<OrderedText> message = new ArrayList<>(renderer.wrapLines(first, textWidth));
        message.addAll(renderer.wrapLines(Text.translatable("gui.explorerscompass.open_quest_chapter"), textWidth));
        int buttonY = 18 + title.size() * lineHeight + 8 + message.size() * lineHeight + 12;
        int popupHeight = buttonY + 30;
        int popupX = (screenWidth - popupWidth) / 2;
        int popupY = (screenHeight - popupHeight) / 2;
        int openX = popupX + (popupWidth - 146) / 2;
        return new PopupLayout(popupX, popupY, popupWidth, popupHeight, popupY + buttonY,
                openX, openX + 76, title, message);
    }

    private void renderQuestPopup(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY, boolean held) {
        PopupLayout p = popupLayout(screenWidth, screenHeight);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        context.fill(p.x - 1, p.y - 1, p.x + p.width + 1, p.y + p.height + 1, BUTTON_BORDER);
        context.fill(p.x, p.y, p.x + p.width, p.y + p.height, 0xff000000);
        int rowY = p.y + 12;
        for (OrderedText line : p.title) {
            context.drawCenteredTextWithShadow(renderer, line, p.x + p.width / 2, rowY, 0xffffff);
            rowY += lineHeight;
        }
        rowY += 8;
        for (OrderedText line : p.message) {
            context.drawText(renderer, line, p.x + 12, rowY, 0xffffff, true);
            rowY += lineHeight;
        }
        renderPopupButton(context, p.openX, p.buttonY, Text.translatable("gui.explorerscompass.open"), mouseX, mouseY, held);
        renderPopupButton(context, p.cancelX, p.buttonY, Text.translatable("gui.explorerscompass.cancel"), mouseX, mouseY, held);
        context.getMatrices().pop();
    }

    private void renderPopupButton(DrawContext context, int buttonX, int buttonY, Text label, int mouseX, int mouseY, boolean held) {
        boolean hovered = ButtonPress.contains(mouseX, mouseY, buttonX, buttonY, 70, 20);
        boolean available = !(hovered && held);
        context.fill(buttonX, buttonY, buttonX + 70, buttonY + 20, available ? BUTTON_BORDER : 0xff505050);
        context.fill(buttonX + 1, buttonY + 1, buttonX + 69, buttonY + 19, available && hovered ? 0xff424242 : BUTTON_BACKGROUND);
        context.drawCenteredTextWithShadow(renderer, label, buttonX + 35, buttonY + 6, available ? 0xffffff : 0x808080);
    }

    private void clickQuestPopup(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        PopupLayout p = popupLayout(screenWidth, screenHeight);
        if (mouseX < p.x - 1 || mouseX >= p.x + p.width + 1 || mouseY < p.y - 1 || mouseY >= p.y + p.height + 1) {
            closeQuestPopup();
        }
    }

    void keyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll(1);
            case GLFW.GLFW_KEY_DOWN -> scroll(-1);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll = Math.max(0, scroll - bodyHeight);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.min(maxScroll(), scroll + bodyHeight);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
        }
    }
}
