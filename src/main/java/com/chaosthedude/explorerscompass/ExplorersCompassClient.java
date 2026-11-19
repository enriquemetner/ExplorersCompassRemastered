package com.chaosthedude.explorerscompass;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.CurrentStructurePacket;
import com.chaosthedude.explorerscompass.network.ClientPacketHandlers;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
import com.chaosthedude.explorerscompass.network.SearchProgressPacket;
import com.chaosthedude.explorerscompass.network.UnlockToastPacket;
import com.chaosthedude.explorerscompass.network.CompassSettingsPacket;

import com.chaosthedude.explorerscompass.config.SearchCosts;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import com.chaosthedude.explorerscompass.mixins.HandledScreenAccessor;
import com.chaosthedude.explorerscompass.network.ConsumeCompassItemsPacket;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

public class ExplorersCompassClient implements ClientModInitializer {
    private final com.chaosthedude.explorerscompass.client.CompassNeedle needle = new com.chaosthedude.explorerscompass.client.CompassNeedle();
	
	@Override
	public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof HandledScreen<?> inventory)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, x, y, button) ->
                    button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !consumeCompassItems(inventory, x, y, true));
            ScreenMouseEvents.allowMouseScroll(screen).register((ignored, x, y, horizontal, vertical) ->
                    vertical == 0 || !consumeCompassItems(inventory, x, y, false));
        });
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ftbquests"))
			com.chaosthedude.explorerscompass.compat.ftb.FtbQuestClientIntegration.register();
		ClientPlayNetworking.registerGlobalReceiver(CurrentStructurePacket.ID, ClientPacketHandlers::applyCurrentStructure);
		ClientPlayNetworking.registerGlobalReceiver(SyncPacket.ID, ClientPacketHandlers::applySync);
		ClientPlayNetworking.registerGlobalReceiver(SearchResultPacket.ID, ClientPacketHandlers::applySearchResult);
		ClientPlayNetworking.registerGlobalReceiver(SearchProgressPacket.ID, ClientPacketHandlers::applySearchProgress);
        ClientPlayNetworking.registerGlobalReceiver(com.chaosthedude.explorerscompass.network.SearchAvailabilityPacket.ID, ClientPacketHandlers::applySearchAvailability);
		ClientPlayNetworking.registerGlobalReceiver(UnlockToastPacket.ID, ClientPacketHandlers::applyUnlockToast);
		ClientPlayNetworking.registerGlobalReceiver(CompassSettingsPacket.ID, ClientPacketHandlers::applyCompassSettings);
        ClientPlayNetworking.registerGlobalReceiver(com.chaosthedude.explorerscompass.network.PartyRefreshPacket.ID, ClientPacketHandlers::applyPartyRefresh);
        // Minecraft can discard queued client work during disconnect.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ExplorersCompassScreen.clearWorldSearchState());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.chaosthedude.explorerscompass.config.SearchCosts.clearRemote();
            needle.clear();
            ClientPacketHandlers.whitelistActive = false;
            ClientPacketHandlers.showNetherEquivalent = false;
            ClientPacketHandlers.useClassicCompass = false;
            ClientPacketHandlers.excludedStructures = java.util.Set.of();
            ClientPacketHandlers.partyRequirements = java.util.Map.of();
            ClientPacketHandlers.latestUnlockedStructures = java.util.Set.of();
            ClientPacketHandlers.showNewlyUnlockedFilter = false;
            client.execute(ExplorersCompassScreen::clearWorldSearchState);
        });
		
		ModelPredicateProviderRegistry.register(ExplorersCompass.EXPLORERS_COMPASS_ITEM, new Identifier("angle"), needle);
		ModelPredicateProviderRegistry.register(ExplorersCompass.EXPLORERS_COMPASS_ITEM,
				new Identifier(ExplorersCompass.MODID, "classic"),
				(stack, world, entity, seed) -> ClientPacketHandlers.useClassicCompass ? 1 : 0);
	}

    private static boolean consumeCompassItems(HandledScreen<?> screen, double x, double y, boolean wholeStack) {
        var handler = screen.getScreenHandler();
        var cursor = handler.getCursorStack();
        var costs = SearchCosts.remote();
        if (cursor.isEmpty() || costs.type() != SearchCosts.Type.ITEMS
                || !costs.items().containsKey(Registries.ITEM.getId(cursor.getItem()))) return false;

        var slot = ((HandledScreenAccessor) screen).explorersCompass$getSlotAt(x, y);
        if (slot == null || !slot.getStack().isOf(ExplorersCompass.EXPLORERS_COMPASS_ITEM)) return false;

        if (screen instanceof CreativeInventoryScreen) {
            // Creative's cursor is client-side. Sync the changed player slot through vanilla.
            var client = MinecraftClient.getInstance();
            var player = client.player;
            var interaction = client.interactionManager;
            if (player == null || interaction == null || !interaction.hasCreativeInventory()
                    || slot.inventory != player.getInventory() || !slot.canTakePartial(player)) return false;
            for (var playerSlot : player.playerScreenHandler.slots) {
                if (playerSlot.getStack() != slot.getStack()) continue;
                if (ExplorersCompassItem.consumeFuel(costs, slot.getStack(), cursor, wholeStack)) {
                    if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
                    slot.markDirty();
                    interaction.clickCreativeStack(slot.getStack(), playerSlot.id);
                    ExplorersCompassItem.playChargeSound(player, player);
                }
                return true;
            }
            return false;
        }
        if (!ClientPlayNetworking.canSend(ConsumeCompassItemsPacket.ID)) return false;

        var packet = PacketByteBufs.create();
        packet.writeVarInt(handler.syncId);
        packet.writeVarInt(slot.id);
        packet.writeBoolean(wholeStack);
        ClientPlayNetworking.send(ConsumeCompassItemsPacket.ID, packet);
        return true;
    }
}
