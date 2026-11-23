package io.github.enriquemetner.explorerscompass.gui.dialog;

import io.github.enriquemetner.explorerscompass.gui.widgets.HitBox;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class EntryFiltersDialog {
    private static final int ROW_HEIGHT = 20;

    public record Row(String category, String label, BooleanSupplier checked, Runnable toggle) {}

    @FunctionalInterface
    public interface CheckboxRenderer {
        void draw(DrawContext context, int x, int y, boolean checked);
    }

    private record Layout(int x, int y, int width, int height, int visible) {
        int bodyY() { return y + 30; }
        DialogButton back(Runnable close) {
            return new DialogButton(x + (width - 70) / 2, bodyY() + visible * ROW_HEIGHT + 10, 70, "Back", close);
        }
    }

    private final TextRenderer font;
    private final Supplier<List<Row>> rows;
    private final Runnable close;
    private final BooleanSupplier entryVisible;
    private final Runnable clickSound;
    private final CheckboxRenderer checkbox;
    private int firstRow;

    public EntryFiltersDialog(TextRenderer font, Supplier<List<Row>> rows, Runnable close,
                              BooleanSupplier entryVisible, Runnable clickSound, CheckboxRenderer checkbox) {
        this.font = font;
        this.rows = rows;
        this.close = close;
        this.entryVisible = entryVisible;
        this.clickSound = clickSound;
        this.checkbox = checkbox;
    }

    private Layout layout(int screenWidth, int screenHeight, int count) {
        int visible = Math.min(count, Math.max(1, (screenHeight - 70) / ROW_HEIGHT));
        int width = DialogFrame.standardWidth(screenWidth);
        int height = 65 + visible * ROW_HEIGHT;
        return new Layout((screenWidth - width) / 2, (screenHeight - height) / 2, width, height, visible);
    }

    public Runnable actionAt(double mouseX, double mouseY, int width, int height) {
        DialogButton back = layout(width, height, rows.get().size()).back(close);
        return back.contains(mouseX, mouseY) ? back.action() : null;
    }

    public void scroll(double amount, int width, int height) {
        int count = rows.get().size();
        firstRow = MathHelper.clamp(firstRow - (int) Math.signum(amount), 0, count - layout(width, height, count).visible);
    }

    public void render(DrawContext context, int width, int height, int mouseX, int mouseY) {
        List<Row> entries = rows.get();
        Layout l = layout(width, height, entries.size());
        int x = l.x, y = l.y, w = l.width, bodyY = l.bodyY();
        int checkboxX = x + w - 19;
        int valueX = x + 12 + font.getWidth("Dimension:") + 8;
        DialogFrame.begin(context, width, height, 300, x, y, w, l.height);
        context.drawCenteredTextWithShadow(font, Text.literal("Filters"), x + w / 2, y + 10, 0xffffff);
        context.enableScissor(x + 8, bodyY, x + w - 8, bodyY + l.visible * ROW_HEIGHT);
        int first = Math.min(firstRow, entries.size() - l.visible);
        for (int i = 0; i < l.visible; i++) {
            Row row = entries.get(first + i);
            int rowY = bodyY + i * ROW_HEIGHT;
            if (HitBox.contains(mouseX, mouseY, x + 8, rowY, w - 16, ROW_HEIGHT))
                context.fill(x + 8, rowY, x + w - 8, rowY + ROW_HEIGHT, 0xff242424);
            context.drawTextWithShadow(font, row.category(), x + 12, rowY + 6, 0xffffff);
            context.drawTextWithShadow(font,
                    font.trimToWidth(row.label(), Math.max(1, checkboxX - valueX - 8)), valueX, rowY + 6, 0xffffff);
            checkbox.draw(context, checkboxX, rowY + 5, row.checked().getAsBoolean());
        }
        context.disableScissor();
        l.back(close).render(context, font, mouseX, mouseY);
        context.getMatrices().pop();
    }

    public boolean click(double mouseX, double mouseY, int button, int width, int height) {
        List<Row> entries = rows.get();
        Layout l = layout(width, height, entries.size());
        if (!HitBox.contains(mouseX, mouseY, l.x - 1, l.y - 1, l.width + 2, l.height + 2)) {
            clickSound.run();
            close.run();
        } else if (button == 0 && mouseY >= l.bodyY()) {
            int index = (int) (mouseY - l.bodyY()) / ROW_HEIGHT;
            if (index < l.visible) {
                clickSound.run();
                entries.get(Math.min(firstRow, entries.size() - l.visible) + index).toggle().run();
                if (!entryVisible.getAsBoolean()) close.run();
            }
        }
        return true;
    }
}
