package io.github.enriquemetner.explorerscompass.workers;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;

/** Keeps the existing manager API for callers that advance through workers. */
public class SearchWorkerManager {
    private SearchWorker worker;
    private boolean popped;

    public void createWorkers(ServerWorld world, PlayerEntity player, ItemStack stack,
            List<Structure> structures, List<Structure> savedStructures, boolean excludeVisited,
            boolean creativeBypass, BlockPos startPos) {
        worker = null;
        popped = false;
        worker = SearchWorker.create(world, player, stack, structures, savedStructures,
                excludeVisited, creativeBypass, startPos);
    }

    public void setRadiusLimit(int radiusLimit) {
        if (worker != null) worker.setRadiusLimit(radiusLimit);
        popped = false;
    }

    public boolean start() {
        if (worker == null || popped) return false;
        if (!worker.hasWork()) {
            popped = true;
            return false;
        }
        worker.start();
        return true;
    }

    public void pop() {
        popped = true;
    }

    public boolean ownsWorker(SearchWorker worker) {
        return worker != null && this.worker == worker;
    }

    public int currentRadius() {
        return worker == null ? 0 : worker.getRadius();
    }

    public void stop() {
        if (worker != null) worker.stop();
    }

    public void clear() {
        worker = null;
        popped = false;
    }
}
