package io.github.enriquemetner.explorerscompass.mixins.compat;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.util.mod.fabric.FabricMod", remap = false)
abstract class ModMenuLinksMixin {
    @Unique
    private static final List<String> explorerscompass$linkOrder = List.of(
            "explorerscompass.modmenu.video_trailer",
            "explorerscompass.modmenu.video_showcase",
            "explorerscompass.modmenu.wiki"
    );

    @Shadow public abstract String getId();

    @Inject(method = "getLinks", at = @At("RETURN"), cancellable = true, require = 0)
    private void explorerscompass$orderLinks(CallbackInfoReturnable<Map<String, String>> cir) {
        if (!ExplorersCompass.MODID.equals(getId())) return;

        // Mod Menu 7.2 discards metadata order when it loads links into a HashMap.
        var links = cir.getReturnValue();
        var ordered = new LinkedHashMap<String, String>();
        for (String key : explorerscompass$linkOrder) {
            if (links.containsKey(key)) ordered.put(key, links.get(key));
        }
        links.forEach(ordered::putIfAbsent);
        cir.setReturnValue(ordered);
    }
}
