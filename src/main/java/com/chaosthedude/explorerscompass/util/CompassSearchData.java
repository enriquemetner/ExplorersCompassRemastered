package com.chaosthedude.explorerscompass.util;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

/** Search snapshots contain target fields only, never ownership, energy, or custom item data. */
public final class CompassSearchData {
    public static final String BACKUP = "CompassSearchBackup";
    public static final String TOKEN = "CompassSearchToken";
    public static final String FOUND_START_CHUNK = "FoundStartChunk";
    private static final List<String> KEYS = List.of("State", "StructureID", "CreativeBypass",
            CompassTransfer.INHERITED, "FoundX", "FoundZ", "FoundDimension", FOUND_START_CHUNK, "PrevPos", "IsSet",
            "IsListed", "DisplayCoordinates", "DisplayDistance", "SearchRadius", "Samples");
    private CompassSearchData() {}
    public static NbtCompound snapshot(NbtCompound data) {
        NbtCompound result = new NbtCompound();
        for (String key : KEYS) if (data.contains(key)) result.put(key, data.get(key).copy());
        return result;
    }
    public static void clear(NbtCompound data) {
        KEYS.forEach(data::remove);
        data.remove("IsGroup"); // Legacy search state saved before the structure set rename.
        data.remove(BACKUP);
        data.remove(TOKEN);
    }
    public static void restore(NbtCompound data, NbtCompound snapshot) {
        clear(data);
        for (String key : KEYS) if (snapshot.contains(key)) data.put(key, snapshot.get(key).copy());
    }
    public static void begin(NbtCompound data, UUID token, NbtCompound snapshot) {
        data.putUuid(TOKEN, token);
        data.put(BACKUP, snapshot.copy());
    }
    public static boolean restore(NbtCompound data, UUID token) {
        if (!data.containsUuid(TOKEN) || !token.equals(data.getUuid(TOKEN))) return false;
        restore(data, data.getCompound(BACKUP).copy());
        return true;
    }
    public static NbtCompound pointingData(NbtCompound data) {
        return data != null && data.contains(BACKUP) ? data.getCompound(BACKUP) : data;
    }
}
