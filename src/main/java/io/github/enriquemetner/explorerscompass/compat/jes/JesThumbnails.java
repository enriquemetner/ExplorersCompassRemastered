package io.github.enriquemetner.explorerscompass.compat.jes;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class JesThumbnails {
    public static final int SIZE = 40;
    private static JesThumbnails activeQueue;
    private final Object queue;
    private final Method tick, draw, supported, catalog, forgetCatalog, has, entryId, close, startOn;
    private final Field permanentlyFailed, waiting, viewport;
    private final Map<?, ?> cachedTextures;
    private final Set<?> pendingTextures;
    private final Class<? extends Screen> browser;
    private boolean failed;
    private boolean catalogRequested;
    private int preloadSize;
    private final Set<Identifier> preloaded = new HashSet<>();
    private CompletableFuture<?> loadedCatalog;
    private Set<Identifier> previewable = Set.of();

    private JesThumbnails() throws ReflectiveOperationException {
        String root = "com.finndog.justenoughstructures.client.";
        var queueType = Class.forName(root + "screen.ThumbnailQueue");
        var constructor = queueType.getDeclaredConstructor();
        constructor.setAccessible(true);
        queue = constructor.newInstance();
        tick = queueType.getDeclaredMethod("tick", List.class, boolean.class);
        tick.setAccessible(true);
        waiting = queueType.getDeclaredField("waiting");
        waiting.setAccessible(true);
        viewport = queueType.getDeclaredField("viewport");
        viewport.setAccessible(true);
        close = queueType.getDeclaredMethod("close");
        close.setAccessible(true);
        var thumbnails = Class.forName(root + "Thumbnails");
        permanentlyFailed = thumbnails.getDeclaredField("FAILED");
        permanentlyFailed.setAccessible(true);
        Field cache = thumbnails.getDeclaredField("CACHE");
        cache.setAccessible(true);
        cachedTextures = (Map<?, ?>) cache.get(null);
        Field pending = thumbnails.getDeclaredField("PENDING");
        pending.setAccessible(true);
        pendingTextures = (Set<?>) pending.get(null);
        has = thumbnails.getMethod("has", Identifier.class);
        draw = thumbnails.getMethod("draw", DrawContext.class, Identifier.class,
                int.class, int.class, int.class);
        var requests = Class.forName(root + "ClientRequests");
        supported = requests.getMethod("serverSupported");
        catalog = requests.getMethod("catalog");
        forgetCatalog = requests.getMethod("forgetCatalog");
        entryId = Class.forName("com.finndog.justenoughstructures.catalog.StructureCatalog$Entry").getMethod("id");
        browser = Class.forName(root + "screen.JesScreen").asSubclass(Screen.class);
        startOn = browser.getMethod("startOn", Identifier.class);
    }

    public static JesThumbnails create() {
        if (!FabricLoader.getInstance().isModLoaded("justenoughstructures")) return null;
        try {
            return new JesThumbnails();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            ExplorersCompass.LOGGER.warn("Could not initialize JES thumbnails", e);
            return null;
        }
    }

    public boolean available() {
        return !failed && Boolean.TRUE.equals(invoke(supported, null));
    }

    public void prepare(int count, int first, int end, IntFunction<Identifier> rowId) {
        if (count == 0 && !queueBusy()) return;
        if (!catalogRequested) {
            // A fresh catalog request registers this player as a browser on the server.
            // A cached catalog alone can leave new captures cancelled as superseded.
            invoke(forgetCatalog, null);
            catalogRequested = true;
        }
        if (!(invoke(catalog, null) instanceof CompletableFuture<?> entries)
                || !entries.isDone() || entries.isCompletedExceptionally()) return;
        if (loadedCatalog != entries) {
            Set<Identifier> ids = new HashSet<>();
            for (Object entry : (List<?>) entries.join()) {
                if (invoke(entryId, entry) instanceof Identifier id) ids.add(id);
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
        List<Identifier> candidates = queueBusy() ? List.of() : candidates(count, first, end, rowId);
        activeQueue = this;
        try {
            invoke(tick, queue, candidates, false);
        } finally {
            activeQueue = null;
        }
    }

    private boolean queueBusy() {
        try {
            return waiting.getBoolean(queue) || viewport.get(queue) != null;
        } catch (IllegalAccessException e) {
            disable(e);
            return false;
        }
    }

    private List<Identifier> candidates(int count, int first, int end, IntFunction<Identifier> rowId) {
        List<Identifier> missing = new ArrayList<>();
        List<Identifier> available = new ArrayList<>();
        for (int i = first; i < end; i++) {
            Identifier id = rowId.apply(i);
            if (!previewable.contains(id)) continue;
            if (Boolean.TRUE.equals(invoke(has, null, id))) available.add(id);
            else missing.add(id);
        }
        missing.addAll(available);
        for (int above = first - 1, below = end; above >= 0 || below < count; above--, below++) {
            if (above >= 0) addPreloadCandidate(missing, rowId.apply(above));
            if (below < count) addPreloadCandidate(missing, rowId.apply(below));
        }
        return missing;
    }

    private void addPreloadCandidate(List<Identifier> candidates, Identifier id) {
        if (previewable.contains(id) && !preloaded.contains(id)) candidates.add(id);
    }

    public static int renderSizeOverride() {
        return activeQueue == null ? 0 : 2 * displaySize();
    }

    private static int displaySize() {
        return (int) Math.ceil(SIZE * MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    public static boolean needsLargerThumbnail(Identifier id) {
        if (activeQueue == null || activeQueue.pendingTextures.contains(id)
                || !(activeQueue.cachedTextures.get(id) instanceof NativeImageBackedTexture texture)) return false;
        var image = texture.getImage();
        int size = displaySize();
        boolean undersized = image != null && (image.getWidth() < size || image.getHeight() < size);
        if (image != null && !undersized) activeQueue.preloaded.add(id);
        return undersized;
    }

    public boolean draw(DrawContext context, Identifier id, int x, int y) {
        return Boolean.TRUE.equals(invoke(draw, null, context, id, x, y, SIZE));
    }

    public boolean canPreview(Identifier id) {
        return loadedCatalog == null || previewable.contains(id);
    }

    public boolean thumbnailUnavailable(Identifier id) {
        if (!canPreview(id) || failed) return true;
        try {
            return ((Set<?>) permanentlyFailed.get(null)).contains(id);
        } catch (IllegalAccessException e) {
            disable(e);
            return true;
        }
    }

    public boolean open(Screen parent, Identifier id) {
        try {
            Screen screen = browser.getConstructor(Screen.class).newInstance(parent);
            startOn.invoke(null, id);
            MinecraftClient.getInstance().setScreen(screen);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            disable(e);
            return false;
        }
    }

    public void close() {
        invoke(close, queue);
    }

    private Object invoke(Method method, Object receiver, Object... args) {
        if (failed) return null;
        try {
            return method.invoke(receiver, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            disable(e);
            return null;
        }
    }

    private void disable(Throwable error) {
        failed = true;
        ExplorersCompass.LOGGER.warn("Could not use JES thumbnails", error);
    }
}
