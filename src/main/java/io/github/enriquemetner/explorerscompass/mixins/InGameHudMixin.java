package io.github.enriquemetner.explorerscompass.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;
import io.github.enriquemetner.explorerscompass.util.CompassProjection;
import io.github.enriquemetner.explorerscompass.util.CompassState;
import io.github.enriquemetner.explorerscompass.util.ItemUtils;
import io.github.enriquemetner.explorerscompass.util.RenderUtils;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At(value = "TAIL"))
    private void renderCompassInfo(DrawContext context, float tickDelta, CallbackInfo info) {
        if (client.player == null || client.options.hudHidden || client.options.debugEnabled
                || (client.currentScreen != null && !(ExplorersCompassConfig.displayWithChatOpen && client.currentScreen instanceof ChatScreen))) return;
        PlayerEntity player = client.player;
        ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
        if (!(stack.getItem() instanceof ExplorersCompassItem compass) || compass.getState(stack) != CompassState.FOUND) return;

        RenderUtils.drawConfiguredStringOnHUD(context, "Structure", client.textRenderer, 5, 5, 0xFFFFFF, 0);
        RenderUtils.drawConfiguredStringOnHUD(context, StructureUtils.getStructureName(compass.getStructureID(stack)), client.textRenderer, 5, 5, 0xAAAAAA, 1);
        int nextLine = 3;
        int x = compass.getFoundStructureX(stack);
        int z = compass.getFoundStructureZ(stack);
        CompassProjection projection = null;
        if (!compass.isInFoundDimension(stack, player.getWorld())) {
            RenderUtils.drawConfiguredStringOnHUD(context, "Wrong Dimension", client.textRenderer, 5, 5, 0xFF4444, nextLine++);
            if (ClientPacketHandlers.showNetherEquivalent) {
                projection = CompassProjection.between(compass.getFoundDimension(stack),
                        player.getWorld().getRegistryKey().getValue(), x, z);
            }
            if (projection == null) return;
            nextLine++;
        }
        Text suffix = projection == null ? Text.empty()
                : Text.literal(" (" + projection.dimensionLabel() + ")").formatted(Formatting.GRAY);
        if (compass.shouldDisplayCoordinates(stack)) {
            int displayX = projection == null ? x : projection.blockX();
            int displayZ = projection == null ? z : projection.blockZ();
            RenderUtils.drawConfiguredStringOnHUD(context, Text.literal("Coordinates").append(suffix), client.textRenderer, 5, 5, 0xFFFFFF, nextLine++);
            RenderUtils.drawConfiguredStringOnHUD(context, displayX + ", " + displayZ, client.textRenderer, 5, 5, 0xAAAAAA, nextLine++);
            nextLine++;
        }
        if (compass.shouldDisplayDistance(stack)) {
            int distance = projection == null ? StructureUtils.getHorizontalDistanceToLocation(player, x, z)
                    : projection.distanceFrom(player.getX(), player.getZ());
            RenderUtils.drawConfiguredStringOnHUD(context, Text.literal("Distance").append(suffix), client.textRenderer, 5, 5, 0xFFFFFF, nextLine++);
            RenderUtils.drawConfiguredStringOnHUD(context, String.valueOf(distance), client.textRenderer, 5, 5, 0xAAAAAA, nextLine);
        }
    }
}
