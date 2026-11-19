package io.github.enriquemetner.explorerscompass.storage;

import io.github.enriquemetner.explorerscompass.mixins.StructureChunkStorageAccessor;
import io.github.enriquemetner.explorerscompass.util.StructureDestination;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.scanner.NbtScanQuery;
import net.minecraft.nbt.scanner.SelectiveNbtCollector;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

public final class SavedStructureReader {
    private SavedStructureReader() {}

    /** Null means no authoritative result. An empty list is confirmed coverage. */
    public static List<Identifier> starts(ServerWorld world, ChunkPos pos,
            EmptyChunkStorage.RegionSnapshot snapshot, SavedStructureIndex index) {
        var loaded = StructureDestination.existingChunk(world, pos, ChunkStatus.STRUCTURE_STARTS);
        if (loaded != null) {
            List<Identifier> ids = new ArrayList<>();
            loaded.getStructureStarts().forEach((structure, start) -> {
                var id = StructureUtils.getIDForStructure(world, structure);
                if (id != null && start.hasChildren() && pos.equals(start.getPos())) ids.add(id);
            });
            return ids;
        }

        int slot = pos.getRegionRelativeX() + (pos.getRegionRelativeZ() << 5);
        var storage = world.getChunkManager().threadedAnvilChunkStorage;
        var backend = storage.getWorker() instanceof EmptyChunkStorage worker ? worker : null;
        boolean cacheable = snapshot != null && !snapshot.revision().isEmpty() && !snapshot.hasPendingWrite(slot)
                && snapshot.writeStamp() >= 0 && backend != null
                && snapshot.writeStamp() == backend.explorerscompass$writeStamp();
        if (cacheable) {
            var previous = index.lookup(pos, snapshot.revision());
            if (previous != null) return previous;
        }

        var scanner = new SelectiveNbtCollector(new NbtScanQuery(NbtInt.TYPE, "DataVersion"),
                new NbtScanQuery("structures", NbtCompound.TYPE, "starts"));
        storage.getWorker().scanChunk(pos, scanner).join();
        if (!(scanner.getRoot() instanceof NbtCompound data)) return null;
        if (data.getInt("DataVersion") != SharedConstants.getGameVersion().getSaveVersion().getId()) {
            var full = storage.getNbt(pos).join().orElse(null);
            if (full == null) return null;
            data = ((StructureChunkStorageAccessor) storage).explorerscompass$updateChunkNbt(full.copy());
        }
        List<Identifier> ids = readStarts(data.getCompound("structures").getCompound("starts"), pos);
        if (cacheable && snapshot.writeStamp() == backend.explorerscompass$writeStamp()) index.remember(pos, snapshot.revision(), ids);
        return ids;
    }

    public static List<Identifier> readStarts(NbtCompound starts, ChunkPos pos) {
        List<Identifier> ids = new ArrayList<>();
        for (String key : starts.getKeys()) {
            var id = Identifier.tryParse(key);
            var start = starts.getCompound(key);
            if (id != null && id.equals(Identifier.tryParse(start.getString("id")))
                    && start.contains("ChunkX", 3) && start.contains("ChunkZ", 3)
                    && start.getInt("ChunkX") == pos.x && start.getInt("ChunkZ") == pos.z
                    && !start.getList("Children", 10).isEmpty()) ids.add(id);
        }
        return ids;
    }
}
