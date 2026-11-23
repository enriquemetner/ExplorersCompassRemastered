package io.github.enriquemetner.explorerscompass.network;

import io.github.enriquemetner.explorerscompass.network.compass.RefreshCompassPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchAvailabilityPacket;
import io.github.enriquemetner.explorerscompass.network.search.SearchResultPacket;
import io.github.enriquemetner.explorerscompass.network.unlocking.StructureRequirementsPacket;
import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.gui.ExplorersCompassScreen;
import io.github.enriquemetner.explorerscompass.gui.FilterSelections;
import io.github.enriquemetner.explorerscompass.gui.UnlockToast;
import io.github.enriquemetner.explorerscompass.config.SearchCosts;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Client receivers are isolated so server packet sending never loads GUI classes. */
@Environment(EnvType.CLIENT)
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}
    public static Identifier trackedStructureId;
    public static boolean showNewlyUnlockedFilter;
    public static boolean showStructureRequirements;
    public static boolean whitelistActive;
    public static boolean showNetherEquivalent;
    public static boolean useClassicCompass;
    public static Set<Identifier> excludedStructures = Set.of();
    public static Map<Identifier, RequirementNode> partyRequirements = Map.of();
    public static Set<Identifier> latestUnlockedStructures = Set.of();

    public static void applyCurrentStructure(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        var request = buf.readUuid();
        var dimension = buf.readIdentifier();
        var matches = buf.readList(PacketByteBuf::readIdentifier);
        client.execute(() -> {
            if (client.getNetworkHandler() == handler && client.world != null
                    && client.world.getRegistryKey().getValue().equals(dimension)
                    && client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.onCurrentStructure(request, matches);
            }
        });
    }

    public static void applyStructureRequirements(MinecraftClient client, ClientPlayNetworkHandler handler,
                                                  PacketByteBuf buf, PacketSender sender) {
        var response = StructureRequirementsPacket.readResponse(buf);
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.onStructureRequirements(response.requestId(), response.requirements());
            }
        });
    }

    public static void applySearchAvailability(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        var response = SearchAvailabilityPacket.read(buf);
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) screen.onSearchAvailability(response);
        });
    }
    public static void applyUnlockToast(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int count = buf.readVarInt();
        boolean party = buf.readBoolean();
        if (count > 0) client.execute(() -> client.getToastManager().add(new UnlockToast(count, party)));
    }

    public static void applyPartyRefresh(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.preparePartyRefresh();
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(RefreshCompassPacket.ID, new RefreshCompassPacket(false));
            }
        });
    }

    public static void applySearchProgress(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int requestId = buf.readInt(), radius = buf.readInt();
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.onSearchProgress(requestId, radius);
            }
        });
    }

    public static void applySearchResult(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int requestId = buf.readInt(), result = buf.readInt(), radius = buf.readInt();
        buf.readBoolean(); // Legacy search continuation flag.
        Identifier foundId = null;
        int foundX = 0, foundZ = 0;
        boolean displayCoordinates = false, displayDistance = false;
        if (buf.readBoolean()) {
            foundId = buf.readIdentifier();
            foundX = buf.readInt();
            foundZ = buf.readInt();
            displayCoordinates = buf.readBoolean();
            displayDistance = buf.readBoolean();
        }
        List<Identifier> biomes = new ArrayList<>();
        java.util.Set<Identifier> validBiomes = new java.util.HashSet<>();
        if (result == SearchResultPacket.FOUND && buf.isReadable()) {
            int count = buf.readVarInt();
            if (count < 0 || count > io.github.enriquemetner.explorerscompass.structure.StructureDestination.MAX_BIOME_SAMPLES) {
                throw new IllegalArgumentException("Invalid found biome count");
            }
            for (int i = 0; i < count; i++) biomes.add(buf.readIdentifier());
            int validBiomeMask = buf.isReadable() ? buf.readVarInt() : 0;
            for (int i = 0; i < count; i++) {
                if ((validBiomeMask & (1 << i)) != 0) validBiomes.add(biomes.get(i));
            }
        }
        Boolean visited = result == SearchResultPacket.FOUND && buf.isReadable() ? buf.readBoolean() : null;
        RequirementNode requirements = result == SearchResultPacket.LOCKED ? RequirementNode.read(buf) : null;
        final Identifier resultId = (result == SearchResultPacket.BLACKLISTED || result == SearchResultPacket.NOT_WHITELISTED) ? buf.readIdentifier() : foundId;
        int balance = (result == SearchResultPacket.INSUFFICIENT_COST || result == SearchResultPacket.MULTI_COST)
                ? buf.readVarInt() : 0;
        final int resultX = foundX, resultZ = foundZ;
        final boolean showCoordinates = displayCoordinates;
        final boolean showDistance = displayDistance;
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.onSearchResult(requestId, result, radius, resultId, resultX, resultZ, showCoordinates, showDistance, List.copyOf(biomes), java.util.Set.copyOf(validBiomes), visited, requirements, balance);
            }
        });
    }

    public static void applyCompassSettings(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        var type = buf.readEnumConstant(SearchCosts.Type.class);
        int energy = buf.readVarInt();
        var items = buf.readMap(LinkedHashMap::new, PacketByteBuf::readIdentifier, PacketByteBuf::readVarInt);
        boolean equivalent = buf.readBoolean();
        int defaultCost = buf.readVarInt();
        var overrides = buf.readMap(PacketByteBuf::readIdentifier, PacketByteBuf::readVarInt);
        boolean classic = buf.isReadable() && buf.readBoolean();
        client.execute(() -> {
            SearchCosts.setRemote(type, defaultCost, overrides, energy, items);
            showNetherEquivalent = equivalent;
            useClassicCompass = classic;
        });
    }

    public static void applySync(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        var sync = SyncPacket.read(buf);
        client.execute(() -> {
            whitelistActive = sync.whitelistActive();
            ExplorersCompass.lockedRequirements = sync.lockedRequirements();
            if (sync.lockedRequirements().isEmpty()) FilterSelections.clearExcludeLocked();
            partyRequirements = sync.partyRequirements();
            excludedStructures = sync.excludedStructures();
            ExplorersCompass.synced = true;
            trackedStructureId = sync.trackedStructureId();
            latestUnlockedStructures = sync.latestUnlocked();
            showNewlyUnlockedFilter = sync.showNewlyUnlockedFilter();
            showStructureRequirements = sync.showStructureRequirements();
            if (!sync.showNewlyUnlockedFilter() && FilterSelections.newlyUnlocked()) {
                FilterSelections.clearNewlyUnlocked();
                if (client.currentScreen instanceof ExplorersCompassScreen screen) screen.showNormalResults();
            }
            ExplorersCompass.canTeleport = sync.canTeleport();
            ExplorersCompass.canExcludeVisited = sync.canExcludeVisited();
            ExplorersCompass.infiniteXp = sync.infiniteXp();
            ExplorersCompass.allowedStructureIDs = sync.allowedStructureIDs();
            ExplorersCompass.allowedStructureIDsToDimensionIDs = sync.dimensionIDs();
            ExplorersCompass.structureIDsToSetIDs = sync.setIDs();
        });
    }

    public static void clearOnDisconnect() {
        whitelistActive = false;
        showNetherEquivalent = false;
        useClassicCompass = false;
        excludedStructures = Set.of();
        partyRequirements = Map.of();
        latestUnlockedStructures = Set.of();
        showNewlyUnlockedFilter = false;
        showStructureRequirements = false;
    }
}
