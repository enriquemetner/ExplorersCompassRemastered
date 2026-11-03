package com.chaosthedude.explorerscompass.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.unlocking.RequirementNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class SyncPacket extends PacketByteBuf {

	public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "sync");

 public SyncPacket(boolean canTeleport, boolean canExcludeVisited, boolean infiniteXp, int defaultXpCost, boolean allowSearchWithoutEnoughXp, List<Identifier> allowedStructureIDs, ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs, Map<Identifier, Identifier> structureIDsToGroupIDs, Map<Identifier, RequirementNode> lockedRequirements, long noticeRevision, boolean noticeRelocked, Identifier trackedStructureId, java.util.Set<Identifier> latestUnlocked, boolean showNewlyUnlockedFilter) {
		super(Unpooled.buffer());
		writeBoolean(canTeleport);
		writeBoolean(canExcludeVisited);
		writeBoolean(infiniteXp);
		writeInt(defaultXpCost);
		writeBoolean(allowSearchWithoutEnoughXp);
		writeInt(allowedStructureIDs.size());
		for (Identifier structureID : allowedStructureIDs) {
			writeIdentifier(structureID);
			List<Identifier> dimensionIDs = allowedStructureIDsToDimensionIDs.get(structureID);
			writeInt(dimensionIDs.size());
			for (Identifier dimensionID : dimensionIDs) {
				writeIdentifier(dimensionID);
			}
			Identifier groupID = structureIDsToGroupIDs.get(structureID);
			writeIdentifier(groupID);
		}
        writeBoolean(showNewlyUnlockedFilter);
        writeVarInt(latestUnlocked.size());
        latestUnlocked.stream().sorted().forEach(this::writeIdentifier);
        writeBoolean(trackedStructureId != null);
        if (trackedStructureId != null) writeIdentifier(trackedStructureId);
        writeLong(noticeRevision);
        writeBoolean(noticeRelocked);
        // Source/set rules often share a tree across many structures; send each tree once.
        List<RequirementNode> descriptions = new ArrayList<>(new java.util.LinkedHashSet<>(lockedRequirements.values()));
        writeInt(descriptions.size());
        for (RequirementNode node : descriptions) node.write(this);
        writeInt(lockedRequirements.size());
        for (Map.Entry<Identifier, RequirementNode> entry : lockedRequirements.entrySet()) {
            writeIdentifier(entry.getKey());
            writeInt(descriptions.indexOf(entry.getValue()));
        }

	}

}
