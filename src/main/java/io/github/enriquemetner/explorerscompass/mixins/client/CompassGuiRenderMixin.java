package io.github.enriquemetner.explorerscompass.mixins.client;

import io.github.enriquemetner.explorerscompass.client.CompassRenderContexts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public class CompassGuiRenderMixin {
    @Unique private final java.util.Deque<java.util.Optional<CompassRenderContexts.Scope>> compassScopes = new java.util.ArrayDeque<>();
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V", at = @At("HEAD"))
    private void begin(LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, int z, CallbackInfo ci) {
        var viewer = MinecraftClient.getInstance().player;
        var context = viewer == null ? null : CompassRenderContexts.entity(viewer, viewer.getYaw(), true);
        compassScopes.push(java.util.Optional.ofNullable(context == null ? null : CompassRenderContexts.push(context)));
    }
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V", at = @At("RETURN"))
    private void end(LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, int z, CallbackInfo ci) {
        compassScopes.pop().ifPresent(CompassRenderContexts.Scope::close);
    }
}
