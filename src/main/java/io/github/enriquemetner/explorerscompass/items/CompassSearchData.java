package io.github.enriquemetner.explorerscompass.items;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Compass target NBT, including the saved target used during an active search. */
public final class CompassSearchData {
    public static final String BACKUP = "CompassSearchBackup";
    public static final String TOKEN = "CompassSearchToken";
    public static final String FOUND_START_CHUNK = "FoundStartChunk";
    static final String STATE = "State";
    static final String STRUCTURE_ID = "StructureID";
    static final String CREATIVE_BYPASS = "CreativeBypass";
    static final String FOUND_X = "FoundX";
    static final String FOUND_Z = "FoundZ";
    static final String FOUND_DIMENSION = "FoundDimension";
    static final String DISPLAY_COORDINATES = "DisplayCoordinates";
    static final String DISPLAY_DISTANCE = "DisplayDistance";
    static final String SEARCH_RADIUS = "SearchRadius";
    static final String SAMPLES = "Samples";
    private static final List<String> KEYS = List.of(STATE, STRUCTURE_ID, CREATIVE_BYPASS,
            CompassTransfer.INHERITED, FOUND_X, FOUND_Z, FOUND_DIMENSION, FOUND_START_CHUNK, "PrevPos", "IsSet",
            "IsListed", DISPLAY_COORDINATES, DISPLAY_DISTANCE, SEARCH_RADIUS, SAMPLES);

    private CompassSearchData() {}

    static void writeSearching(NbtCompound data, Identifier structure) {
        data.remove(CompassTransfer.INHERITED);
        data.putString(STRUCTURE_ID, structure.toString());
        data.putInt(STATE, CompassState.SEARCHING.getID());
        data.putInt(SEARCH_RADIUS, 0);
        data.putInt(SAMPLES, 0);
    }

    static void writeFound(NbtCompound data, Identifier structure, int x, int z, int samples) {
        data.putString(STRUCTURE_ID, structure.toString());
        data.putInt(STATE, CompassState.FOUND.getID());
        data.putInt(FOUND_X, x);
        data.putInt(FOUND_Z, z);
        data.putInt(SAMPLES, samples);
    }

    static void commitFound(NbtCompound data, Identifier structure, int x, int z, int samples, Identifier dimension,
            long startChunk, boolean creativeBypass, boolean displayCoordinates, boolean displayDistance) {
        clear(data);
        writeFound(data, structure, x, z, samples);
        if (creativeBypass) data.putBoolean(CREATIVE_BYPASS, true);
        data.putString(FOUND_DIMENSION, dimension.toString());
        data.putLong(FOUND_START_CHUNK, startChunk);
        data.putBoolean(DISPLAY_COORDINATES, displayCoordinates);
        data.putBoolean(DISPLAY_DISTANCE, displayDistance);
    }

    static void writeNotFound(NbtCompound data, int radius, int samples) {
        data.putInt(STATE, CompassState.NOT_FOUND.getID());
        data.putInt(SEARCH_RADIUS, radius);
        data.putInt(SAMPLES, samples);
    }

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
