package io.github.enriquemetner.explorerscompass.items;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.availability.AvailabilitySelection;
import io.github.enriquemetner.explorerscompass.config.ExplorersCompassConfig;
import io.github.enriquemetner.explorerscompass.network.search.SearchResultPacket;
import io.github.enriquemetner.explorerscompass.workers.SearchWorker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Tracks the live search and worker associated with each player's compass. */
final class CompassSearchSessions {
    private static final AtomicLong SEARCH_IDS = new AtomicLong();
    private final Map<UUID, Session> active = new HashMap<>();

    Session begin(UUID owner, ServerWorld world, ItemStack stack, int requestId,
            List<Identifier> eligibleIds, boolean creativeBypass, String target) {
        var data = stack.getOrCreateNbt();
        var session = new Session(owner, world, stack, CompassSearchData.snapshot(data), requestId, eligibleIds, creativeBypass);
        CompassSearchData.begin(data, session.token, session.previousNbt);
        active.put(owner, session);
        ExplorersCompass.LOGGER.info("Search {} started for {}", session.logId, target);
        return session;
    }

    Session get(PlayerEntity player) {
        return active.get(player.getUuid());
    }

    Session forStack(PlayerEntity player, ItemStack stack) {
        Session session = get(player);
        return session != null && session.stack == stack ? session : null;
    }

    boolean isActive(Session session) {
        return active.get(session.owner) == session;
    }

    void recoverTransferredSearch(PlayerEntity holder, ItemStack stack, NbtCompound data) {
        if (!data.containsUuid(CompassSearchData.TOKEN)) return;
        UUID token = data.getUuid(CompassSearchData.TOKEN);
        Session session = forStack(holder, stack);
        if (session != null && session.token.equals(token)) return;

        // Stop the old owner's search before the item records a new holder or imports unlocks.
        for (Session old : List.copyOf(active.values())) {
            if (old.token.equals(token)) rejectDetached(old);
        }
        CompassSearchData.restore(data, token);
    }

    void tickOwnership(MinecraftServer server) {
        for (Session session : List.copyOf(active.values())) {
            var player = server.getPlayerManager().getPlayer(session.owner);
            if (player == null || !session.isHeldBy(player)) rejectDetached(session);
        }
    }

    void stopAll() {
        for (Session session : List.copyOf(active.values())) cancel(session);
    }

    void cancel(PlayerEntity player, int requestId) {
        Session session = get(player);
        if (session != null && (requestId == -1 || session.requestId == requestId)) cancel(session);
    }

    void abort(PlayerEntity player, SearchWorker worker, RuntimeException error) {
        Session session = get(player);
        if (worker != null && session != null && session.worker == worker) abort(session, error);
    }

    void abort(Session session, RuntimeException error) {
        int radius = session.currentRadius();
        if (rollback(session)) {
            ExplorersCompass.LOGGER.error("Search {} error at radius {} blocks after {} ms",
                    session.logId, radius, session.elapsedMillis(), error);
            sendRejected(session);
        }
    }

    void rejectDetached(Session session) {
        if (cancel(session)) sendRejected(session);
    }

    private boolean cancel(Session session) {
        int radius = session.currentRadius();
        if (!rollback(session)) return false;
        ExplorersCompass.LOGGER.info("Search {} cancelled at radius {} blocks after {} ms",
                session.logId, radius, session.elapsedMillis());
        return true;
    }

    private void sendRejected(Session session) {
        var player = session.world.getServer().getPlayerManager().getPlayer(session.owner);
        if (player != null) SearchResultPacket.send(player, session.requestId, SearchResultPacket.REJECTED, 0);
    }

    private boolean rollback(Session session) {
        if (!detach(session)) return false;
        if (session.stack.hasNbt()) CompassSearchData.restore(session.stack.getNbt(), session.token);
        return true;
    }

    boolean detach(Session session) {
        if (!active.remove(session.owner, session)) return false;
        if (session.worker != null) {
            session.worker.stop();
            session.worker = null;
        }
        return true;
    }

    static final class Session {
        final UUID owner;
        final ItemStack stack;
        final NbtCompound previousNbt;
        final UUID token = UUID.randomUUID();
        final int requestId;
        final ServerWorld world;
        final List<Identifier> eligibleIds;
        final boolean creativeBypass;
        final int radiusLimit = ExplorersCompassConfig.maxSearchRadius;
        SearchWorker worker;
        AvailabilitySelection availability;
        boolean reachedRadiusLimit;
        private final long logId = SEARCH_IDS.updateAndGet(id -> id == 9999 ? 1 : id + 1);
        private final long startedNanos = System.nanoTime();

        private Session(UUID owner, ServerWorld world, ItemStack stack, NbtCompound previousNbt, int requestId,
                List<Identifier> eligibleIds, boolean creativeBypass) {
            this.owner = owner;
            this.world = world;
            this.stack = stack;
            this.previousNbt = previousNbt;
            this.requestId = requestId;
            this.eligibleIds = List.copyOf(eligibleIds);
            this.creativeBypass = creativeBypass;
        }

        boolean isHeldBy(PlayerEntity player) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                if (inventory.getStack(i) == stack && !stack.isEmpty()) return true;
            }
            return false;
        }

        private int currentRadius() {
            return worker == null ? 0 : worker.getRadius();
        }

        private long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        }

        void logFound(Identifier structure, int radius) {
            ExplorersCompass.LOGGER.info("Search {} located {} at radius {} blocks in {} ms",
                    logId, structure, radius, elapsedMillis());
        }

        void logNotFound() {
            if (reachedRadiusLimit) {
                ExplorersCompass.LOGGER.info("Search {} not found within {} blocks after {} ms", logId, radiusLimit, elapsedMillis());
            } else {
                ExplorersCompass.LOGGER.info("Search {} not found after {} ms", logId, elapsedMillis());
            }
        }
    }
}
