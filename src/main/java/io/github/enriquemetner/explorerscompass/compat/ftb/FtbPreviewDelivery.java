package io.github.enriquemetner.explorerscompass.compat.ftb;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Holds editor updates that arrive before FTB creates the corresponding quest object. */
final class FtbPreviewDelivery<F> {
    enum Kind { TASK, REWARD }

    private record Key(Kind kind, long objectId) {}

    private final Map<Key, FtbRewardPreview> pending = new HashMap<>();
    private F currentFile;

    void receive(F file, Kind kind, FtbPreviewPacket packet, Predicate<FtbPreviewPacket> deliver) {
        if (file == null) return;
        useFile(file);
        if (!deliver.test(packet)) {
            pending.put(new Key(kind, packet.objectId()), packet.preview());
        }
    }

    FtbRewardPreview take(F file, Kind kind, long objectId) {
        useFile(file);
        return pending.remove(new Key(kind, objectId));
    }

    void clear() {
        pending.clear();
        currentFile = null;
    }

    private void useFile(F file) {
        if (currentFile != file) {
            pending.clear();
            currentFile = file;
        }
    }
}
