package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.compat.jes.JesCompassIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace the original provider and skip its screen callbacks when our provider installs. */
@Pseudo
@Mixin(targets = "com.finndog.justenoughstructures.fabric.FabricExplorersCompass", remap = false)
public class JesCompassRegistrationMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void explorerscompass$registerProvider(CallbackInfo ci) {
        if (JesCompassIntegration.register()) ci.cancel();
    }
}
