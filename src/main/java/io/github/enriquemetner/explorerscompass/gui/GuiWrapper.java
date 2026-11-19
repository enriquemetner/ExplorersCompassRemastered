package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.Hand;

public class GuiWrapper {
	
	public static void openGUI(World world, PlayerEntity player, ItemStack stack) {
		ExplorersCompass.synced = false;
		MinecraftClient.getInstance().setScreen(new ExplorersCompassScreen(world, player, stack, (ExplorersCompassItem) stack.getItem(), ExplorersCompass.allowedStructureIDs));
	}

	public static void animateTargetClear(Hand hand) {
		MinecraftClient.getInstance().gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
	}
}
