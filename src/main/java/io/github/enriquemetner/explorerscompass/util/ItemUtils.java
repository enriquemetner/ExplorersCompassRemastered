package io.github.enriquemetner.explorerscompass.util;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public class ItemUtils {

	public static boolean verifyNBT(ItemStack stack) {
		return getOrCreateCompassNbt(stack) != null;
	}

	/** Returns the compass's writable NBT, or null for an empty or different item. */
	public static NbtCompound getOrCreateCompassNbt(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() != ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			return null;
		}

		if (!stack.hasNbt()) stack.setNbt(new NbtCompound());
		return stack.getNbt();
	}

	public static ItemStack getHeldItem(PlayerEntity player, Item item) {
		ItemStack mainHand = player.getMainHandStack();
		if (!mainHand.isEmpty() && mainHand.getItem() == item) return mainHand;
		ItemStack offHand = player.getOffHandStack();
		return !offHand.isEmpty() && offHand.getItem() == item ? offHand : ItemStack.EMPTY;
	}

}
