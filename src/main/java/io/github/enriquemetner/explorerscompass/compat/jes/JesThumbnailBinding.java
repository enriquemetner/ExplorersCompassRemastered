package io.github.enriquemetner.explorerscompass.compat.jes;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Reflected JES API and queue state for one compass screen. */
final class JesThumbnailBinding {
    private final Object queue;
    private final Method tick, draw, supported, catalog, forgetCatalog, has, entryId, close, startOn;
    private final Field permanentlyFailed, waiting, viewport;
    private final Map<?, ?> cachedTextures;
    private final Set<?> pendingTextures;
    private final Class<? extends Screen> browser;
    private boolean failed;

    JesThumbnailBinding() throws ReflectiveOperationException {
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

    boolean supported() {
        return Boolean.TRUE.equals(invoke(supported, null));
    }

    void forgetCatalog() {
        invoke(forgetCatalog, null);
    }

    CompletableFuture<?> catalog() {
        return invoke(catalog, null) instanceof CompletableFuture<?> future ? future : null;
    }

    Identifier entryId(Object entry) {
        return invoke(entryId, entry) instanceof Identifier id ? id : null;
    }

    boolean has(Identifier id) {
        return Boolean.TRUE.equals(invoke(has, null, id));
    }

    void tick(List<Identifier> candidates) {
        invoke(tick, queue, candidates, false);
    }

    boolean queueBusy() {
        try {
            return waiting.getBoolean(queue) || viewport.get(queue) != null;
        } catch (IllegalAccessException e) {
            disable(e);
            return false;
        }
    }

    NativeImageBackedTexture cachedTexture(Identifier id) {
        return !pendingTextures.contains(id) && cachedTextures.get(id) instanceof NativeImageBackedTexture texture
                ? texture : null;
    }

    boolean draw(DrawContext context, Identifier id, int x, int y, int size) {
        return Boolean.TRUE.equals(invoke(draw, null, context, id, x, y, size));
    }

    boolean thumbnailUnavailable(Identifier id) {
        if (failed) return true;
        try {
            return ((Set<?>) permanentlyFailed.get(null)).contains(id);
        } catch (IllegalAccessException e) {
            disable(e);
            return true;
        }
    }

    boolean open(Screen parent, Identifier id) {
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

    void close() {
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
