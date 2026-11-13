package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.compat.jes.JesThumbnails;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.finndog.justenoughstructures.client.Thumbnails", remap = false)
public class JesThumbnailSizeMixin {
    @Inject(method = "has", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void explorerscompass$refreshSmallThumbnail(Identifier id, CallbackInfoReturnable<Boolean> cir) {
        // Keep the old image available to draw until the queue replaces it.
        if (cir.getReturnValueZ() && JesThumbnails.needsLargerThumbnail(id)) cir.setReturnValue(false);
    }

    @Inject(method = "renderSize", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void explorerscompass$thumbnailSize(CallbackInfoReturnable<Integer> cir) {
        int size = JesThumbnails.renderSizeOverride();
        if (size > 0) cir.setReturnValue(size);
    }
}
