package io.github.enriquemetner.explorerscompass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.recipe.ClassicCompassRecipe;
import io.github.enriquemetner.explorerscompass.util.VisitedStructures;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.workers.WorldWorkerManager;
import io.github.enriquemetner.explorerscompass.network.SearchPacket;
import io.github.enriquemetner.explorerscompass.network.SearchListedPacket;
import io.github.enriquemetner.explorerscompass.network.CancelSearchPacket;
import io.github.enriquemetner.explorerscompass.network.ContinueSearchPacket;
import io.github.enriquemetner.explorerscompass.network.ResetCompassPacket;
import io.github.enriquemetner.explorerscompass.network.SyncPacket;
import io.github.enriquemetner.explorerscompass.network.CurrentStructurePacket;
import io.github.enriquemetner.explorerscompass.network.ConsumeCompassItemsPacket;
import io.github.enriquemetner.explorerscompass.network.TeleportPacket;
import io.github.enriquemetner.explorerscompass.network.CompassSettingsPacket;
import io.github.enriquemetner.explorerscompass.network.RefreshCompassPacket;
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
import net.minecraft.util.Identifier;

public class ExplorersCompass implements ModInitializer {

	public static final String MODID = "explorerscompass";
	/** Shared texture for compass icons that should stay still. */
	public static final Identifier STILL_COMPASS_ICON = new Identifier(MODID, "textures/gui/ftb_reward_compass.png");

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static final ExplorersCompassItem EXPLORERS_COMPASS_ITEM = new ExplorersCompassItem();

	public static Map<Identifier, io.github.enriquemetner.explorerscompass.unlocking.RequirementNode> lockedRequirements = Map.of();
	public static boolean synced;
	public static boolean canTeleport;
	public static boolean canExcludeVisited;
	public static boolean infiniteXp;
	public static List<Identifier> allowedStructureIDs;
	public static ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs;
	public static Map<Identifier, Identifier> structureIDsToSetIDs;

	@Override
	public void onInitialize() {
		ExplorersCompassConfig.load();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("openpartiesandclaims"))
            io.github.enriquemetner.explorerscompass.compat.opac.OpacIntegration.register();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ftbquests"))
			io.github.enriquemetner.explorerscompass.compat.ftb.FtbQuestIntegration.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ExplorersCompassConfig.loadServer(server);
            ClassicCompassRecipe.apply(server);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server, world) -> {
            if (!ExplorersCompassConfig.findOldStructures) return;
            var worker = world.getChunkManager().threadedAnvilChunkStorage.getWorker();
            if (worker instanceof io.github.enriquemetner.explorerscompass.storage.EmptyChunkStorage backend) {
                var index = io.github.enriquemetner.explorerscompass.storage.SavedStructureIndex.get(world);
                backend.explorerscompass$attachIndex(index, world.getPersistentStateManager()::save);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var rules = io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.reload(server);
            var costs = io.github.enriquemetner.explorerscompass.config.SearchCosts.load(server);
            var available = io.github.enriquemetner.explorerscompass.util.StructureUtils.getEligibleStructureIDs(server.getOverworld());
            ExplorersCompassConfig.logStartup(available, rules, costs);
            io.github.enriquemetner.explorerscompass.unlocking.UnlockReloads.tick(server);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Invalidate queued continuations before shutdown can drain the server task queue.
            WorldWorkerManager.clear(server);
            EXPLORERS_COMPASS_ITEM.stopAllSearches();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EXPLORERS_COMPASS_ITEM.stopAllSearches();
            io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules.clear();
            io.github.enriquemetner.explorerscompass.config.StartupConfigReport.clear();
            io.github.enriquemetner.explorerscompass.unlocking.UnlockReloads.clear();
            io.github.enriquemetner.explorerscompass.unlocking.PartySharing.clear();
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                io.github.enriquemetner.explorerscompass.unlocking.UnlockingCommands.register(dispatcher));

		Registry.register(Registries.ITEM, new Identifier(MODID, "explorerscompass"), EXPLORERS_COMPASS_ITEM);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(EXPLORERS_COMPASS_ITEM));

        ServerPlayNetworking.registerGlobalReceiver(
                ConsumeCompassItemsPacket.ID,
                ConsumeCompassItemsPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(CurrentStructurePacket.ID, CurrentStructurePacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(SearchPacket.ID, SearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(SearchListedPacket.ID, SearchListedPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(CancelSearchPacket.ID, CancelSearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(ContinueSearchPacket.ID, ContinueSearchPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(ResetCompassPacket.ID, ResetCompassPacket::apply);
		ServerPlayNetworking.registerGlobalReceiver(RefreshCompassPacket.ID, RefreshCompassPacket::apply);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EXPLORERS_COMPASS_ITEM.cancelSearch(handler.player, -1));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VisitedStructures.get(server).forgetPosition(handler.player.getUuid()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				io.github.enriquemetner.explorerscompass.unlocking.UnlockReloads.left(handler.player.getUuid()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				CompassSettingsPacket.send(handler.player));
        // Also validate idle sessions awaiting "Keep searching" during normal gameplay.
        // Active searches validate ownership in every queued slice, including while paused.
        ServerTickEvents.START_SERVER_TICK.register(EXPLORERS_COMPASS_ITEM::tickSearchOwnership);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			io.github.enriquemetner.explorerscompass.unlocking.UnlockReloads.tick(server);
            io.github.enriquemetner.explorerscompass.unlocking.PartySharing.synchronize(server);
			if (server.getTicks() % 20 != 0 || server.getPlayerManager().getPlayerList().isEmpty()) return;
			VisitedStructures history = VisitedStructures.get(server);
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				io.github.enriquemetner.explorerscompass.unlocking.UnlockNotifications.deliverPending(player);
				history.checkPlayer(player);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(TeleportPacket.ID, TeleportPacket::apply);

		allowedStructureIDs = new ArrayList<Identifier>();
		allowedStructureIDsToDimensionIDs = ArrayListMultimap.create();
		structureIDsToSetIDs = new HashMap<Identifier, Identifier>();
	}

}
