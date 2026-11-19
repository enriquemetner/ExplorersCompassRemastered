package io.github.enriquemetner.explorerscompass.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.enriquemetner.explorerscompass.gui.ExplorersCompassScreen;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;

import io.github.enriquemetner.explorerscompass.util.CompassState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

	@Shadow
	private ItemStack mainHand;

	@Shadow
	private ItemStack offHand;

	@Shadow
	@Final
	private MinecraftClient client;

	@Inject(method = "resetEquipProgress", at = @At("HEAD"), cancellable = true)
	private void keepCompassRaisedOnOpen(Hand hand, CallbackInfo ci) {
		if (client.currentScreen instanceof ExplorersCompassScreen && client.player != null
				&& client.player.getStackInHand(hand).getItem() instanceof ExplorersCompassItem) {
			ci.cancel();
		}
	}

	@Inject(method = "updateHeldItems()V", at = @At("HEAD"))
	private void keepCompassRaisedOnSearchEnd(CallbackInfo ci) {
		ItemStack newMainStack = client.player.getMainHandStack();
		if (skipEquipMotion(mainHand, newMainStack)) mainHand = newMainStack;

		ItemStack newOffStack = client.player.getOffHandStack();
		if (skipEquipMotion(offHand, newOffStack)) offHand = newOffStack;
	}

	private static boolean skipEquipMotion(ItemStack previous, ItemStack current) {
		if (!(previous.getItem() instanceof ExplorersCompassItem compass) || !current.isOf(compass)) return false;
		CompassState previousState = compass.getState(previous);
		return previousState == CompassState.SEARCHING || previousState == compass.getState(current);
	}
}
