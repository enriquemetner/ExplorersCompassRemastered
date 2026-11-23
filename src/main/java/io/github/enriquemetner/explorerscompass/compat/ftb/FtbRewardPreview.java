package io.github.enriquemetner.explorerscompass.compat.ftb;

import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Server-resolved presentation shared by full quest sync and individual reward edits. */
public record FtbRewardPreview(int totalCount, List<Identifier> structures) {
    public static final FtbRewardPreview EMPTY = new FtbRewardPreview(0, List.of());

    public FtbRewardPreview {
        structures = List.copyOf(structures);
        if (totalCount < 0 || structures.size() != totalCount)
            throw new IllegalArgumentException("Invalid FTB structure reward preview size");
    }

    public static FtbRewardPreview of(Set<Identifier> resolved) {
        return new FtbRewardPreview(resolved.size(), resolved.stream()
                .sorted(Comparator.comparing(StructureUtils::formatIdPath).thenComparing(Identifier::toString))
                .toList());
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(totalCount);
        buf.writeVarInt(structures.size());
        structures.forEach(buf::writeIdentifier);
    }

    public static FtbRewardPreview read(PacketByteBuf buf) {
        int total = buf.readVarInt();
        int count = buf.readVarInt();
        if (total < 0 || count != total || count > 65536)
            throw new IllegalArgumentException("Invalid FTB structure reward preview size");
        java.util.ArrayList<Identifier> ids = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readIdentifier());
        return new FtbRewardPreview(total, ids);
    }
}
