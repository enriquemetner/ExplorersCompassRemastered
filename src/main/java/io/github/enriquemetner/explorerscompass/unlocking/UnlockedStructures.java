package io.github.enriquemetner.explorerscompass.unlocking;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

/** Structure types permanently earned by each player in this world. */
public final class UnlockedStructures extends PersistentState {
    private final Map<UUID, Set<Identifier>> unlockedStructures = new HashMap<>();
    private final Set<UUID> notificationsEnabled = new HashSet<>();
    private final Map<UUID, Set<Identifier>> pendingNotifications = new HashMap<>();
    record Relock(Set<Identifier> direct, Set<Identifier> dependent) {
        static final Relock EMPTY = new Relock(Set.of(), Set.of());
    }

    private final Map<UUID, Relock> pendingRelock = new HashMap<>();
    private UnlockDefinitions definitions;
    private final Map<UUID, Set<Identifier>> latestUnlockedStructures = new HashMap<>();
    private final Map<UUID, Set<Identifier>> unlockEventBaselines = new HashMap<>();

    public Set<Identifier> latestUnlocked(UUID player) {
        Set<Identifier> latest = new HashSet<>(latestUnlockedStructures.getOrDefault(player, Set.of()));
        latest.retainAll(getStructures(player));
        return Set.copyOf(latest);
    }
    public void beginUnlockEvent(UUID player, Set<Identifier> before) { unlockEventBaselines.put(player, before); }
    public void finishUnlockEvent(UUID player) { unlockEventBaselines.remove(player); }

    public static UnlockedStructures get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                UnlockedStructures::fromNbt, UnlockedStructures::new, "explorerscompass_unlocked_structures");
    }

    public boolean contains(UUID player, Identifier structure) {
        return unlockedStructures.getOrDefault(player, Set.of()).contains(structure);
    }

    public Set<Identifier> getStructures(UUID player) {
        return Set.copyOf(unlockedStructures.getOrDefault(player, Set.of()));
    }

    UnlockDefinitions definitions() { return definitions; }

    void saveDefinitions(UnlockDefinitions current) {
        if (!current.equals(definitions)) {
            definitions = current;
            markDirty();
        }
    }

    Set<String> savedStructureIds() {
        Set<Identifier> ids = new HashSet<>();
        unlockedStructures.values().forEach(ids::addAll);
        pendingRelock.values().forEach(pending -> ids.addAll(pending.dependent()));
        return UnlockDefinitions.ids(ids);
    }

    void relock(Set<Identifier> changed, Map<Identifier, UnlockPrerequisites.Gate> gates) {
        if (changed.isEmpty()) return;
        Map<Identifier, Set<Identifier>> dependents = new HashMap<>();
        gates.forEach((target, gate) -> gate.required().keySet().forEach(dependency ->
                dependents.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(target)));

        Set<UUID> players = new HashSet<>(unlockedStructures.keySet());
        players.addAll(pendingRelock.keySet());
        for (UUID player : players) {
            Set<Identifier> saved = unlockedStructures.getOrDefault(player, Set.of());
            Relock previous = pendingRelock.getOrDefault(player, Relock.EMPTY);
            Set<Identifier> removed = new HashSet<>();
            Set<Identifier> direct = new HashSet<>(previous.direct());
            Set<Identifier> dependent = new HashSet<>(previous.dependent());
            Set<Identifier> smaller = saved.size() < changed.size() ? saved : changed;
            Set<Identifier> larger = smaller == saved ? changed : saved;
            for (Identifier id : smaller) if (larger.contains(id)) removed.add(id);
            direct.addAll(removed);
            var previouslyDependent = dependent.iterator();
            while (previouslyDependent.hasNext()) {
                Identifier id = previouslyDependent.next();
                if (changed.contains(id)) {
                    previouslyDependent.remove();
                    direct.add(id);
                }
            }

            var queue = new ArrayDeque<>(removed);
            while (!queue.isEmpty()) {
                Identifier dependency = queue.removeFirst();
                for (Identifier id : dependents.getOrDefault(dependency, Set.of())) {
                    var gate = gates.get(id);
                    if (saved.contains(id) && !removed.contains(id)
                            && gate.satisfied(saved::contains)
                            && !gate.satisfied(required -> saved.contains(required) && !removed.contains(required))) {
                        if (!direct.contains(id)) dependent.add(id);
                        removed.add(id);
                        queue.addLast(id);
                    }
                }
            }

            if (!removed.isEmpty()) remove(player, removed);
            Relock pending = new Relock(Set.copyOf(direct), Set.copyOf(dependent));
            if (!pending.equals(previous)) {
                pendingRelock.put(player, pending);
                markDirty();
            }
        }
    }

    boolean hasPendingRelock(UUID player) { return pendingRelock.containsKey(player); }

    Relock takePendingRelock(UUID player) {
        Relock pending = pendingRelock.remove(player);
        if (pending == null) return Relock.EMPTY;
        markDirty();
        return pending;
    }

    public void clearStructures(UUID player) {
        if (unlockedStructures.remove(player) != null) {
            PartySharing.changed();
            markDirty();
        }
        if (pendingNotifications.remove(player) != null) markDirty();
    }

    public boolean notificationsEnabled(UUID player) {
        return notificationsEnabled.contains(player);
    }

    /** Returns false after the first compass, including after advancement revocation. */
    public boolean enableNotifications(UUID player) {
        if (!notificationsEnabled.add(player)) return false;
        pendingNotifications.remove(player);
        markDirty();
        return true;
    }

    public void queueNotification(UUID player, Set<Identifier> added) {
        if (added.isEmpty()) return;
        if (pendingNotifications.computeIfAbsent(player, ignored -> new HashSet<>()).addAll(added)) markDirty();
    }

    /** Consume queued IDs, counting only structure types that remain unlocked. */
    public int takePendingNotificationCount(UUID player) {
        Set<Identifier> pending = pendingNotifications.remove(player);
        if (pending == null) return 0;
        markDirty();
        pending.retainAll(unlockedStructures.getOrDefault(player, Set.of()));
        return pending.size();
    }

    public int add(UUID player, Set<Identifier> structures) {
        Relock pending = pendingRelock.get(player);
        if (pending != null && !structures.isEmpty()) {
            // Keep these unlocks out of the saved and shared sets until join reconciliation.
            structures = new HashSet<>(structures);
            structures.removeAll(pending.direct());
            structures.removeAll(pending.dependent());
        }
        if (structures.isEmpty()) return 0;
        Set<Identifier> saved = unlockedStructures.computeIfAbsent(player, ignored -> new HashSet<>());
        int before = saved.size();
        Set<Identifier> newlyUnlocked = new HashSet<>(structures);
        newlyUnlocked.removeAll(saved);
        newlyUnlocked.removeAll(unlockEventBaselines.getOrDefault(player, Set.of()));
        if (!newlyUnlocked.isEmpty()) latestUnlockedStructures.put(player, Set.copyOf(newlyUnlocked));
        saved.addAll(structures);
        if (saved.size() != before) {
            PartySharing.changed();
            markDirty();
        }
        return saved.size() - before;
    }

    public int remove(UUID player, Set<Identifier> structures) {
        Set<Identifier> pending = pendingNotifications.get(player);
        if (pending != null && pending.removeAll(structures)) {
            if (pending.isEmpty()) pendingNotifications.remove(player);
            markDirty();
        }
        Set<Identifier> saved = unlockedStructures.get(player);
        if (saved == null) return 0;
        int before = saved.size();
        saved.removeAll(structures);
        int removed = before - saved.size();
        if (saved.isEmpty()) unlockedStructures.remove(player);
        if (removed > 0) {
            PartySharing.changed();
            markDirty();
        }
        return removed;
    }

    public static UnlockedStructures fromNbt(NbtCompound nbt) {
        UnlockedStructures state = new UnlockedStructures();
        if (nbt.getInt("UnlockDefinitionsVersion") == UnlockDefinitions.VERSION
                && nbt.contains("UnlockDefinitions", NbtElement.BYTE_ARRAY_TYPE)) {
            state.definitions = UnlockDefinitions.deserialize(
                    new String(nbt.getByteArray("UnlockDefinitions"), StandardCharsets.UTF_8));
        }
        NbtList players = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound player = players.getCompound(i);
            if (!player.containsUuid("UUID")) continue;
            UUID uuid = player.getUuid("UUID");
            Set<Identifier> structures = readIds(player, "UnlockedStructures");
            if (!structures.isEmpty()) state.unlockedStructures.put(uuid, new HashSet<>(structures));
            Set<Identifier> latest = readIds(player, "LatestUnlockedStructures");
            if (!latest.isEmpty()) state.latestUnlockedStructures.put(uuid, latest);
            if (player.getBoolean("NotificationsEnabled")) state.notificationsEnabled.add(uuid);
            Set<Identifier> direct = readIds(player, "PendingDirectRelock");
            Set<Identifier> dependent = readIds(player, "PendingDependentRelock");
            if (!direct.isEmpty() || !dependent.isEmpty()) state.pendingRelock.put(uuid, new Relock(direct, dependent));
            Set<Identifier> pending = readIds(player, "PendingNotifications");
            if (!pending.isEmpty()) state.pendingNotifications.put(uuid, new HashSet<>(pending));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList players = new NbtList();
        Set<UUID> uuids = new HashSet<>(unlockedStructures.keySet());
        uuids.addAll(notificationsEnabled);
        uuids.addAll(pendingNotifications.keySet());
        uuids.addAll(pendingRelock.keySet());
        uuids.addAll(latestUnlockedStructures.keySet());
        uuids.forEach(uuid -> {
            NbtCompound player = new NbtCompound();
            player.putUuid("UUID", uuid);
            writeIds(player, "UnlockedStructures", unlockedStructures.getOrDefault(uuid, Set.of()));
            writeIds(player, "LatestUnlockedStructures", latestUnlockedStructures.getOrDefault(uuid, Set.of()));
            player.putBoolean("NotificationsEnabled", notificationsEnabled.contains(uuid));
            Relock pending = pendingRelock.get(uuid);
            if (pending != null) {
                writeIds(player, "PendingDirectRelock", pending.direct());
                writeIds(player, "PendingDependentRelock", pending.dependent());
            }
            writeIds(player, "PendingNotifications", pendingNotifications.getOrDefault(uuid, Set.of()));
            players.add(player);
        });
        nbt.put("Players", players);
        if (definitions != null) {
            nbt.putInt("UnlockDefinitionsVersion", UnlockDefinitions.VERSION);
            nbt.putByteArray("UnlockDefinitions", definitions.serialize().getBytes(StandardCharsets.UTF_8));
        }
        return nbt;
    }
    private static Set<Identifier> readIds(NbtCompound player, String key) {
        Set<Identifier> ids = new HashSet<>();
        NbtList list = player.getList(key, NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getString(i));
            if (id != null) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static void writeIds(NbtCompound player, String key, Set<Identifier> ids) {
        if (ids.isEmpty()) return;
        NbtList list = new NbtList();
        ids.stream().sorted().forEach(id -> list.add(NbtString.of(id.toString())));
        player.put(key, list);
    }
}
