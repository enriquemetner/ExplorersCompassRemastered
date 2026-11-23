package io.github.enriquemetner.explorerscompass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import io.github.enriquemetner.explorerscompass.compat.ftb.FtbQuestIntegration;
import io.github.enriquemetner.explorerscompass.compat.opac.OpacIntegration;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.config.StartupConfigReport;
import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.network.compass.CompassSettingsPacket;
import io.github.enriquemetner.explorerscompass.network.compass.ConsumeCompassItemsPacket;
import io.github.enriquemetner.explorerscompass.network.compass.RefreshCompassPacket;
import io.github.enriquemetner.explorerscompass.network.compass.ResetCompassPacket;
import io.github.enriquemetner.explorerscompass.network.compass.TeleportPacket;
import io.github.enriquemetner.explorerscompass.network.search.CancelSearchPacket;
import io.github.enriquemetner.explorerscompass.network.search.ContinueSearchPacket;
import io.github.enriquemetner.explorerscompass.network.search.CurrentStructurePacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchListedPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchPacket;
import io.github.enriquemetner.explorerscompass.network.unlocking.StructureRequirementsPacket;
import io.github.enriquemetner.explorerscompass.recipe.ClassicCompassRecipe;
import io.github.enriquemetner.explorerscompass.storage.EmptyChunkStorage;
import io.github.enriquemetner.explorerscompass.storage.SavedStructureIndex;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import io.github.enriquemetner.explorerscompass.structure.VisitedStructures;
import io.github.enriquemetner.explorerscompass.unlocking.PartySharing;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockNotifications;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockReloads;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingCommands;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.workers.WorldWorkerManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public class ExplorersCompass implements ModInitializer {

    public static final String MODID = "explorerscompass";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final ExplorersCompassItem EXPLORERS_COMPASS_ITEM = new ExplorersCompassItem();

    public static Map<Identifier, RequirementNode> lockedRequirements = Map.of();
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
        var loader = FabricLoader.getInstance();
        if (loader.isModLoaded("openpartiesandclaims")) {
            OpacIntegration.register();
        }
        if (loader.isModLoaded("ftbquests")) {
            FtbQuestIntegration.register();
        }

        ServerLifecycleEvents.SERVER_STARTING.register(ExplorersCompass::onServerStarting);
        ServerWorldEvents.LOAD.register((server, world) -> attachSavedStructureIndex(world));
        ServerLifecycleEvents.SERVER_STARTED.register(ExplorersCompass::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ExplorersCompass::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(ExplorersCompass::onServerStopped);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                UnlockingCommands.register(dispatcher));

        Registry.register(Registries.ITEM, new Identifier(MODID, "explorerscompass"), EXPLORERS_COMPASS_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(EXPLORERS_COMPASS_ITEM));

        registerServerReceivers();
        ServerPlayConnectionEvents.DISCONNECT.register(ExplorersCompass::onPlayerDisconnect);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CompassSettingsPacket.send(handler.player));

        // Queued search slices also check ownership, including while the world is paused.
        ServerTickEvents.START_SERVER_TICK.register(EXPLORERS_COMPASS_ITEM::tickSearchOwnership);
        ServerTickEvents.END_SERVER_TICK.register(ExplorersCompass::onEndServerTick);

        allowedStructureIDs = new ArrayList<>();
        allowedStructureIDsToDimensionIDs = ArrayListMultimap.create();
        structureIDsToSetIDs = new HashMap<>();
    }

    private static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ConsumeCompassItemsPacket.ID, ConsumeCompassItemsPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(CurrentStructurePacket.ID, CurrentStructurePacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(SearchPacket.ID, SearchPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(SearchListedPacket.ID, SearchListedPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(CancelSearchPacket.ID, CancelSearchPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(ContinueSearchPacket.ID, ContinueSearchPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(ResetCompassPacket.ID, ResetCompassPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(RefreshCompassPacket.ID, RefreshCompassPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(StructureRequirementsPacket.ID, StructureRequirementsPacket::apply);
        ServerPlayNetworking.registerGlobalReceiver(TeleportPacket.ID, TeleportPacket::apply);
    }

    private static void onServerStarting(MinecraftServer server) {
        ExplorersCompassConfig.loadServer(server);
        ClassicCompassRecipe.apply(server);
    }

    private static void attachSavedStructureIndex(ServerWorld world) {
        if (!ExplorersCompassConfig.findOldStructures) return;
        var worker = world.getChunkManager().threadedAnvilChunkStorage.getWorker();
        if (worker instanceof EmptyChunkStorage backend) {
            var index = SavedStructureIndex.get(world);
            backend.explorerscompass$attachIndex(index, world.getPersistentStateManager()::save);
        }
    }

    private static void onServerStarted(MinecraftServer server) {
        var rules = UnlockingRules.reload(server);
        var costs = SearchCosts.load(server);
        var available = StructureUtils.getEligibleStructureIDs(server.getOverworld());
        ExplorersCompassConfig.logStartup(available, rules, costs);
        UnlockReloads.tick(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        // Invalidate queued search work before shutdown drains the server task queue.
        WorldWorkerManager.clear(server);
        EXPLORERS_COMPASS_ITEM.stopAllSearches();
    }

    private static void onServerStopped(MinecraftServer server) {
        EXPLORERS_COMPASS_ITEM.stopAllSearches();
        UnlockingRules.clear();
        StartupConfigReport.clear();
        UnlockReloads.clear();
        PartySharing.clear();
    }

    private static void onPlayerDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        var player = handler.player;
        EXPLORERS_COMPASS_ITEM.cancelSearch(player, -1);
        VisitedStructures.get(server).forgetPosition(player.getUuid());
        UnlockReloads.left(player.getUuid());
    }

    private static void onEndServerTick(MinecraftServer server) {
        UnlockReloads.tick(server);
        PartySharing.synchronize(server);
        if (server.getTicks() % 20 != 0) return;

        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        VisitedStructures history = VisitedStructures.get(server);
        for (ServerPlayerEntity player : players) {
            UnlockNotifications.deliverPending(player);
            history.checkPlayer(player);
        }
    }
}
