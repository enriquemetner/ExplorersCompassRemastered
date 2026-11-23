package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.gui.widgets.IconButton;

import io.github.enriquemetner.explorerscompass.gui.widgets.Scrollbar;

import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentButton;
import io.github.enriquemetner.explorerscompass.gui.widgets.TransparentTextField;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

final class HiddenStructuresScreen extends Screen {
    private static final int LIST_TOP = 40;
    private final CompassSettingsScreen parent;
    private final HiddenStructuresList list = new HiddenStructuresList();
    private TransparentTextField search;
    private boolean draggingScrollbar;
    private double grabOffset;

    HiddenStructuresScreen(CompassSettingsScreen parent) {
        super(Text.literal(HiddenStructuresList.TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int backWidth = textRenderer.getWidth("Back") + 20;
        int closeX = width - 30;
        int backX = closeX - 5 - backWidth;
        int searchX = Math.min(20 + textRenderer.getWidth(title), Math.max(10, backX - 85));
        addDrawableChild(new TransparentButton(backX, 10, backWidth, 20, Text.literal("Back"), button -> close()));
        addDrawableChild(IconButton.close(closeX, parent::closeSettingsFlow));
        search = addDrawableChild(new TransparentTextField(textRenderer, searchX, 10,
                Math.max(1, backX - 5 - searchX), 20, Text.literal("Search hidden structures")));
        search.setMaxLength(256);
        search.setText(list.query());
        search.setChangedListener(list::setQuery);
        draggingScrollbar = false;
        setInitialFocus(search);
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public boolean shouldPause() { return parent.shouldPause(); }

    @Override
    public void tick() { search.tick(); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        list.render(context, textRenderer, width, LIST_TOP, height - 10, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(title.getString(),
                Math.max(1, search.getX() - 20)), 10, 15, 0xffffff);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            Scrollbar bar = list.scrollbar(width, LIST_TOP, height - 10);
            if (bar.contains(x, y)) {
                draggingScrollbar = true;
                grabOffset = bar.grabOffset(y, Scrollbar.GRAB_PADDING);
                if (bar.outsideThumb(y, Scrollbar.GRAB_PADDING)) list.setScroll(bar.offsetForMouse(y, grabOffset));
                return true;
            }
            Runnable remove = list.removeButtonAt(x, y, textRenderer, width, LIST_TOP, height - 10, () -> {});
            if (remove != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                remove.run();
                return true;
            }
        }
        if (button == 1 && search.isMouseOver(x, y)) {
            search.setText("");
            setFocused(search);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScrollbar) {
            list.setScroll(list.scrollbar(width, LIST_TOP, height - 10).offsetForMouse(y, grabOffset));
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
        list.scroll(amount, LIST_TOP, height - 10);
        return true;
    }
}
