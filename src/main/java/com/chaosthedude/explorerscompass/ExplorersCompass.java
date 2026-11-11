package com.chaosthedude.explorerscompass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.workers.WorldWorkerManager;
import com.chaosthedude.explorerscompass.network.SearchPacket;
import com.chaosthedude.explorerscompass.network.SearchListedPacket;
import com.chaosthedude.explorerscompass.network.CancelSearchPacket;
import com.chaosthedude.explorerscompass.network.ContinueSearchPacket;
import com.chaosthedude.explorerscompass.network.ResetCompassPacket;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.network.TeleportPacket;
import com.chaosthedude.explorerscompass.network.RepairDurabilityPacket;
import com.chaosthedude.explorerscompass.network.RefreshCompassPacket;
import com.chaosthedude.explorerscompass.recipe.CompassRepairRecipe;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.util.Identifier;

public class ExplorersCompass implements ModInitializer {

	public static final String MODID = "explorerscompass";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static final ExplorersCompassItem EXPLORERS_COMPASS_ITEM = new ExplorersCompassItem();
	public static final RecipeSerializer<CompassRepairRecipe> COMPASS_REPAIR_SERIALIZER =
			new SpecialRecipeSerializer<>(CompassRepairRecipe::new);

	public static Map<Identifier, com.chaosthedude.explorerscompass.unlocking.RequirementNode> lockedRequirements = Map.of();
	public static boolean synced;
	public static boolean canTeleport;
	public static boolean canExcludeVisited;
	public static boolean infiniteXp;
	public static List<Identifier> allowedStructureIDs;
	public static int defaultXpCost;
	public static boolean allowSearchWithoutEnoughXp;
	public static ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs;
	public static Map<Identifier, Identifier> structureIDsToSetIDs;

	@Override
	public void onInitialize() {
		ExplorersCompassConfig.load();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("openpartiesandclaims"))
            com.chaosthedude.explorerscompass.compat.opac.OpacIntegration.register();
        com.chaosthedude.explorerscompass.network.AcknowledgeNoticePacket.register();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ftbquests"))
			com.chaosthedude.explorerscompass.compat.ftb.FtbQuestIntegration.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server ->
                com.chaosthedude.explorerscompass.unlocking.UnlockingRules.reload(server));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Invalidate queued continuations before shutdown can drain the server task queue.
            WorldWorkerManager.clear(server);
            EXPLORERS_COMPASS_ITEM.stopAllSearches();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EXPLORERS_COMPASS_ITEM.stopAllSearches();
            com.chaosthedude.explorerscompass.unlocking.UnlockingRules.clear();
            com.chaosthedude.explorerscompass.unlocking.UnlockReloads.clear();
            com.chaosthedude.explorerscompass.unlocking.PartySharing.clear();
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                com.chaosthedude.explorerscompass.unlocking.UnlockingCommands.register(dispatcher));

		Registry.register(Registries.ITEM, new Identifier(MODID, "explorerscompass"), EXPLORERS_COMPASS_ITEM);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier(MODID, "compass_repair"), COMPASS_REPAIR_SERIALIZER);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(EXPLORERS_COMPASS_ITEM));

		ServerPlayNetworking.registerGlobalReceiver(SearchPacket.ID, SearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(SearchListedPacket.ID, SearchListedPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(CancelSearchPacket.ID, CancelSearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(ContinueSearchPacket.ID, ContinueSearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(ResetCompassPacket.ID, ResetCompassPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(RefreshCompassPacket.ID, RefreshCompassPacket::apply);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EXPLORERS_COMPASS_ITEM.cancelSearch(handler.player, -1));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VisitedStructures.get(server).forgetPosition(handler.player.getUuid()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				com.chaosthedude.explorerscompass.unlocking.UnlockReloads.left(handler.player.getUuid()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				RepairDurabilityPacket.send(handler.player, ExplorersCompassConfig.compassDurability));
        // Also validate idle sessions awaiting "Keep searching" during normal gameplay.
        // Active searches validate ownership in every queued slice, including while paused.
        ServerTickEvents.START_SERVER_TICK.register(EXPLORERS_COMPASS_ITEM::tickSearchOwnership);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			com.chaosthedude.explorerscompass.unlocking.UnlockReloads.tick(server);
            com.chaosthedude.explorerscompass.unlocking.PartySharing.synchronize(server);
			if (server.getTicks() % 20 != 0 || server.getPlayerManager().getPlayerList().isEmpty()) return;
			VisitedStructures history = VisitedStructures.get(server);
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				com.chaosthedude.explorerscompass.unlocking.UnlockNotifications.deliverPending(player);
				history.checkPlayer(player);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(TeleportPacket.ID, TeleportPacket::apply);

		allowedStructureIDs = new ArrayList<Identifier>();
		allowedStructureIDsToDimensionIDs = ArrayListMultimap.create();
		structureIDsToSetIDs = new HashMap<Identifier, Identifier>();
	}

}
