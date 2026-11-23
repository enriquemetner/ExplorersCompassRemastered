package io.github.enriquemetner.explorerscompass.mixins.client;

import io.github.enriquemetner.explorerscompass.client.CompassRenderContexts;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class DroppedCompassRenderMixin {
    @Unique private final java.util.Deque<CompassRenderContexts.Scope> compassScopes = new java.util.ArrayDeque<>();
    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void begin(ItemEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        double rotation = 180 - entity.getRotation(delta) * 180 / Math.PI;
        compassScopes.push(CompassRenderContexts.push(CompassRenderContexts.entity(entity, rotation, false)));
    }
    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void end(ItemEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        compassScopes.pop().close();
    }
}
