package io.github.enriquemetner.explorerscompass.network;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.unlocking.RequirementNode;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class SyncPacket extends PacketByteBuf {
    public static final Identifier ID = new Identifier(ExplorersCompass.MODID, "sync");

    public record Contents(boolean canTeleport, boolean canExcludeVisited, boolean infiniteXp,
                           List<Identifier> allowedStructureIDs, ListMultimap<Identifier, Identifier> dimensionIDs,
                           Map<Identifier, Identifier> setIDs, Map<Identifier, RequirementNode> lockedRequirements,
                           Map<Identifier, RequirementNode> partyRequirements, Identifier trackedStructureId,
                           Set<Identifier> latestUnlocked, boolean showNewlyUnlockedFilter,
                           Set<Identifier> excludedStructures, boolean whitelistActive, boolean showStructureRequirements) {}

    public SyncPacket(boolean canTeleport, boolean canExcludeVisited, boolean infiniteXp,
            List<Identifier> allowedStructureIDs, ListMultimap<Identifier, Identifier> allowedStructureIDsToDimensionIDs,
            Map<Identifier, Identifier> structureIDsToSetIDs, Map<Identifier, RequirementNode> lockedRequirements,
            Map<Identifier, RequirementNode> partyRequirements, Identifier trackedStructureId, Set<Identifier> latestUnlocked,
            boolean showNewlyUnlockedFilter, List<Identifier> excludedIds, boolean whitelistActive,
            boolean showStructureRequirements) {
        super(Unpooled.buffer());
        writeBoolean(canTeleport);
        writeBoolean(canExcludeVisited);
        writeBoolean(infiniteXp);
        writeInt(allowedStructureIDs.size());
        for (Identifier structureID : allowedStructureIDs) {
            writeIdentifier(structureID);
            List<Identifier> dimensionIDs = allowedStructureIDsToDimensionIDs.get(structureID);
            writeInt(dimensionIDs.size());
            for (Identifier dimensionID : dimensionIDs) writeIdentifier(dimensionID);
            writeIdentifier(structureIDsToSetIDs.get(structureID));
        }

        writeBoolean(showNewlyUnlockedFilter);
        writeVarInt(latestUnlocked.size());
        latestUnlocked.stream().sorted().forEach(this::writeIdentifier);
        writeBoolean(trackedStructureId != null);
        if (trackedStructureId != null) writeIdentifier(trackedStructureId);

        // Assign indices in first-use order across both maps, with personal requirements first.
        Map<RequirementNode, Integer> descriptions = new LinkedHashMap<>();
        for (var requirements : List.of(lockedRequirements, partyRequirements)) {
            for (var node : requirements.values()) descriptions.putIfAbsent(node, descriptions.size());
        }
        writeInt(descriptions.size());
        for (RequirementNode node : descriptions.keySet()) node.write(this);
        writeRequirements(lockedRequirements, descriptions);
        writeRequirements(partyRequirements, descriptions);

        writeCollection(excludedIds, PacketByteBuf::writeIdentifier);
        writeBoolean(whitelistActive);
        writeBoolean(showStructureRequirements);
    }

    private void writeRequirements(Map<Identifier, RequirementNode> requirements, Map<RequirementNode, Integer> descriptions) {
        writeInt(requirements.size());
        for (Map.Entry<Identifier, RequirementNode> entry : requirements.entrySet()) {
            writeIdentifier(entry.getKey());
            writeInt(descriptions.get(entry.getValue()));
        }
    }

    public static Contents read(PacketByteBuf buf) {
        boolean canTeleport = buf.readBoolean();
        boolean canExcludeVisited = buf.readBoolean();
        boolean infiniteXp = buf.readBoolean();
        List<Identifier> allowedStructureIDs = new ArrayList<>();
        ListMultimap<Identifier, Identifier> dimensionsByStructure = ArrayListMultimap.create();
        Map<Identifier, Identifier> setsByStructure = new HashMap<>();

        int numStructures = buf.readInt();
        for (int i = 0; i < numStructures; i++) {
            Identifier structureID = buf.readIdentifier();
            int numDimensions = buf.readInt();
            List<Identifier> dimensions = new ArrayList<>();
            for (int j = 0; j < numDimensions; j++) dimensions.add(buf.readIdentifier());
            Identifier setID = buf.readIdentifier();
            if (structureID != null) {
                allowedStructureIDs.add(structureID);
                dimensionsByStructure.putAll(structureID, dimensions);
                setsByStructure.put(structureID, setID);
            }
        }

        boolean showLatestFilter = buf.readBoolean();
        Set<Identifier> latest = readIdSet(buf, "Invalid latest unlocked count");
        Identifier tracked = buf.readBoolean() ? buf.readIdentifier() : null;
        List<RequirementNode> descriptions = new ArrayList<>();
        int descriptionCount = buf.readInt();
        for (int i = 0; i < descriptionCount; i++) descriptions.add(RequirementNode.read(buf));
        Map<Identifier, RequirementNode> locked = readRequirements(buf, descriptions, numStructures);

        Map<Identifier, RequirementNode> party = buf.isReadable()
                ? readRequirements(buf, descriptions, numStructures) : Map.of();
        Set<Identifier> excluded = buf.isReadable()
                ? readIdSet(buf, "Invalid excluded structure count") : Set.of();
        boolean whitelist = buf.isReadable() && buf.readBoolean();
        boolean showRequirements = buf.isReadable() ? buf.readBoolean() : showLatestFilter;

        return new Contents(canTeleport, canExcludeVisited, infiniteXp, allowedStructureIDs, dimensionsByStructure,
                setsByStructure, locked, party, tracked, latest, showLatestFilter, excluded, whitelist, showRequirements);
    }

    private static Set<Identifier> readIdSet(PacketByteBuf buf, String invalidCountMessage) {
        int count = buf.readVarInt();
        if (count < 0 || count > 100_000) throw new IllegalArgumentException(invalidCountMessage);
        Set<Identifier> ids = new HashSet<>();
        for (int i = 0; i < count; i++) ids.add(buf.readIdentifier());
        return Set.copyOf(ids);
    }

    private static Map<Identifier, RequirementNode> readRequirements(PacketByteBuf buf, List<RequirementNode> descriptions, int maxEntries) {
        int count = buf.readInt();
        if (count < 0 || count > maxEntries) throw new IllegalArgumentException("Invalid requirements count");
        Map<Identifier, RequirementNode> requirements = new HashMap<>();
        for (int i = 0; i < count; i++) requirements.put(buf.readIdentifier(), descriptions.get(buf.readInt()));
        return Map.copyOf(requirements);
    }
}
