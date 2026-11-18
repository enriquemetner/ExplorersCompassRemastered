package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.gui.FilterSelections;
import com.chaosthedude.explorerscompass.gui.UnlockToast;
import com.chaosthedude.explorerscompass.config.SearchCosts;
import com.chaosthedude.explorerscompass.unlocking.RequirementNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public static long pendingNoticeRevision;
    public static long shownNoticeRevision;
    public static Identifier trackedStructureId;
    public static boolean showNewlyUnlockedFilter;
    public static boolean whitelistActive;
    public static boolean showNetherEquivalent;
    public static java.util.Set<Identifier> excludedStructures = java.util.Set.of();
    public static Map<Identifier, RequirementNode> partyRequirements = Map.of();
    public static java.util.Set<Identifier> latestUnlockedStructures = java.util.Set.of();
    public static boolean pendingNoticeRelocked;

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

    public static void applyRulesChanged(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.prepareRuleRefresh();
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(RefreshCompassPacket.ID, new RefreshCompassPacket());
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
            if (count < 0 || count > com.chaosthedude.explorerscompass.util.StructureDestination.MAX_BIOME_SAMPLES) {
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
        var items = buf.readMap(PacketByteBuf::readIdentifier, PacketByteBuf::readVarInt);
        boolean equivalent = buf.readBoolean();
        int defaultCost = buf.readVarInt();
        var overrides = buf.readMap(PacketByteBuf::readIdentifier, PacketByteBuf::readVarInt);
        client.execute(() -> {
            SearchCosts.setRemote(type, defaultCost, overrides, energy, items);
            showNetherEquivalent = equivalent;
        });
    }

    public static void applySync(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        final boolean canTeleport = buf.readBoolean();
        final boolean canExcludeVisited = buf.readBoolean();
        final boolean infiniteXp = buf.readBoolean();
        final List<Identifier> allowedStructureIDs = new ArrayList<Identifier>();
        final ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs = ArrayListMultimap.create();
        final Map<Identifier, Identifier> structureIDsToSetIDs = new HashMap<Identifier, Identifier>();

        final int numStructures = buf.readInt();
        for (int i = 0; i < numStructures; i++) {
            Identifier structureID = buf.readIdentifier();
            int numDimensions = buf.readInt();
            List<Identifier> dimensionIDs = new ArrayList<Identifier>();
            for (int j = 0; j < numDimensions; j++) {
                dimensionIDs.add(buf.readIdentifier());
            }
            Identifier setID = buf.readIdentifier();

            if (structureID != null) {
                allowedStructureIDs.add(structureID);
                allowedStructureIDsToDimensionIDs.putAll(structureID, dimensionIDs);
                structureIDsToSetIDs.put(structureID, setID);
            }
        }

        boolean showLatestFilter = buf.readBoolean();
        int latestCount = buf.readVarInt();
        if (latestCount < 0 || latestCount > 100000) throw new IllegalArgumentException("Invalid latest unlocked count");
        java.util.Set<Identifier> latest = new java.util.HashSet<>();
        for (int i = 0; i < latestCount; i++) latest.add(buf.readIdentifier());
        Identifier tracked = buf.readBoolean() ? buf.readIdentifier() : null;
        long noticeRevision = buf.readLong();
        boolean noticeRelocked = buf.readBoolean();
        List<RequirementNode> descriptions = new ArrayList<>();
        int descriptionCount = buf.readInt();
        for (int i = 0; i < descriptionCount; i++) descriptions.add(RequirementNode.read(buf));
        Map<Identifier, RequirementNode> lockedRequirements = readRequirements(buf, descriptions, numStructures);
        Map<Identifier, RequirementNode> shared = buf.isReadable()
                ? readRequirements(buf, descriptions, numStructures) : Map.of();
        java.util.Set<Identifier> excluded = new java.util.HashSet<>();
        if (buf.isReadable()) {
            int count = buf.readVarInt();
            if (count < 0 || count > 100000) throw new IllegalArgumentException("Invalid excluded structure count");
            for (int i = 0; i < count; i++) excluded.add(buf.readIdentifier());
        }

        boolean whitelist = buf.isReadable() && buf.readBoolean();
        client.execute(() -> {
            whitelistActive = whitelist;
            ExplorersCompass.lockedRequirements = lockedRequirements;
            partyRequirements = shared;
            excludedStructures = java.util.Set.copyOf(excluded);
            ExplorersCompass.synced = true;
            trackedStructureId = tracked;
            latestUnlockedStructures = java.util.Set.copyOf(latest);
            showNewlyUnlockedFilter = showLatestFilter;
            if (!showLatestFilter && FilterSelections.newlyUnlocked()) {
                FilterSelections.clearNewlyUnlocked();
                if (client.currentScreen instanceof ExplorersCompassScreen screen) screen.showNormalResults();
            }
            pendingNoticeRevision = noticeRevision;
            pendingNoticeRelocked = noticeRelocked;
            ExplorersCompass.canTeleport = canTeleport;
            ExplorersCompass.canExcludeVisited = canExcludeVisited;
            ExplorersCompass.infiniteXp = infiniteXp;
            ExplorersCompass.allowedStructureIDs = allowedStructureIDs;
            ExplorersCompass.allowedStructureIDsToDimensionIDs = allowedStructureIDsToDimensionIDs;
            ExplorersCompass.structureIDsToSetIDs = structureIDsToSetIDs;
        });
    }
    private static Map<Identifier, RequirementNode> readRequirements(PacketByteBuf buf, List<RequirementNode> descriptions, int maxEntries) {
        int count = buf.readInt();
        if (count < 0 || count > maxEntries) throw new IllegalArgumentException("Invalid requirements count");
        Map<Identifier, RequirementNode> requirements = new HashMap<>();
        for (int i = 0; i < count; i++) requirements.put(buf.readIdentifier(), descriptions.get(buf.readInt()));
        return Map.copyOf(requirements);
    }
}
