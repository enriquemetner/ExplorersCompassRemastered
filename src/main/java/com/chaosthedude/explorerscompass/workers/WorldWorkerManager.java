package com.chaosthedude.explorerscompass.workers;

import java.util.IdentityHashMap;
import java.util.Map;

import com.chaosthedude.explorerscompass.ExplorersCompass;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;

/** All access, including worker execution, stays on the owning server thread. */
public final class WorldWorkerManager {

    private static final Map<MinecraftServer, SearchTaskQueue> queues = new IdentityHashMap<>();

    private WorldWorkerManager() {}

    public static void addWorker(MinecraftServer server, IWorker worker) {
        if (!server.isRunning() || server.isStopped()) return;
        queues.computeIfAbsent(server, owner -> new SearchTaskQueue(
                // execute() can run inline on the server thread. send() always queues the slice,
                // including while the integrated server has paused normal world ticks.
                task -> owner.send(new ServerTask(owner.getTicks(), task)),
                () -> ExplorersCompass.EXPLORERS_COMPASS_ITEM.tickSearchOwnership(owner),
                System::nanoTime)).add(worker);
    }

    public static void clear(MinecraftServer server) {
        SearchTaskQueue queue = queues.remove(server);
        if (queue != null) queue.close();
    }

    public interface IWorker {
        boolean hasWork();

        boolean doWork();
    }
}
