package io.github.enriquemetner.explorerscompass.mixins;

import io.github.enriquemetner.explorerscompass.unlocking.UnlockingRules;
import io.github.enriquemetner.explorerscompass.unlocking.UnlockNotifications;
import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void explorerscompass$onProgress(Advancement advancement, String criterion, CallbackInfoReturnable<Boolean> result) {
        if (owner.getAdvancementTracker() != null && result.getReturnValueZ() && ((PlayerAdvancementTracker) (Object) this).getProgress(advancement).isDone()) {
            if (advancement.getId().equals(new net.minecraft.util.Identifier(ExplorersCompass.MODID, "get_compass"))) {
                UnlockNotifications.compassAdvancementCompleted(owner);
            } else {
                UnlockingRules.get().onProgress(owner, null, advancement.getId());
            }
        }
    }
}
