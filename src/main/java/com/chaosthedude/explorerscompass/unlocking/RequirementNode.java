package com.chaosthedude.explorerscompass.unlocking;

import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/** A snapshot of a requirement and its satisfaction when the compass opens. */
public record RequirementNode(Text label, boolean checked, boolean logicalGroup,
                              List<RequirementNode> children, Text tooltipDescription, Long questId) {
    public RequirementNode {
        children = List.copyOf(children);
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
        int count = buf.readVarInt();
        if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid requirement child count: " + count);
        List<RequirementNode> children = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) children.add(read(buf, depth + 1));
        return new RequirementNode(label, checked, logicalGroup, children, tooltipDescription, questId);
    }
}
