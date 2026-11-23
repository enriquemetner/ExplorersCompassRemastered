package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.compat.ftb.FtbQuestNavigation;
import io.github.enriquemetner.explorerscompass.gui.widgets.Scrollbar;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import org.lwjgl.glfw.GLFW;

/** Modal requirements panel using the same frame and dim backdrop as the search dialog. */
public final class LockedRequirementsDialog {
    private static final int FRAME_HEIGHT = 67; // Title, footer, and border outside the scrollable body.
    private static final int BUTTON_BACKGROUND = 0xff242424;
    private static final int BUTTON_BORDER = 0xff909090;
    private final TextRenderer renderer;
    private final RequirementNode requirements;
    private final Identifier structureId;
    private final Runnable onQuestBookOpened;
    private final java.util.function.Consumer<RequirementNode> onLink;
    private final Predicate<RequirementNode> canOpenLink;
    private final boolean nested;
    private final String title;
    private record Row(OrderedText text, int indent, Boolean ticked, boolean heading, boolean bullet,
                       Text tooltipTitle, Text tooltipDescription, Long questId, RequirementNode node) {}
    private record QuestPopup(long questId, boolean invisibleUntilCompleted, boolean missing) {}
    private record PopupLayout(int x, int y, int width, int height, int buttonY, int openX, int buttonWidth,
                               List<OrderedText> title, List<OrderedText> message) {
        int backX() { return openX + buttonWidth + 8; }
        int closeX() { return backX() + buttonWidth + 8; }
    }
    private List<Row> lines = List.of();
    private int screenWidth = -1, screenHeight = -1;
    private int x, y, width, bodyHeight, lineHeight;
    private double scroll, grabOffset;
    private boolean dragging;
    private QuestPopup questPopup;
    private Runnable openTab;

    public void offerOpenTab(Runnable action) { openTab = action; }
    private final boolean partyUnlocked;

    private final Runnable back;
    private final Runnable close;

    public LockedRequirementsDialog(TextRenderer renderer, Identifier structureId, RequirementNode requirements, Runnable onQuestBookOpened, boolean partyUnlocked, boolean nested, String title,
                           java.util.function.Consumer<RequirementNode> onLink, Predicate<RequirementNode> canOpenLink, Runnable back, Runnable close) {
        this.back = back;
        this.close = close;
        this.renderer = renderer;
        this.nested = nested;
        this.title = title;
        this.onLink = onLink;
        this.canOpenLink = canOpenLink;
        this.requirements = requirements;
        this.structureId = structureId;
        this.partyUnlocked = partyUnlocked;
        this.onQuestBookOpened = onQuestBookOpened;
        this.lineHeight = renderer.fontHeight + 3;
    }

    private void layout(int screenWidth, int screenHeight) {
        if (this.screenWidth == screenWidth && this.screenHeight == screenHeight) return;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        width = DialogFrame.standardWidth(screenWidth);
        lineHeight = renderer.fontHeight + 3;
        lines = new ArrayList<>();
        if (structureId != null) {
            addDetail(Text.literal(StructureUtils.getStructureName(structureId)));
            lines.add(detailRow(Text.empty().asOrderedText()));
        }
        addNode(requirements, 0, false, false);
        int heightLimit = structureId != null ? screenHeight * 3 / 4 : screenHeight / 2;
        int maxBodyHeight = Math.max(1, heightLimit - FRAME_HEIGHT);
        bodyHeight = Math.max(1, Math.min(maxBodyHeight, lines.size() * lineHeight));
        x = (screenWidth - width) / 2;
        y = (screenHeight - bodyHeight - FRAME_HEIGHT) / 2 + 1;
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }

    private void addDetail(Text detail) {
        for (OrderedText line : renderer.wrapLines(detail, Math.max(1, width - 36))) {
            lines.add(detailRow(line));
        }
    }

    private static Row detailRow(OrderedText text) {
        return new Row(text, 0, null, true, false, null, null, null, null);
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
                        node.tooltipDescription() == null ? null : label, node.tooltipDescription(), node.questId(), node));
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

    public void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        layout(screenWidth, screenHeight);
        DialogFrame.begin(context, screenWidth, screenHeight, 300, x, y, width, backTop() + 25 - y);
        context.drawCenteredTextWithShadow(renderer, Text.literal(title != null ? title : partyUnlocked ? "Personal Requirements" : "Locked"),
                x + width / 2, y + 10, title != null || partyUnlocked ? 0xffffffff : 0xffff5555);
        context.enableScissor(x + 10, bodyTop(), x + width - 18, bodyTop() + bodyHeight);
        int first = Math.max(0, (int) scroll / lineHeight);
        int last = Math.min(lines.size(), first + bodyHeight / lineHeight + 2);
        Row hovered = questPopup == null ? rowAt(mouseX, mouseY) : null;
        RequirementNode hoveredLink = hovered != null && isClickable(hovered.node) ? hovered.node : null;
        Row hoveredAdvancement = null;
        Row hoveredQuest = null;
        for (int i = first; i < last; i++) {
            Row row = lines.get(i);
            int rowY = bodyTop() + i * lineHeight - (int) scroll;
            if (row.ticked != null) context.drawText(renderer, row.ticked ? "✔" : "•",
                    x + 12 + row.indent, rowY, row.ticked ? 0xff55ff55 : 0xffc0c0c0, true);
            else if (row.bullet) context.drawText(renderer, "•", x + 12 + row.indent, rowY, 0xffc0c0c0, true);
            int textX = row.node == null ? x + (width - renderer.getWidth(row.text)) / 2
                    : x + (row.heading ? 12 : 23) + row.indent;
            OrderedText text = hoveredLink != null && row.node == hoveredLink
                    ? visitor -> row.text.accept((index, style, codePoint) -> visitor.accept(index, style.withBold(true), codePoint))
                    : row.text;
            context.drawText(renderer, text, textX, rowY, 0xffffff, true);
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
        for (DialogButton button : mainButtons()) button.render(context, renderer, mouseX, mouseY);
        context.getMatrices().pop();
        if (questPopup != null) {
            renderQuestPopup(context, screenWidth, screenHeight, mouseX, mouseY);
        } else if (hoveredQuest != null) {
            context.drawTooltip(renderer, Text.translatable("gui.explorerscompass.open_quest_tooltip"), mouseX, mouseY);
        } else if (hoveredAdvancement != null) {
            int tooltipWidth = Math.max(20, Math.min(240, screenWidth - 40));
            List<OrderedText> tooltip = new ArrayList<>(renderer.wrapLines(hoveredAdvancement.tooltipTitle, tooltipWidth));
            tooltip.addAll(renderer.wrapLines(hoveredAdvancement.tooltipDescription, tooltipWidth));
            context.drawTooltip(renderer, tooltip, HoveredTooltipPositioner.INSTANCE, mouseX, mouseY);
        }
    }

    private int buttonCount() { return openTab != null ? 3 : nested ? 2 : 1; }
    private int buttonWidth() { return Math.min(70, (width - buttonCount() * 8) / buttonCount()); }
    private int firstButtonX() { return x + (width - buttonCount() * buttonWidth() - (buttonCount() - 1) * 8) / 2; }
    private int openX() { return firstButtonX(); }
    private int backX() { return firstButtonX() + (openTab != null ? buttonWidth() + 8 : 0); }
    private int closeX() { return backX() + buttonWidth() + 8; }

    public boolean shows(RequirementNode node) { return requirements.equals(node); }
    public double scrollPosition() { return scroll; }
    public void restoreScroll(double position) { scroll = position; }

    private List<DialogButton> mainButtons() {
        var buttons = new ArrayList<DialogButton>(buttonCount());
        buttons.add(new DialogButton(backX(), backTop(), buttonWidth(), "Back", back));
        if (openTab != null) buttons.add(new DialogButton(openX(), backTop(), buttonWidth(), "Open", openTab));
        if (nested) buttons.add(new DialogButton(closeX(), backTop(), buttonWidth(), "Close", close));
        return buttons;
    }

    private List<DialogButton> questButtons(PopupLayout p) {
        var buttons = new ArrayList<DialogButton>(3);
        if (!questPopup.missing) {
            buttons.add(new DialogButton(p.openX, p.buttonY, p.buttonWidth, Text.translatable("gui.explorerscompass.open"), () -> {
                long questId = questPopup.questId;
                closeQuestPopup();
                if (FtbQuestNavigation.openChapter(questId)) onQuestBookOpened.run();
                else questPopup = new QuestPopup(questId, false, true);
            }));
        }
        buttons.add(new DialogButton(p.backX(), p.buttonY, p.buttonWidth, "Back", this::closeQuestPopup));
        buttons.add(new DialogButton(p.closeX(), p.buttonY, p.buttonWidth, "Close", close));
        return buttons;
    }

    public Runnable actionAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        return DialogButton.actionAt(questPopup == null ? mainButtons()
                : questButtons(popupLayout(screenWidth, screenHeight)), mouseX, mouseY);
    }

    public boolean click(double mouseX, double mouseY, int button, int width, int height) {
        layout(width, height);
        if (questPopup != null) {
            return clickQuestPopup(mouseX, mouseY, width, height);
        }
        if (mouseX < x - 1 || mouseX >= x + this.width + 1
                || mouseY < y - 1 || mouseY >= backTop() + 26) {
            back.run();
            return true;
        }
        if (button != 0) return false;
        Scrollbar bar = scrollbar();
        if (bar.contains(mouseX, mouseY)) {
            grabOffset = bar.grabOffset(mouseY, 0);
            scroll = bar.offsetForMouse(mouseY, grabOffset);
            dragging = true;
            return false;
        }
        Row questRow = rowAt(mouseX, mouseY);
        if (questRow != null && questRow.questId == null && isClickable(questRow.node)) {
            onLink.accept(questRow.node);
            return true;
        }
        if (questRow != null && questRow.questId != null) {
            FtbQuestNavigation.QuestInfo info = FtbQuestNavigation.info(questRow.questId);
            if (info == null) {
                questPopup = new QuestPopup(questRow.questId, false, true);
            } else if (info.openable()) {
                if (FtbQuestNavigation.openQuest(questRow.questId)) onQuestBookOpened.run();
                else questPopup = new QuestPopup(questRow.questId, false, true);
            } else {
                questPopup = new QuestPopup(questRow.questId, info.invisibleUntilCompleted(), false);
            }
            return true;
        }
        return false;
    }

    public void scroll(double amount) {
        if (questPopup == null) scroll = Scrollbar.scrollPixels(scroll, amount, lineHeight * 3, maxScroll());
    }
    public void drag(double mouseY) { if (questPopup == null && dragging) scroll = scrollbar().offsetForMouse(mouseY, grabOffset); }
    public void release() { dragging = false; }
    public boolean closeQuestPopup() {
        if (questPopup == null) return false;
        questPopup = null;
        return true;
    }

    private boolean isClickable(RequirementNode node) {
        return node != null && (node.questId() != null || canOpenLink.test(node));
    }

    private Row rowAt(double mouseX, double mouseY) {
        if (mouseY < bodyTop() || mouseY >= bodyTop() + bodyHeight) return null;
        int index = (int) ((mouseY - bodyTop() + (int) scroll) / lineHeight);
        if (index < 0 || index >= lines.size()) return null;
        Row row = lines.get(index);
        if (row.node == null) return null;
        int rowY = bodyTop() + index * lineHeight - (int) scroll;
        int textX = x + (row.heading ? 12 : 23) + row.indent;
        return mouseX >= textX && mouseX < textX + renderer.getWidth(row.text)
                && mouseY >= rowY && mouseY < rowY + renderer.fontHeight ? row : null;
    }

    private PopupLayout popupLayout(int screenWidth, int screenHeight) {
        int popupWidth = DialogFrame.standardWidth(screenWidth);
        int textWidth = Math.max(1, popupWidth - 24);
        List<OrderedText> title = renderer.wrapLines(Text.literal("Quest Unavailable"), textWidth);
        Text first = Text.translatable(questPopup.missing
                ? "gui.explorerscompass.quest_missing" : questPopup.invisibleUntilCompleted
                ? "gui.explorerscompass.quest_hidden_until_complete"
                : "gui.explorerscompass.quest_hidden_progress");
        List<OrderedText> message = new ArrayList<>(renderer.wrapLines(first, textWidth));
        if (!questPopup.missing) {
            message.addAll(renderer.wrapLines(Text.translatable("gui.explorerscompass.open_quest_chapter"), textWidth));
        }
        int buttonY = 18 + title.size() * lineHeight + 8 + message.size() * lineHeight + 12;
        int popupHeight = buttonY + 30;
        int popupX = (screenWidth - popupWidth) / 2;
        int popupY = (screenHeight - popupHeight) / 2;
        int buttonWidth = Math.min(70, (popupWidth - 24) / 3);
        int openX = questPopup.missing
                ? popupX + (popupWidth - 2 * buttonWidth - 8) / 2 - buttonWidth - 8
                : popupX + (popupWidth - 3 * buttonWidth - 16) / 2;
        return new PopupLayout(popupX, popupY, popupWidth, popupHeight, popupY + buttonY,
                openX, buttonWidth, title, message);
    }

    private void renderQuestPopup(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        PopupLayout p = popupLayout(screenWidth, screenHeight);
        DialogFrame.begin(context, screenWidth, screenHeight, 400, p.x, p.y, p.width, p.height);
        int rowY = p.y + 12;
        for (OrderedText line : p.title) {
            context.drawCenteredTextWithShadow(renderer, line, p.x + p.width / 2, rowY, 0xff5555);
            rowY += lineHeight;
        }
        rowY += 8;
        for (OrderedText line : p.message) {
            context.drawText(renderer, line, p.x + 12, rowY, 0xffffff, true);
            rowY += lineHeight;
        }
        for (DialogButton button : questButtons(p)) button.render(context, renderer, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private boolean clickQuestPopup(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        PopupLayout p = popupLayout(screenWidth, screenHeight);
        if (mouseX < p.x - 1 || mouseX >= p.x + p.width + 1 || mouseY < p.y - 1 || mouseY >= p.y + p.height + 1) {
            return closeQuestPopup();
        }
        return false;
    }

    public void keyPressed(int key) {
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
