package com.chaosthedude.explorerscompass.network;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.gui.UnlockToast;
import com.chaosthedude.explorerscompass.recipe.CompassRepairRecipe;
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
    public static java.util.Set<Identifier> latestUnlockedStructures = java.util.Set.of();
    public static boolean pendingNoticeRelocked;

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

    public static void applySearchResult(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int requestId = buf.readInt(), result = buf.readInt(), radius = buf.readInt();
        boolean canContinue = buf.readBoolean();
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
        final Identifier resultId = foundId;
        final int resultX = foundX, resultZ = foundZ;
        final boolean showCoordinates = displayCoordinates;
        final boolean showDistance = displayDistance;
        client.execute(() -> {
            if (client.currentScreen instanceof ExplorersCompassScreen screen) {
                screen.onSearchResult(requestId, result, radius, canContinue, resultId, resultX, resultZ, showCoordinates, showDistance);
            }
        });
    }

    public static void applyRepairDurability(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        int durability = buf.readVarInt();
        client.execute(() -> CompassRepairRecipe.setServerDurability(durability));
    }

    public static void applySync(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        final boolean canTeleport = buf.readBoolean();
        final boolean canExcludeVisited = buf.readBoolean();
        final boolean infiniteXp = buf.readBoolean();
        final int defaultXpCost = buf.readInt();
        final boolean allowSearchWithoutEnoughXp = buf.readBoolean();
        final List<Identifier> allowedStructureIDs = new ArrayList<Identifier>();
        final ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs = ArrayListMultimap.create();
        final Map<Identifier, Identifier> structureIDsToGroupIDs = new HashMap<Identifier, Identifier>();

        final int numStructures = buf.readInt();
        for (int i = 0; i < numStructures; i++) {
            Identifier structureID = buf.readIdentifier();
            int numDimensions = buf.readInt();
            List<Identifier> dimensionIDs = new ArrayList<Identifier>();
            for (int j = 0; j < numDimensions; j++) {
                dimensionIDs.add(buf.readIdentifier());
            }
            Identifier groupID = buf.readIdentifier();

            if (structureID != null) {
                allowedStructureIDs.add(structureID);
                allowedStructureIDsToDimensionIDs.putAll(structureID, dimensionIDs);
                structureIDsToGroupIDs.put(structureID, groupID);
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
        Map<Identifier, RequirementNode> lockedRequirements = new HashMap<>();
        int lockedCount = buf.readInt();
        for (int i = 0; i < lockedCount; i++) lockedRequirements.put(buf.readIdentifier(), descriptions.get(buf.readInt()));

        client.execute(() -> {
            ExplorersCompass.lockedRequirements = Map.copyOf(lockedRequirements);
            ExplorersCompass.synced = true;
            trackedStructureId = tracked;
            latestUnlockedStructures = java.util.Set.copyOf(latest);
            showNewlyUnlockedFilter = showLatestFilter;
            if (!showLatestFilter) com.chaosthedude.explorerscompass.gui.FilterSelections.clearNewlyUnlocked();
            pendingNoticeRevision = noticeRevision;
            pendingNoticeRelocked = noticeRelocked;
            ExplorersCompass.canTeleport = canTeleport;
            ExplorersCompass.canExcludeVisited = canExcludeVisited;
            ExplorersCompass.infiniteXp = infiniteXp;
            ExplorersCompass.allowedStructureIDs = allowedStructureIDs;
            ExplorersCompass.defaultXpCost = defaultXpCost;
            ExplorersCompass.allowSearchWithoutEnoughXp = allowSearchWithoutEnoughXp;
            ExplorersCompass.allowedStructureIDsToDimensionIDs = allowedStructureIDsToDimensionIDs;
            ExplorersCompass.structureIDsToGroupIDs = structureIDsToGroupIDs;
        });
    }
}
