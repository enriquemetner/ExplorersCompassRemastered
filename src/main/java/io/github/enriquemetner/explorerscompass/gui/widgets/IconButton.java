package io.github.enriquemetner.explorerscompass.gui.widgets;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;

import java.io.IOException;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class IconButton {
    private static final Identifier CLOSE = new Identifier(ExplorersCompass.MODID, "textures/gui/close.png");
    private static final Size FALLBACK = new Size(7, 7);
    private static volatile Size closeSize = FALLBACK;

    private IconButton() {}

    record Size(int width, int height) {
        int centeredX(int x, int containerWidth) { return x + (containerWidth - width) / 2; }
        int centeredY(int y, int containerHeight) { return y + (containerHeight - height) / 2; }
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(ExplorersCompass.MODID, "close_icon");
            }

            @Override
            public void reload(ResourceManager manager) {
                closeSize = readCloseSize(manager);
            }
        });
    }

    private static Size readCloseSize(ResourceManager manager) {
        try (var stream = manager.open(CLOSE); var image = NativeImage.read(stream)) {
            return new Size(image.getWidth(), image.getHeight());
        } catch (IOException ignored) {
            return FALLBACK;
        }
    }

    public static TransparentButton close(int x, Runnable close) {
        return new TransparentButton(x, 10, 20, 20, Text.translatable("gui.close"), button -> close.run()) {
            @Override
            protected void renderLabel(DrawContext context, TextRenderer renderer) {
                Size size = closeSize;
                drawWithShadow(context, CLOSE, size.centeredX(getX(), getWidth()), size.centeredY(getY(), getHeight()),
                        size.width(), size.height(), active ? 1.0f : 144 / 255.0f);
            }
        };
    }

    public static void drawWithShadow(DrawContext context, Identifier texture, int x, int y, int size, float brightness) {
        drawWithShadow(context, texture, x, y, size, size, brightness);
    }

    private static void drawWithShadow(DrawContext context, Identifier texture, int x, int y, int width, int height, float brightness) {
        float shadow = brightness * 0.25f;
        context.setShaderColor(shadow, shadow, shadow, 1.0f);
        context.drawTexture(texture, x + 1, y + 1, 0, 0, width, height, width, height);
        context.setShaderColor(brightness, brightness, brightness, 1.0f);
        context.drawTexture(texture, x, y, 0, 0, width, height, width, height);
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
