package io.github.enriquemetner.explorerscompass.unlocking;

import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A snapshot of a requirement and its satisfaction when the compass opens. */
public record RequirementNode(Text label, boolean checked, boolean logicalGroup,
                              List<RequirementNode> children, Text tooltipDescription, Long questId,
                              Identifier structureId, Identifier advancementId, boolean advancementHasDisplay, Identifier advancementTab) {
    public RequirementNode {
        children = List.copyOf(children);
    }

    public RequirementNode(Text label, boolean checked, boolean logicalGroup,
                           List<RequirementNode> children, Text tooltipDescription, Long questId) {
        this(label, checked, logicalGroup, children, tooltipDescription, questId, null, null, false, null);
    }

    public static RequirementNode structure(Text label, boolean checked, Identifier id) {
        return new RequirementNode(label, checked, false, List.of(), null, null, id, null, false, null);
    }


    public static RequirementNode advancement(Text label, boolean checked, Text description, Identifier id, boolean hasDisplay, Identifier tab) {
        return new RequirementNode(label, checked, false, List.of(), description, null, null, id, hasDisplay, tab);
    }

    public RequirementNode(Text label, boolean checked, List<RequirementNode> children) {
        this(label, checked, false, children, null, null);
    }

    public RequirementNode(Text label, boolean checked, boolean logicalGroup, List<RequirementNode> children) {
        this(label, checked, logicalGroup, children, null, null);
    }

    public RequirementNode(Text label, boolean checked, List<RequirementNode> children, long questId) {
        this(label, checked, false, children, null, questId);
    }

    public RequirementNode(Text label, boolean checked, boolean logicalGroup,
                           List<RequirementNode> children, Text tooltipDescription) {
        this(label, checked, logicalGroup, children, tooltipDescription, null);
    }

    public void write(PacketByteBuf buf) {
        buf.writeText(label);
        buf.writeBoolean(checked);
        buf.writeBoolean(logicalGroup);
        buf.writeBoolean(tooltipDescription != null);
        if (tooltipDescription != null) buf.writeText(tooltipDescription);
        buf.writeBoolean(questId != null);
        if (questId != null) buf.writeLong(questId);
        buf.writeBoolean(structureId != null);
        if (structureId != null) buf.writeIdentifier(structureId);
        buf.writeBoolean(advancementId != null);
        if (advancementId != null) {
            buf.writeIdentifier(advancementId);
            buf.writeBoolean(advancementHasDisplay);
            buf.writeBoolean(advancementTab != null);
            if (advancementTab != null) buf.writeIdentifier(advancementTab);
        }
        buf.writeVarInt(children.size());
        for (RequirementNode child : children) child.write(buf);
    }

    public static RequirementNode read(PacketByteBuf buf) {
        return read(buf, 0);
    }

    private static RequirementNode read(PacketByteBuf buf, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Requirement tree is too deep");
        Text label = buf.readText();
        boolean checked = buf.readBoolean();
        boolean logicalGroup = buf.readBoolean();
        Text tooltipDescription = buf.readBoolean() ? buf.readText() : null;
        Long questId = buf.readBoolean() ? buf.readLong() : null;
        Identifier structureId = buf.readBoolean() ? buf.readIdentifier() : null;
        Identifier advancementId = buf.readBoolean() ? buf.readIdentifier() : null;
        boolean hasDisplay = advancementId != null && buf.readBoolean();
        Identifier tab = advancementId != null && buf.readBoolean() ? buf.readIdentifier() : null;
        int count = buf.readVarInt();
        if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid requirement child count: " + count);
        List<RequirementNode> children = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) children.add(read(buf, depth + 1));
        return new RequirementNode(label, checked, logicalGroup, children, tooltipDescription, questId, structureId, advancementId, hasDisplay, tab);
    }
}
