package com.chaosthedude.explorerscompass.unlocking;

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
    private final Set<UUID> pendingReloadNotice = new HashSet<>();
    private final Set<UUID> pendingRelock = new HashSet<>();
    private String definitionHistory;
    private final Map<UUID, Set<Identifier>> latestUnlockedStructures = new HashMap<>();
    private final Map<UUID, Set<Identifier>> unlockEventBaselines = new HashMap<>();

    public Set<Identifier> latestUnlocked(UUID player) {
        Set<Identifier> latest = new HashSet<>(latestUnlockedStructures.getOrDefault(player, Set.of()));
        latest.retainAll(getStructures(player));
        return Set.copyOf(latest);
    }
    public void beginUnlockEvent(UUID player, Set<Identifier> before) { unlockEventBaselines.put(player, before); }
    public void finishUnlockEvent(UUID player) { unlockEventBaselines.remove(player); }

    private long noticeSequence;
    private final Map<UUID, Long> noticeRevisions = new HashMap<>();
    private final Set<UUID> noticeRelocked = new HashSet<>();
    public long noticeRevision(UUID player) { return pendingReloadNotice.contains(player) ? noticeRevisions.getOrDefault(player, 1L) : 0; }
    public boolean noticeRelocked(UUID player) { return noticeRelocked.contains(player); }
    public void acknowledgeNotice(UUID player, long revision) {
        if (noticeRevision(player) == revision) takePendingReloadNotice(player);
    }

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

    public Set<UUID> knownPlayers() {
        Set<UUID> players = new HashSet<>(unlockedStructures.keySet());
        players.addAll(notificationsEnabled);
        players.addAll(pendingReloadNotice);
        players.addAll(pendingRelock);
        return players;
    }

    public String definitionHistory() { return definitionHistory; }

    public boolean updateDefinitionHistory(String definition) {
        if (definition.equals(definitionHistory)) return false;
        boolean changed = definitionHistory != null;
        definitionHistory = definition;
        markDirty();
        return changed;
    }

    public void queueReload(Set<UUID> players, boolean relock, boolean notice) {
        if (relock) {
            pendingRelock.addAll(players);
            PartySharing.changed();
        }
        if (notice) {
            pendingReloadNotice.addAll(players);
            long revision = ++noticeSequence;
            players.forEach(player -> noticeRevisions.put(player, revision));
            if (relock) noticeRelocked.addAll(players);
        }
        if (relock || notice) markDirty();
    }

    boolean hasPendingRelock(UUID player) {
        return pendingRelock.contains(player);
    }

    public boolean takePendingRelock(UUID player) {
        if (!pendingRelock.remove(player)) return false;
        PartySharing.changed();
        markDirty();
        return true;
    }

    public boolean takePendingReloadNotice(UUID player) {
        if (!pendingReloadNotice.remove(player)) return false;
        noticeRevisions.remove(player);
        noticeRelocked.remove(player);
        markDirty();
        return true;
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
        state.noticeSequence = nbt.getLong("NoticeSequence");
        if (nbt.contains("DefinitionHistory", NbtElement.STRING_TYPE))
            state.definitionHistory = nbt.getString("DefinitionHistory");
        NbtList players = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound player = players.getCompound(i);
            if (!player.containsUuid("UUID")) continue;
            Set<Identifier> structures = new HashSet<>();
            NbtList ids = player.getList("UnlockedStructures", NbtElement.STRING_TYPE);
            for (int j = 0; j < ids.size(); j++) {
                Identifier id = Identifier.tryParse(ids.getString(j));
                if (id != null) structures.add(id);
            }
            Set<Identifier> latest = new HashSet<>();
            NbtList latestIds = player.getList("LatestUnlockedStructures", NbtElement.STRING_TYPE);
            for (int j = 0; j < latestIds.size(); j++) {
                Identifier id = Identifier.tryParse(latestIds.getString(j));
                if (id != null) latest.add(id);
            }
            if (!latest.isEmpty()) state.latestUnlockedStructures.put(player.getUuid("UUID"), latest);
            state.unlockedStructures.put(player.getUuid("UUID"), structures);
            UUID uuid = player.getUuid("UUID");
            if (player.getBoolean("NotificationsEnabled")) state.notificationsEnabled.add(uuid);
            if (player.getBoolean("PendingReloadNotice")) state.pendingReloadNotice.add(uuid);
            state.noticeRevisions.put(uuid, Math.max(1, player.getLong("NoticeRevision")));
            state.noticeSequence = Math.max(state.noticeSequence, state.noticeRevisions.get(uuid));
            if (player.getBoolean("NoticeRelocked")) state.noticeRelocked.add(uuid);
            if (player.getBoolean("PendingRelock")) state.pendingRelock.add(uuid);
            Set<Identifier> pending = new HashSet<>();
            NbtList queued = player.getList("PendingNotifications", NbtElement.STRING_TYPE);
            for (int j = 0; j < queued.size(); j++) {
                Identifier id = Identifier.tryParse(queued.getString(j));
                if (id != null) pending.add(id);
            }
            if (!pending.isEmpty()) state.pendingNotifications.put(uuid, pending);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList players = new NbtList();
        Set<UUID> uuids = new HashSet<>(unlockedStructures.keySet());
        uuids.addAll(notificationsEnabled);
        uuids.addAll(pendingNotifications.keySet());
        uuids.addAll(pendingReloadNotice);
        uuids.addAll(pendingRelock);
        uuids.addAll(latestUnlockedStructures.keySet());
        uuids.forEach(uuid -> {
            NbtCompound player = new NbtCompound();
            player.putUuid("UUID", uuid);
            NbtList ids = new NbtList();
            unlockedStructures.getOrDefault(uuid, Set.of()).stream().sorted().forEach(id -> ids.add(NbtString.of(id.toString())));
            player.put("UnlockedStructures", ids);
            NbtList latest = new NbtList();
            latestUnlockedStructures.getOrDefault(uuid, Set.of()).stream().sorted().forEach(id -> latest.add(NbtString.of(id.toString())));
            player.put("LatestUnlockedStructures", latest);
            player.putBoolean("NotificationsEnabled", notificationsEnabled.contains(uuid));
            player.putBoolean("PendingReloadNotice", pendingReloadNotice.contains(uuid));
            player.putLong("NoticeRevision", noticeRevisions.getOrDefault(uuid, 0L));
            player.putBoolean("NoticeRelocked", noticeRelocked.contains(uuid));
            player.putBoolean("PendingRelock", pendingRelock.contains(uuid));
            NbtList queued = new NbtList();
            pendingNotifications.getOrDefault(uuid, Set.of()).stream().sorted().forEach(id -> queued.add(NbtString.of(id.toString())));
            player.put("PendingNotifications", queued);
            players.add(player);
        });
        nbt.put("Players", players);
        nbt.putLong("NoticeSequence", noticeSequence);
        if (definitionHistory != null) nbt.putString("DefinitionHistory", definitionHistory);
        return nbt;
    }
}
