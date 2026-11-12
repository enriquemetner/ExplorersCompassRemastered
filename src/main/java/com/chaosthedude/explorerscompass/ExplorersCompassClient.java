package com.chaosthedude.explorerscompass;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.ClientPacketHandlers;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
import com.chaosthedude.explorerscompass.network.SearchProgressPacket;
import com.chaosthedude.explorerscompass.network.UnlockToastPacket;
import com.chaosthedude.explorerscompass.network.RepairDurabilityPacket;
import com.chaosthedude.explorerscompass.network.RulesChangedPacket;
import com.chaosthedude.explorerscompass.recipe.CompassRepairRecipe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

public class ExplorersCompassClient implements ClientModInitializer {
    private final com.chaosthedude.explorerscompass.client.CompassNeedle needle = new com.chaosthedude.explorerscompass.client.CompassNeedle();
	
	@Override
	public void onInitializeClient() {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ftbquests"))
			com.chaosthedude.explorerscompass.compat.ftb.FtbQuestClientIntegration.register();
		ClientPlayNetworking.registerGlobalReceiver(SyncPacket.ID, ClientPacketHandlers::applySync);
		ClientPlayNetworking.registerGlobalReceiver(SearchResultPacket.ID, ClientPacketHandlers::applySearchResult);
		ClientPlayNetworking.registerGlobalReceiver(SearchProgressPacket.ID, ClientPacketHandlers::applySearchProgress);
        ClientPlayNetworking.registerGlobalReceiver(com.chaosthedude.explorerscompass.network.SearchAvailabilityPacket.ID, ClientPacketHandlers::applySearchAvailability);
		ClientPlayNetworking.registerGlobalReceiver(UnlockToastPacket.ID, ClientPacketHandlers::applyUnlockToast);
		ClientPlayNetworking.registerGlobalReceiver(RepairDurabilityPacket.ID, ClientPacketHandlers::applyRepairDurability);
		ClientPlayNetworking.registerGlobalReceiver(RulesChangedPacket.ID, ClientPacketHandlers::applyRulesChanged);
        ClientPlayNetworking.registerGlobalReceiver(com.chaosthedude.explorerscompass.network.PartyRefreshPacket.ID, ClientPacketHandlers::applyPartyRefresh);
        // Minecraft can discard queued client work during disconnect.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ExplorersCompassScreen.clearWorldSearchState());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CompassRepairRecipe.clearServerDurability();
            needle.clear();
            ClientPacketHandlers.shownNoticeRevision = 0;
            ClientPacketHandlers.pendingNoticeRevision = 0;
            ClientPacketHandlers.blacklistedStructures = java.util.Set.of();
            ClientPacketHandlers.partyRequirements = java.util.Map.of();
            ClientPacketHandlers.latestUnlockedStructures = java.util.Set.of();
            ClientPacketHandlers.showNewlyUnlockedFilter = false;
            client.execute(ExplorersCompassScreen::clearWorldSearchState);
        });
		
		ModelPredicateProviderRegistry.register(ExplorersCompass.EXPLORERS_COMPASS_ITEM, new Identifier("angle"), needle);
	}

}
