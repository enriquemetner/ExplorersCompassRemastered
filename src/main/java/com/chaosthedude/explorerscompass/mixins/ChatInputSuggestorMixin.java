package com.chaosthedude.explorerscompass.mixins;

import java.util.List;

import com.mojang.brigadier.ParseResults;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Redirect(method = "highlight", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private static Object explorerscompass$teleportCoordinateStyle(List<?> styles, int index,
            ParseResults<CommandSource> parse, String visibleText, int offset) {
        if (index == 1 || index == 2) {
            String input = parse.getReader().getString();
            int start = input.startsWith("/") ? 1 : 0;
            if (input.startsWith("explorerscompass tp ", start) || input.startsWith("ecomp tp ", start)) {
                // Vanilla uses its first argument style (aqua) for a single position argument.
                return styles.get(0);
            }
        }
        return styles.get(index);
    }
}
