package com.chaosthedude.explorerscompass.compat.jes;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    private final Method tick, draw, supported, catalog, entryId, close, startOn;
    private final Field permanentlyFailed;
    private final Map<?, ?> cachedTextures;
    private final Set<?> pendingTextures;
    private final Class<? extends Screen> browser;
    private boolean failed;
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
        draw = thumbnails.getMethod("draw", DrawContext.class, Identifier.class,
                int.class, int.class, int.class);
        var requests = Class.forName(root + "ClientRequests");
        supported = requests.getMethod("serverSupported");
        catalog = requests.getMethod("catalog");
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

    public void prepare(List<Identifier> visible) {
        if (visible.isEmpty()) return;
        if (!(invoke(catalog, null) instanceof CompletableFuture<?> entries)
                || !entries.isDone() || entries.isCompletedExceptionally()) return;
        if (loadedCatalog != entries) {
            Set<Identifier> ids = new HashSet<>();
            for (Object entry : (List<?>) entries.join()) {
                if (invoke(entryId, entry) instanceof Identifier id) ids.add(id);
            }
            previewable = ids;
            loadedCatalog = entries;
        }
        List<Identifier> candidates = visible.stream().filter(previewable::contains).toList();
        if (candidates.isEmpty()) return;
        activeQueue = this;
        try {
            invoke(tick, queue, candidates, false);
        } finally {
            activeQueue = null;
        }
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
        return image != null && (image.getWidth() < size || image.getHeight() < size);
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
