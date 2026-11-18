package com.chaosthedude.explorerscompass.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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

 public SyncPacket(boolean canTeleport, boolean canExcludeVisited, boolean infiniteXp, List<Identifier> allowedStructureIDs, ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs, Map<Identifier, Identifier> structureIDsToSetIDs, Map<Identifier, RequirementNode> lockedRequirements, Map<Identifier, RequirementNode> partyRequirements, long noticeRevision, boolean noticeRelocked, Identifier trackedStructureId, java.util.Set<Identifier> latestUnlocked, boolean showNewlyUnlockedFilter, List<Identifier> excludedIds, boolean whitelistActive) {
		super(Unpooled.buffer());
		writeBoolean(canTeleport);
		writeBoolean(canExcludeVisited);
		writeBoolean(infiniteXp);
		writeInt(allowedStructureIDs.size());
		for (Identifier structureID : allowedStructureIDs) {
			writeIdentifier(structureID);
			List<Identifier> dimensionIDs = allowedStructureIDsToDimensionIDs.get(structureID);
			writeInt(dimensionIDs.size());
			for (Identifier dimensionID : dimensionIDs) {
				writeIdentifier(dimensionID);
			}
			Identifier setID = structureIDsToSetIDs.get(structureID);
			writeIdentifier(setID);
		}
        writeBoolean(showNewlyUnlockedFilter);
        writeVarInt(latestUnlocked.size());
        latestUnlocked.stream().sorted().forEach(this::writeIdentifier);
        writeBoolean(trackedStructureId != null);
        if (trackedStructureId != null) writeIdentifier(trackedStructureId);
        writeLong(noticeRevision);
        writeBoolean(noticeRelocked);
        // Source/set rules often share a tree across many structures; send each tree once.
        var uniqueDescriptions = new LinkedHashSet<>(lockedRequirements.values());
        uniqueDescriptions.addAll(partyRequirements.values());
        List<RequirementNode> descriptions = new ArrayList<>(uniqueDescriptions);
        writeInt(descriptions.size());
        for (RequirementNode node : descriptions) node.write(this);
        writeRequirements(lockedRequirements, descriptions);
        writeRequirements(partyRequirements, descriptions);
        writeCollection(excludedIds, PacketByteBuf::writeIdentifier);
        writeBoolean(whitelistActive);
    }

    private void writeRequirements(Map<Identifier, RequirementNode> requirements, List<RequirementNode> descriptions) {
        writeInt(requirements.size());
        for (Map.Entry<Identifier, RequirementNode> entry : requirements.entrySet()) {
            writeIdentifier(entry.getKey());
            writeInt(descriptions.indexOf(entry.getValue()));
        }
    }
}
