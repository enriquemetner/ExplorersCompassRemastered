package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.unlocking.UnlockReloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FTB marks definition edits dirty and reloads quest files through these methods. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.ServerQuestFile", remap = false)
public class FtbQuestFileMixin {
    @Inject(method = "markDirty", at = @At("TAIL"), require = 0, remap = false)
    private void explorerscompass$edited(CallbackInfo ci) {
        UnlockReloads.definitionChanged();
    }

    @Inject(method = "load", at = @At("TAIL"), require = 0, remap = false)
    private void explorerscompass$reloaded(CallbackInfo ci) {
        UnlockReloads.definitionChanged();
    }
}
