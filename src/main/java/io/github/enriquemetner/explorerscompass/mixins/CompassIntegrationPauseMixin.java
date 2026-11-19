package io.github.enriquemetner.explorerscompass.mixins;

import io.github.enriquemetner.explorerscompass.gui.ExplorersCompassScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class CompassIntegrationPauseMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;shouldPause()Z"))
    private boolean explorerscompass$shouldPauseScreen(Screen screen) {
        return screen.shouldPause() || ExplorersCompassScreen.shouldPauseIntegration(screen);
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void explorerscompass$clearIntegrationPause(Screen nextScreen, CallbackInfo ci) {
        ExplorersCompassScreen.clearIntegrationPause(nextScreen);
    }
}
