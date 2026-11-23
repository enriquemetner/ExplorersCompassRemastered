package io.github.enriquemetner.explorerscompass.mixins.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.finndog.justenoughstructures.fabric.JustEnoughStructuresFabric", remap = false)
public class JesLegacySearchMixin {
    @Redirect(method = "onInitialize", at = @At(value = "INVOKE", target =
            "Lcom/finndog/justenoughstructures/compat/explorerscompass/ExplorersCompassSearch;install()V"),
            require = 0, remap = false)
    private void explorerscompass$skipLegacySearch() {
        // JES's original installer references the upstream Java package.
    }
}
