package io.github.enriquemetner.explorerscompass.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

/** Disk coverage only. Live and pending chunk data take precedence over these records. */
public final class SavedStructureIndex extends PersistentState {
    private record Region(String revision, Map<Integer, List<Identifier>> chunks) {}
    private final Map<Long, Region> regions = new HashMap<>();

    public static SavedStructureIndex get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(SavedStructureIndex::read,
                SavedStructureIndex::new, "explorerscompass_saved_starts_v1");
    }

    public synchronized List<Identifier> lookup(ChunkPos pos, String revision) {
        var region = regions.get(regionKey(pos));
        return region != null && region.revision.equals(revision) ? region.chunks.get(slot(pos)) : null;
    }

    public synchronized void remember(ChunkPos pos, String revision, List<Identifier> starts) {
        long key = regionKey(pos);
        var region = regions.get(key);
        if (region == null || !region.revision.equals(revision)) {
            region = new Region(revision, new HashMap<>());
            regions.put(key, region);
        }
        region.chunks.put(slot(pos), List.copyOf(starts));
        markDirty();
    }

    /** Called on the storage executor after the actual write, including coalesced saves. */
    public synchronized void written(ChunkPos pos, String before, String after, List<Identifier> starts) {
        long key = regionKey(pos);
        var previous = regions.get(key);
        if (after == null || after.isEmpty()) {
            if (regions.remove(key) != null) markDirty();
            return;
        }
        Map<Integer, List<Identifier>> chunks = previous != null && previous.revision.equals(before)
                ? previous.chunks : new HashMap<>();
        if (starts == null) chunks.remove(slot(pos));
        else chunks.put(slot(pos), List.copyOf(starts));
        regions.put(key, new Region(after, chunks));
        markDirty();
    }

    public synchronized void rebase(ChunkPos pos, String before, String after) {
        long key = regionKey(pos);
        var previous = regions.get(key);
        if (before != null && after != null && !after.isEmpty() && !after.equals(before)
                && previous != null && previous.revision.equals(before)) {
            regions.put(key, new Region(after, previous.chunks));
            markDirty();
        }
    }

    public static String revision(Path path) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return "";
        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return attrs.lastModifiedTime() + ":" + attrs.size() + ":" + attrs.fileKey();
    }

    public static String tryRevision(Path path) {
        try { return revision(path); }
        catch (IOException | RuntimeException e) { return null; }
    }

    private static long regionKey(ChunkPos pos) { return ChunkPos.toLong(pos.getRegionX(), pos.getRegionZ()); }
    private static int slot(ChunkPos pos) { return pos.getRegionRelativeX() + (pos.getRegionRelativeZ() << 5); }

    private static SavedStructureIndex read(NbtCompound nbt) {
        var result = new SavedStructureIndex();
        for (var entry : nbt.getList("Regions", 10)) {
            var region = (NbtCompound) entry;
            Map<Integer, List<Identifier>> chunks = new HashMap<>();
            for (var encoded : region.getList("Chunks", 10)) {
                var chunk = (NbtCompound) encoded;
                chunks.put(chunk.getInt("Slot"), chunk.getList("Starts", 8).stream()
                        .map(id -> Identifier.tryParse(id.asString())).filter(java.util.Objects::nonNull).toList());
            }
            result.regions.put(region.getLong("Region"), new Region(region.getString("Revision"), chunks));
        }
        return result;
    }

    @Override public synchronized void save(java.io.File file) { super.save(file); }

    @Override public synchronized NbtCompound writeNbt(NbtCompound nbt) {
        var list = new NbtList();
        regions.forEach((key, region) -> {
            var tag = new NbtCompound();
            tag.putLong("Region", key);
            tag.putString("Revision", region.revision);
            var chunks = new NbtList();
            region.chunks.forEach((slot, ids) -> {
                var chunk = new NbtCompound();
                chunk.putInt("Slot", slot);
                var starts = new NbtList();
                ids.forEach(id -> starts.add(NbtString.of(id.toString())));
                chunk.put("Starts", starts);
                chunks.add(chunk);
            });
            tag.put("Chunks", chunks);
            list.add(tag);
        });
        nbt.put("Regions", list);
        return nbt;
    }
}
