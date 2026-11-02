package com.chaosthedude.explorerscompass.mixins;

import com.chaosthedude.explorerscompass.unlocking.UnlockReloads;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remember who submitted an FTB definition edit until the next server-side comparison. */
@Pseudo
@Mixin(targets = {
        "dev.ftb.mods.ftbquests.net.CreateObjectMessage",
        "dev.ftb.mods.ftbquests.net.DeleteObjectMessage",
        "dev.ftb.mods.ftbquests.net.EditObjectMessage",
        "dev.ftb.mods.ftbquests.net.CreateTaskAtMessage",
        "dev.ftb.mods.ftbquests.net.MoveChapterMessage",
        "dev.ftb.mods.ftbquests.net.MoveMovableMessage",
        "dev.ftb.mods.ftbquests.net.MoveChapterGroupMessage",
        "dev.ftb.mods.ftbquests.net.ChangeChapterGroupMessage"
}, remap = false)
public class FtbQuestEditMessageMixin {
    @Inject(method = "handle", at = @At("HEAD"), require = 0, remap = false)
    private void explorerscompass$edited(NetworkManager.PacketContext context, CallbackInfo ci) {
        if (context.getPlayer() instanceof ServerPlayerEntity player) UnlockReloads.editedBy(player.getUuid());
    }
}
