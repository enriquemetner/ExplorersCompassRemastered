package io.github.enriquemetner.explorerscompass.mixins;

import java.io.IOException;
import java.nio.file.Path;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RegionBasedStorage.class)
public interface RegionBasedStorageAccessor {
    @Accessor("directory")
    Path explorerscompass$getDirectory();

    @Accessor("cachedRegionFiles")
    Long2ObjectLinkedOpenHashMap<RegionFile> explorerscompass$getCachedRegionFiles();

    @Invoker("write")
    void explorerscompass$write(ChunkPos pos, NbtCompound data) throws IOException;

    @Invoker("getRegionFile")
    RegionFile explorerscompass$getRegionFile(ChunkPos pos) throws IOException;
}
