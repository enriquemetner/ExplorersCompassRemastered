package io.github.enriquemetner.explorerscompass.mixins;

import io.github.enriquemetner.explorerscompass.recipe.ClassicCompassRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "onDataPacksReloaded", at = @At("HEAD"))
    private void restoreClassicCompassRecipe(CallbackInfo ci) {
        ClassicCompassRecipe.apply(server);
    }
}
