package io.github.enriquemetner.explorerscompass.mixins.storage;

import io.github.enriquemetner.explorerscompass.storage.IndexedRegionFile;
import io.github.enriquemetner.explorerscompass.storage.SavedStructureIndex;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.ChunkStreamVersion;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegionFile.class)
abstract class RegionFileIndexMixin implements IndexedRegionFile {
    @Shadow private void fillLastSector() throws IOException { throw new AssertionError(); }

    @Unique private Path explorerscompass$path;
    @Unique private SavedStructureIndex explorerscompass$index;
    @Unique private ChunkPos explorerscompass$position;

    @Inject(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/storage/ChunkStreamVersion;Z)V",
            at = @At("RETURN"))
    private void explorerscompass$rememberPath(Path path, Path directory, ChunkStreamVersion version,
            boolean sync, CallbackInfo ci) {
        explorerscompass$path = path;
    }

    @Override
    public void explorerscompass$trackPadding(SavedStructureIndex index, ChunkPos pos) {
        explorerscompass$index = index;
        explorerscompass$position = pos;
    }

    @Redirect(method = "close", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/storage/RegionFile;fillLastSector()V"))
    private void explorerscompass$preserveCoverageOnPadding(RegionFile file) throws IOException {
        var index = explorerscompass$index;
        if (index == null) {
            fillLastSector();
            return;
        }
        String before = SavedStructureIndex.tryRevision(explorerscompass$path);
        fillLastSector();
        index.rebase(explorerscompass$position, before, SavedStructureIndex.tryRevision(explorerscompass$path));
    }
}
