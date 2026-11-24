package io.github.enriquemetner.explorerscompass.compat.jes;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;

public final class JesThumbnails {
    public static final int SIZE = 40;
    private static JesThumbnails activeQueue;
    private final JesThumbnailBinding binding;
    private boolean catalogRequested;
    private int preloadSize;
    private final Set<Identifier> preloaded = new HashSet<>();
    private CompletableFuture<?> loadedCatalog;
    private Set<Identifier> previewable = Set.of();

    private JesThumbnails(JesThumbnailBinding binding) {
        this.binding = binding;
    }

    public static JesThumbnails create() {
        if (!FabricLoader.getInstance().isModLoaded("justenoughstructures")) return null;
        try {
            return new JesThumbnails(new JesThumbnailBinding());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            ExplorersCompass.LOGGER.warn("Could not initialize JES thumbnails", e);
            return null;
        }
    }

    public boolean available() {
        return binding.supported();
    }

    public void prepare(int count, int first, int end, IntFunction<Identifier> rowId) {
        if (count == 0 && !binding.queueBusy()) return;
        if (!catalogRequested) {
            // A fresh catalog request registers this player as a browser on the server.
            // A cached catalog alone can leave new captures cancelled as superseded.
            binding.forgetCatalog();
            catalogRequested = true;
        }
        var entries = binding.catalog();
        if (entries == null || !entries.isDone() || entries.isCompletedExceptionally()) return;
        if (loadedCatalog != entries) {
            Set<Identifier> ids = new HashSet<>();
            for (Object entry : (List<?>) entries.join()) {
                Identifier id = binding.entryId(entry);
                if (id != null) ids.add(id);
            }
            previewable = ids;
            loadedCatalog = entries;
            preloaded.clear();
        }
        int size = displaySize();
        if (preloadSize != size) {
            preloaded.clear();
            preloadSize = size;
        }
        List<Identifier> candidates = binding.queueBusy() ? List.of()
                : orderCandidates(count, first, end, rowId, previewable, preloaded, binding::has);
        activeQueue = this;
        try {
            binding.tick(candidates);
        } finally {
            activeQueue = null;
        }
    }

    static List<Identifier> orderCandidates(int count, int first, int end, IntFunction<Identifier> rowId,
                                             Set<Identifier> previewable, Set<Identifier> preloaded,
                                             Predicate<Identifier> cached) {
        List<Identifier> missing = new ArrayList<>();
        List<Identifier> available = new ArrayList<>();
        for (int i = first; i < end; i++) {
            Identifier id = rowId.apply(i);
            if (!previewable.contains(id)) continue;
            if (cached.test(id)) available.add(id);
            else missing.add(id);
        }
        missing.addAll(available);
        for (int above = first - 1, below = end; above >= 0 || below < count; above--, below++) {
            if (above >= 0) addPreloadCandidate(missing, rowId.apply(above), previewable, preloaded);
            if (below < count) addPreloadCandidate(missing, rowId.apply(below), previewable, preloaded);
        }
        return missing;
    }

    private static void addPreloadCandidate(List<Identifier> candidates, Identifier id, Set<Identifier> previewable,
                                            Set<Identifier> preloaded) {
        if (previewable.contains(id) && !preloaded.contains(id)) candidates.add(id);
    }

    public static int renderSizeOverride() {
        return activeQueue == null ? 0 : 2 * displaySize();
    }

    private static int displaySize() {
        return (int) Math.ceil(SIZE * MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    public static boolean needsLargerThumbnail(Identifier id) {
        if (activeQueue == null) return false;
        var texture = activeQueue.binding.cachedTexture(id);
        if (texture == null) return false;
        var image = texture.getImage();
        int size = displaySize();
        boolean undersized = image != null && (image.getWidth() < size || image.getHeight() < size);
        if (image != null && !undersized) activeQueue.preloaded.add(id);
        return undersized;
    }

    public boolean draw(DrawContext context, Identifier id, int x, int y) {
        return binding.draw(context, id, x, y, SIZE);
    }

    public boolean canPreview(Identifier id) {
        return loadedCatalog == null || previewable.contains(id);
    }

    public boolean thumbnailUnavailable(Identifier id) {
        return !canPreview(id) || binding.thumbnailUnavailable(id);
    }

    public boolean open(Screen parent, Identifier id) {
        return binding.open(parent, id);
    }

    public void close() {
        binding.close();
    }
}
