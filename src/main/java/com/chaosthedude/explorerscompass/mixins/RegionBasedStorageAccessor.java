package com.chaosthedude.explorerscompass.mixins;

import java.io.IOException;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RegionBasedStorage.class)
public interface RegionBasedStorageAccessor {
    @Invoker("getRegionFile")
    RegionFile explorerscompass$getRegionFile(ChunkPos pos) throws IOException;
}
