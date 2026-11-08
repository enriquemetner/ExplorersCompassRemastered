package com.chaosthedude.explorerscompass.mixins;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadedAnvilChunkStorage.class)
public interface StructureChunkStorageAccessor {
    @Invoker("getCurrentChunkHolder")
    ChunkHolder explorerscompass$getCurrentChunkHolder(long chunk);

    @Invoker("updateChunkNbt")
    NbtCompound explorerscompass$updateChunkNbt(NbtCompound nbt);
}
