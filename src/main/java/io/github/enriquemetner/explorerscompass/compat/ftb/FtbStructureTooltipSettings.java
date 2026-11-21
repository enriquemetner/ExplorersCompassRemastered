package io.github.enriquemetner.explorerscompass.compat.ftb;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import java.util.Locale;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

final class FtbStructureTooltipSettings {
    private static final String DESCRIPTION = "description";
    private static final String LEGACY_SUBTITLE = "subtitle";
    private static final String STRUCTURE_DISPLAY = "structure_display";
    private static final String LANGUAGE_PREFIX = "ftb_tooltip.explorerscompass.";

    private enum Display {
        ALWAYS, HOLD_ALT, NEVER;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final NameMap<Display> DISPLAY_NAMES = NameMap.of(Display.ALWAYS, Display.values())
            .id(Display::id)
            .name(value -> Text.translatable(LANGUAGE_PREFIX + value.id()))
            .create();

    private String description = "";
    private Display display = Display.ALWAYS;

    void fillConfigGroup(ConfigGroup group) {
        group.addString(DESCRIPTION, description, value -> description = value, "")
                .setNameKey(LANGUAGE_PREFIX + DESCRIPTION);
        group.addEnum(STRUCTURE_DISPLAY, display, value -> display = value, DISPLAY_NAMES)
                .setNameKey(LANGUAGE_PREFIX + STRUCTURE_DISPLAY);
    }

    void writeData(NbtCompound nbt) {
        nbt.remove(LEGACY_SUBTITLE);
        if (description.isBlank()) nbt.remove(DESCRIPTION);
        else nbt.putString(DESCRIPTION, description);
        nbt.putString(STRUCTURE_DISPLAY, display.id());
    }

    void readData(NbtCompound nbt) {
        description = nbt.contains(DESCRIPTION, NbtElement.STRING_TYPE)
                ? nbt.getString(DESCRIPTION) : nbt.getString(LEGACY_SUBTITLE);
        display = switch (nbt.getString(STRUCTURE_DISPLAY)) {
            case "hold_alt" -> Display.HOLD_ALT;
            case "never" -> Display.NEVER;
            default -> Display.ALWAYS;
        };
    }

    void writeNetData(PacketByteBuf buf) {
        buf.writeString(description);
        buf.writeEnumConstant(display);
    }

    void readNetData(PacketByteBuf buf) {
        description = buf.readString();
        display = buf.readEnumConstant(Display.class);
    }

    static void removeFromDefinition(NbtCompound nbt) {
        nbt.remove(LEGACY_SUBTITLE);
        nbt.remove(DESCRIPTION);
        nbt.remove(STRUCTURE_DISPLAY);
    }

    void addTaskDescription(TooltipList tooltip) {
        if (!description.isBlank()) tooltip.add(descriptionText());
    }

    void addRewardDescription(TooltipList tooltip, Text title) {
        if (description.isBlank()) return;
        // FTB adds the title before the body but may tint it for a team reward.
        var lines = tooltip.getLines();
        String titleString = title.getString();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getString().equals(titleString)) {
                lines.add(i + 1, descriptionText());
                return;
            }
        }
        tooltip.add(descriptionText());
    }

    private Text descriptionText() {
        return Text.literal(description).formatted(Formatting.GRAY);
    }

    boolean showStructures(TooltipList tooltip) {
        return switch (display) {
            case ALWAYS -> true;
            case NEVER -> false;
            case HOLD_ALT -> {
                boolean held = Widget.isKeyDown(GLFW.GLFW_KEY_LEFT_ALT);
                if (!held) {
                    tooltip.add(Text.translatable(LANGUAGE_PREFIX + "hold_alt_hint").formatted(Formatting.DARK_GRAY));
                }
                yield held;
            }
        };
    }
}
