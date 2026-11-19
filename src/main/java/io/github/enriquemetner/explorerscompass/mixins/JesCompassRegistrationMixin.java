package io.github.enriquemetner.explorerscompass.mixins;

import io.github.enriquemetner.explorerscompass.compat.jes.JesCompassIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use our provider in place of the upstream compass integration. */
@Pseudo
@Mixin(targets = "com.finndog.justenoughstructures.fabric.FabricExplorersCompass", remap = false)
public class JesCompassRegistrationMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void explorerscompass$registerProvider(CallbackInfo ci) {
        ci.cancel();
        JesCompassIntegration.register();
    }
}
